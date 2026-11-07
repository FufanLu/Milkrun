package com.milkrun.server

import com.milkrun.model.ClientId
import com.milkrun.model.Decision
import com.milkrun.model.Parcel
import com.milkrun.model.RouteId
import com.milkrun.model.Stop
import com.milkrun.model.wireName
import com.milkrun.protocol.ApiError
import com.milkrun.protocol.CommandRequest
import com.milkrun.protocol.CommandResponse
import com.milkrun.protocol.CreateUpload
import com.milkrun.protocol.DevSession
import com.milkrun.protocol.DispatchApi
import com.milkrun.protocol.ErrorCode
import com.milkrun.protocol.MutationEnvelope
import com.milkrun.protocol.MutationResult
import com.milkrun.protocol.PlanRouteRequest
import com.milkrun.protocol.PlanRouteResponse
import com.milkrun.protocol.PushRequest
import com.milkrun.protocol.RouteSnapshot
import com.milkrun.protocol.SyncApi
import com.milkrun.protocol.Traceparent
import com.milkrun.server.sync.SyncException
import com.milkrun.server.sync.SyncObserver
import com.milkrun.server.sync.SyncService
import com.milkrun.server.sync.SyncStore
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.auth.principal
import io.ktor.server.metrics.micrometer.MicrometerMetrics
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.callid.CallId
import io.ktor.server.plugins.callid.callIdMdc
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.header
import io.ktor.server.request.path
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import kotlinx.serialization.SerializationException
import org.slf4j.event.Level
import java.sql.SQLTransientException
import java.util.UUID

/** Everything the HTTP layer needs; built by [main] or by tests. */
public class ServerDeps(
    public val store: SyncStore,
    public val jwt: JwtConfig,
    public val registry: PrometheusMeterRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT),
    public val readiness: suspend () -> Boolean = { true },
    public val wallClock: () -> Long = System::currentTimeMillis,
    /** Enables `POST /v1/dev/session`. Never on in production (MILKRUN_DEV_MODE). */
    public val devMode: Boolean = false,
    public val blobs: BlobStore = FileBlobStore(java.nio.file.Files.createTempDirectory("milkrun-blobs")),
) {
    public val sync: SyncService = SyncService(store, wallClock, MetricsObserver(registry))
}

/** Ruling counters: `milkrun_rulings_total{outcome,reason,type}` and `milkrun_push_duplicates_total`. */
internal class MetricsObserver(private val registry: MeterRegistry) : SyncObserver {
    override fun onRuling(route: RouteId, client: ClientId, envelope: MutationEnvelope, result: MutationResult) {
        registry.counter(
            "milkrun.rulings",
            "outcome",
            result.outcome.name.lowercase(),
            "reason",
            result.reason?.name?.lowercase() ?: "none",
            "type",
            envelope.mutation.wireName,
        ).increment()
    }

    override fun onDuplicate(route: RouteId, client: ClientId, seq: Long) {
        registry.counter("milkrun.push.duplicates").increment()
    }
}

private class ApiFailure(val status: HttpStatusCode, val error: ApiError) : RuntimeException(error.message)

