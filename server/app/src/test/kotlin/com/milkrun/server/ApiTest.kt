package com.milkrun.server

import com.milkrun.database.JvmDatabase
import com.milkrun.model.ClientId
import com.milkrun.model.DispatcherCommand
import com.milkrun.model.GeoPoint
import com.milkrun.model.HandoffMethod
import com.milkrun.model.Hlc
import com.milkrun.model.Mutation
import com.milkrun.model.ParcelId
import com.milkrun.model.RejectReason
import com.milkrun.model.RouteId
import com.milkrun.model.StopId
import com.milkrun.model.StopStatus
import com.milkrun.network.KtorSyncTransport
import com.milkrun.network.milkrunDefaults
import com.milkrun.protocol.ApiError
import com.milkrun.protocol.CommandRequest
import com.milkrun.protocol.CommandResponse
import com.milkrun.protocol.DevSession
import com.milkrun.protocol.DispatchApi
import com.milkrun.protocol.ErrorCode
import com.milkrun.protocol.MutationEnvelope
import com.milkrun.protocol.PlanRouteRequest
import com.milkrun.protocol.PlanRouteResponse
import com.milkrun.protocol.PlannedParcel
import com.milkrun.protocol.PlannedStop
import com.milkrun.protocol.PushRequest
import com.milkrun.protocol.RouteSnapshot
import com.milkrun.protocol.SyncApi
import com.milkrun.sync.RecordResult
import com.milkrun.sync.SyncEngine
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ApiTest {
    private val jwt = JwtConfig("test-secret-that-is-long-enough-for-hs256")
    private val dispatcher = jwt.issue(Caller.Dispatcher("disp-ops-1"))

    private fun newRoute() = RouteId("R-API-${System.nanoTime()}-${n.incrementAndGet()}")

    private fun plan(route: RouteId) = PlanRouteRequest(
        routeId = route,
        depot = GeoPoint(47.5985, -122.3270), // DSE4-ish, SoDo
        stops = listOf(
            PlannedStop(
                StopId("$route-A"),
                "410 Terry Ave N",
                GeoPoint(47.6223, -122.3365),
                listOf(PlannedParcel(ParcelId("$route-P1"), "box")),
            ),
            PlannedStop(
                StopId("$route-B"),
                "1st Ave S & S Royal Brougham",
                GeoPoint(47.5917, -122.3343),
                listOf(PlannedParcel(ParcelId("$route-P2"), "mailer")),
            ),
            PlannedStop(
                StopId("$route-C"),
                "2021 7th Ave",
                GeoPoint(47.6153, -122.3383),
                listOf(PlannedParcel(ParcelId("$route-P3"), "box")),
            ),
        ),
    )

    private fun api(block: suspend ApplicationTestBuilder.(HttpClient) -> Unit) = testApplication {
        application { milkrun(ServerDeps(PostgresSyncStore(Postgres.dataSource), jwt)) }
        block(createClient { milkrunDefaults("http://localhost") })
    }

    private suspend fun HttpClient.createRoute(route: RouteId): PlanRouteResponse {
        val r = post(DispatchApi.ROUTES_PATH) {
            bearerAuth(dispatcher)
            contentType(ContentType.Application.Json)
            setBody(plan(route))
        }
        assertEquals(HttpStatusCode.Created, r.status)
        return r.body()
    }

    @Test
    fun requestsWithoutATokenAreRejected() = api { http ->
        val r = http.get(SyncApi.pullPath(RouteId("R-x")) + "?client=c&cursor=0") { header(SyncApi.PROTOCOL_HEADER, 1) }
        assertEquals(HttpStatusCode.Unauthorized, r.status)
    }

    @Test
    fun aDriverTokenOnlyOpensItsOwnRoute() = api { http ->
        val mine = newRoute()
        http.createRoute(mine)
        val token = jwt.issue(Caller.Driver("drv-1", mine))
        val r = http.get(SyncApi.pullPath(newRoute()) + "?client=c&cursor=0") {
            bearerAuth(token)
            header(SyncApi.PROTOCOL_HEADER, 1)
        }
        assertEquals(HttpStatusCode.Forbidden, r.status)
        assertEquals(ErrorCode.FORBIDDEN, r.body<ApiError>().code)
    }

    @Test
    fun anOutdatedAppIsToldToUpgrade() = api { http ->
        val route = newRoute()
        http.createRoute(route)
        val r = http.get(SyncApi.pullPath(route) + "?client=c&cursor=0") {
            bearerAuth(jwt.issue(Caller.Driver("drv-1", route)))
            header(SyncApi.PROTOCOL_HEADER, 0)
        }
        assertEquals(HttpStatusCode.UpgradeRequired, r.status)
        assertEquals(ErrorCode.UNSUPPORTED_PROTOCOL, r.body<ApiError>().code)
    }

    @Test
    fun theDispatcherPlanIsSequencedByTheServer() = api { http ->
        val route = newRoute()
        val plan = http.createRoute(route)
        // From SoDo: the stadium stop first, then 7th Ave, then Terry Ave N.
        assertEquals(listOf("$route-B", "$route-C", "$route-A"), plan.order.map { it.value })
        val snap = http.get(DispatchApi.routePath(route)) { bearerAuth(dispatcher) }.body<RouteSnapshot>()
        assertEquals(listOf(1, 2, 3), snap.stops.map { it.sequence })
    }

    @Test
    fun aRealSyncEngineDeliversThroughHttpAndPostgres() = api { http ->
        val route = newRoute()
        http.createRoute(route)
        val token = jwt.issue(Caller.Driver("drv-7", route))
        val engine =
            SyncEngine(JvmDatabase.create(), KtorSyncTransport(http) { token }, route, System::currentTimeMillis)
        engine.syncOnce()
        val a = StopId("$route-A")
        assertIs<RecordResult.Recorded>(engine.record(Mutation.Arrive(a, 1)))
        assertIs<RecordResult.Recorded>(
            engine.record(Mutation.Deliver(a, listOf(ParcelId("$route-P1")), HandoffMethod.MAILROOM, 2)),
        )
        engine.syncOnce()
        assertEquals(0, engine.status.value.pending)

        val snap = http.get(DispatchApi.routePath(route)) { bearerAuth(dispatcher) }.body<RouteSnapshot>()
        assertEquals(StopStatus.DELIVERED, snap.stops.first { it.id == a }.status)
        assertEquals(engine.snapshot().sortedBy { it.id.value }, snap.stops.sortedBy { it.id.value })

        val cancel = http.post(DispatchApi.commandsPath(route)) {
            bearerAuth(dispatcher)
            contentType(ContentType.Application.Json)
            setBody(CommandRequest(DispatcherCommand.Cancel(a)))
        }.body<CommandResponse>()
        assertEquals(CommandResponse(accepted = false, reason = RejectReason.ALREADY_DELIVERED), cancel)

        val metrics = http.get("/metrics").body<String>()
        assertContains(metrics, "milkrun_rulings_total{outcome=\"accepted\"")
    }

    @Test
    fun aSkippedSeqIsAConflictNamingTheExpectedSeq() = api { http ->
        val route = newRoute()
        http.createRoute(route)
        val r = http.post(SyncApi.pushPath(route)) {
            bearerAuth(jwt.issue(Caller.Driver("drv-1", route)))
            header(SyncApi.PROTOCOL_HEADER, 1)
            contentType(ContentType.Application.Json)
            setBody(
                PushRequest(
                    ClientId("c9"),
                    listOf(MutationEnvelope(3, Hlc(1, 0, "c9"), Mutation.Arrive(StopId("$route-A"), 1))),
                ),
            )
        }
        assertEquals(HttpStatusCode.Conflict, r.status)
        assertEquals(
            ApiError(ErrorCode.SEQ_GAP, "client c9 sent seq 3, expected 1", expectedSeq = 1),
            r.body<ApiError>(),
        )
    }

    @Test
    fun devSessionExistsOnlyInDevMode() {
        testApplication {
            application { milkrun(ServerDeps(PostgresSyncStore(Postgres.dataSource), jwt)) }
            assertEquals(
                HttpStatusCode.NotFound,
                createClient {
                    milkrunDefaults("http://localhost")
                }.post("/v1/dev/session").status,
            )
        }
        testApplication {
            application { milkrun(ServerDeps(PostgresSyncStore(Postgres.dataSource), jwt, devMode = true)) }
            val http = createClient { milkrunDefaults("http://localhost") }
            val session = http.post("/v1/dev/session").body<DevSession>()
            val engine =
                SyncEngine(
                    JvmDatabase.create(),
                    KtorSyncTransport(http) {
                        session.driverToken
                    },
                    session.routeId,
                    System::currentTimeMillis,
                )
            engine.syncOnce()
            assertEquals(12, engine.snapshot().size)
        }
    }

    @Test
    fun theServerContinuesThePhonesTrace() = api { http ->
        val route = newRoute()
        http.createRoute(route)
        val sent = com.milkrun.protocol.Traceparent.parse("00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01")!!
        val r = http.get(SyncApi.pullPath(route) + "?client=c&cursor=0") {
            bearerAuth(jwt.issue(Caller.Driver("drv-1", route)))
            header(SyncApi.PROTOCOL_HEADER, 1)
            header(com.milkrun.protocol.Traceparent.HEADER, sent.encode())
        }
        val back = com.milkrun.protocol.Traceparent.parse(r.headers[com.milkrun.protocol.Traceparent.RESPONSE_HEADER])!!
        assertEquals(sent.traceId, back.traceId, "same trace")
        kotlin.test.assertNotEquals(sent.spanId, back.spanId, "server span is a child")
    }

    private companion object {
        val n = AtomicInteger()
    }
}