public fun Application.milkrun(deps: ServerDeps) {
    install(ContentNegotiation) { json(SyncApi.json) }
    install(CallId) {
        retrieveFromHeader(HttpHeaders.XRequestId)
        generate { UUID.randomUUID().toString() }
        replyToHeader(HttpHeaders.XRequestId)
    }
    install(TraceContext)
    install(CallLogging) {
        level = Level.INFO
        callIdMdc("request_id")
        mdc("trace_id") { it.attributes.getOrNull(TraceKey)?.traceId }
        filter { !it.request.path().startsWith("/metrics") && !it.request.path().startsWith("/healthz") }
    }
    install(MicrometerMetrics) { registry = deps.registry }
    install(StatusPages) {
        exception<ApiFailure> { call, e -> call.respond(e.status, e.error) }
        exception<SyncException.SeqGap> { call, e ->
            call.respond(
                HttpStatusCode.Conflict,
                ApiError(ErrorCode.SEQ_GAP, e.message ?: "", expectedSeq = e.expected),
            )
        }
        exception<SyncException.UnknownRoute> { call, e ->
            call.respond(HttpStatusCode.NotFound, ApiError(ErrorCode.UNKNOWN_ROUTE, e.message ?: ""))
        }
        exception<BadRequestException> { call, e -> call.badRequest(e.rootMessage()) }
        exception<SerializationException> { call, e -> call.badRequest(e.message) }
        exception<IllegalArgumentException> { call, e -> call.badRequest(e.message) }
        exception<UploadException> { call, e ->
            val status = when (e.code) {
                "unknown_upload" -> HttpStatusCode.NotFound
                "conflict", "incomplete", "hash_mismatch" -> HttpStatusCode.Conflict
                else -> HttpStatusCode.BadRequest
            }
            call.respond(status, ApiError(ErrorCode.BAD_REQUEST, "${e.code}: ${e.message}"))
        }
        exception<SQLTransientException> { call, _ ->
            call.response.headers.append(HttpHeaders.RetryAfter, "2")
            call.respond(HttpStatusCode.ServiceUnavailable, ApiError(ErrorCode.UNAVAILABLE, "database unavailable"))
        }
    }
    install(io.ktor.server.auth.Authentication) {
        jwt("milkrun") {
            verifier(deps.jwt.verifier)
            validate { cred ->
                if (JwtConfig.caller(JWTPrincipal(cred.payload)) !=
                    null
                ) {
                    JWTPrincipal(cred.payload)
                } else {
                    null
                }
            }
            challenge { _, _ ->
                call.respond(HttpStatusCode.Unauthorized, ApiError(ErrorCode.FORBIDDEN, "missing or invalid token"))
            }
        }
    }

    routing {
        get("/healthz") { call.respondText("ok") }
        get("/readyz") {
            if (deps.readiness()) {
                call.respondText(
                    "ready",
                )
            } else {
                call.respondText("not ready", status = HttpStatusCode.ServiceUnavailable)
            }
        }
        get("/metrics") { call.respondText(deps.registry.scrape()) }

        if (deps.devMode) {
            // Demo convenience: a fresh Seattle route and tokens for it, so an emulator or simulator
            // can start a shift with one request. Off unless MILKRUN_DEV_MODE=true.
            post("/v1/dev/session") {
                val route = RouteId(
                    // Millis alone collide when many drivers start at once (found by the load tool).
                    "R-DEMO-${System.currentTimeMillis().toString(36).uppercase()}" +
                        "-${java.util.UUID.randomUUID().toString().take(6).uppercase()}",
                )
                deps.planAndCreate(DemoRoute.request(route))
                call.respond(
                    DevSession(
                        routeId = route,
                        driverToken = deps.jwt.issue(Caller.Driver("drv-demo", route)),
                        dispatcherToken = deps.jwt.issue(Caller.Dispatcher("disp-demo")),
                    ),
                )
            }
        }

        authenticate("milkrun") {
            route("/v1/routes/{routeId}") {
                post("/push") {
                    val route = call.driverRoute()
                    val request = call.receive<PushRequest>()
                    call.respond(deps.sync.push(route, request))
                }
                get("/pull") {
                    val route = call.driverRoute()
                    val client = call.request.queryParameters["client"]?.let(::ClientId)
                        ?: throw ApiFailure(
                            HttpStatusCode.BadRequest,
                            ApiError(ErrorCode.BAD_REQUEST, "client is required"),
                        )
                    val cursor = call.request.queryParameters["cursor"]?.toLongOrNull() ?: 0L
                    call.respond(deps.sync.pull(route, client, cursor))
                }
            }

            route("/v1/routes/{routeId}/uploads") {
                post {
                    val route = call.driverRoute()
                    call.respond(deps.blobs.create(route, call.receive<CreateUpload>()))
                }
                get("/{uploadId}") {
                    val route = call.driverRoute()
                    val st = deps.blobs.status(route, call.parameters["uploadId"]!!)
                        ?: throw UploadException("unknown_upload", "no such upload")
                    call.respond(st)
                }
                put("/{uploadId}/parts/{part}") {
                    val route = call.driverRoute()
                    val part =
                        call.parameters["part"]!!.toIntOrNull()
                            ?: throw UploadException("bad_part", "part must be a number")
                    deps.blobs.putPart(route, call.parameters["uploadId"]!!, part, call.receive<ByteArray>())
                    call.respond(HttpStatusCode.NoContent)
                }
                post("/{uploadId}/complete") {
                    val route = call.driverRoute()
                    call.respond(deps.blobs.complete(route, call.parameters["uploadId"]!!))
                }
            }

            route(DispatchApi.ROUTES_PATH) {
                post {
                    call.requireDispatcher()
                    val plan = deps.planAndCreate(call.receive<PlanRouteRequest>())
                    call.respond(HttpStatusCode.Created, plan)
                }
                get("/{routeId}") {
                    call.requireDispatcher()
                    val route = RouteId(call.parameters["routeId"]!!)
                    val pull = deps.sync.pull(route, ClientId("dispatcher"), 0)
                    call.respond(RouteSnapshot(route, pull.cursor, pull.changes))
                }
                get("/{routeId}/proofs/{uploadId}") {
                    call.requireDispatcher()
                    val bytes = deps.blobs.read(RouteId(call.parameters["routeId"]!!), call.parameters["uploadId"]!!)
                        ?: throw UploadException("unknown_upload", "no completed upload")
                    call.respondBytes(bytes, io.ktor.http.ContentType.Image.JPEG)
                }
                post("/{routeId}/commands") {
                    call.requireDispatcher()
                    val route = RouteId(call.parameters["routeId"]!!)
                    val d = deps.sync.dispatch(route, call.receive<CommandRequest>().command)
                    call.respond(
                        when (d) {
                            is Decision.Accepted -> CommandResponse(accepted = true)
                            is Decision.Rejected -> CommandResponse(accepted = false, reason = d.reason)
                        },
                    )
                }
            }
        }
    }
}

/** The route in the path, which must be the route on the caller's driver token. */
private fun ApplicationCall.driverRoute(): RouteId {
    checkProtocol()
    val route = RouteId(parameters["routeId"]!!)
    when (val caller = caller()) {
        is Caller.Driver -> if (caller.route != route) forbidden("token is for route ${caller.route}, not $route")
        is Caller.Dispatcher -> forbidden("dispatchers do not sync")
    }
    return route
}

private fun ApplicationCall.requireDispatcher() {
    if (caller() !is Caller.Dispatcher) forbidden("dispatcher role required")
}

private fun ApplicationCall.caller(): Caller =
    principal<JWTPrincipal>()?.let(JwtConfig::caller) ?: forbidden("no caller")

/** Apps send `Milkrun-Protocol: 1`; a different major means the app must update (HTTP 426). */
private fun ApplicationCall.checkProtocol() {
    val v = request.header(SyncApi.PROTOCOL_HEADER)?.toIntOrNull()
    if (v != SyncApi.PROTOCOL_VERSION) {
        throw ApiFailure(
            HttpStatusCode.UpgradeRequired,
            ApiError(
                ErrorCode.UNSUPPORTED_PROTOCOL,
                "server speaks protocol ${SyncApi.PROTOCOL_VERSION}, app sent ${v ?: "none"}",
            ),
        )
    }
}

private fun forbidden(message: String): Nothing =
    throw ApiFailure(HttpStatusCode.Forbidden, ApiError(ErrorCode.FORBIDDEN, message))

private suspend fun ApplicationCall.badRequest(message: String?) =
    respond(HttpStatusCode.BadRequest, ApiError(ErrorCode.BAD_REQUEST, message ?: "bad request"))

private fun Throwable.rootMessage(): String? = generateSequence(this) { it.cause }.last().message ?: message

/** Sequences [req]'s stops with [RoutePlanner] and creates the route at version 1. */
internal suspend fun ServerDeps.planAndCreate(req: PlanRouteRequest): PlanRouteResponse {
    require(req.stops.isNotEmpty()) { "a route needs at least one stop" }
    require(req.stops.map { it.id }.toSet().size == req.stops.size) { "duplicate stop ids" }
    val plan = RoutePlanner.plan(req.depot, req.stops.map { it.location })
    val stops = plan.order.mapIndexed { position, index ->
        val s = req.stops[index]
        Stop(
            id = s.id,
            routeId = req.routeId,
            sequence = position + 1,
            address = s.address,
            location = s.location,
            parcels = s.parcels.map { Parcel(it.id, it.description) },
        )
    }
    sync.createRoute(req.routeId, stops)
    return PlanRouteResponse(req.routeId, stops.map { it.id }, plan.nearestNeighbourKm, plan.optimisedKm)
}

internal val TraceKey = io.ktor.util.AttributeKey<Traceparent>("milkrun.trace")

/**
 * Continues the caller's W3C trace (or starts one), exposes it to logging, and answers with
 * `traceresponse` carrying the server's span, so the phone can log which server span served it.
 */
internal val TraceContext = io.ktor.server.application.createApplicationPlugin("TraceContext") {
    onCall { call ->
        val incoming = Traceparent.parse(call.request.headers[Traceparent.HEADER])
        val server = incoming?.child() ?: Traceparent.random()
        call.attributes.put(TraceKey, server)
        call.response.headers.append(Traceparent.RESPONSE_HEADER, server.encode())
    }
}
