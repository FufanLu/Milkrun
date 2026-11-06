package com.milkrun.presentation

import com.milkrun.database.MilkrunDatabase
import com.milkrun.model.ClientId
import com.milkrun.model.GeoPoint
import com.milkrun.model.HandoffMethod
import com.milkrun.model.Hlc
import com.milkrun.model.Parcel
import com.milkrun.model.ParcelId
import com.milkrun.model.RejectReason
import com.milkrun.model.RouteId
import com.milkrun.model.Stop
import com.milkrun.model.StopId
import com.milkrun.model.StopStatus
import com.milkrun.protocol.MutationResult
import com.milkrun.protocol.Outcome
import com.milkrun.protocol.PullResponse
import com.milkrun.protocol.PushRequest
import com.milkrun.protocol.PushResponse
import com.milkrun.sync.SyncEngine
import com.milkrun.sync.SyncTransport
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

internal expect fun testDatabase(): MilkrunDatabase

/**
 * Runs everything ready now, including backgroundScope collectors (the screen model's stateIn).
 * advanceUntilIdle() would not: it treats background work as idle by design.
 */
@OptIn(ExperimentalCoroutinesApi::class)
private fun TestScope.settle() = runCurrent()

@OptIn(ExperimentalCoroutinesApi::class)
class RouteScreenModelTest {
    private val route = RouteId("R-1")

    private fun stop(n: Int, parcels: Int = 1) = Stop(
        id = StopId("S-$n"),
        routeId = route,
        sequence = n,
        address = "$n Pine St",
        location = GeoPoint(47.6, -122.3),
        parcels = List(parcels) { Parcel(ParcelId("P$n-$it"), "box") },
    )

    private class Server(var stops: List<Stop>) : SyncTransport {
        var online = true
        var reject: RejectReason? = null
        private var last = 0L

        override suspend fun push(route: RouteId, request: PushRequest): PushResponse {
            if (!online) error("no signal")
            last = request.mutations.last().seq
            val r = reject
            return PushResponse(
                last,
                request.mutations.map {
                    MutationResult(
                        it.seq,
                        if (r ==
                            null
                        ) {
                            Outcome.ACCEPTED
                        } else {
                            Outcome.REJECTED
                        },
                        r,
                    )
                },
                Hlc(1, 0, "server"),
            )
        }

        override suspend fun pull(route: RouteId, client: ClientId, cursor: Long): PullResponse {
            if (!online) error("no signal")
            return PullResponse(cursor + 1, last, if (cursor == 0L) stops else emptyList(), Hlc(1, 0, "server"))
        }
    }

    private suspend fun TestScope.model(server: Server): Pair<SyncEngine, RouteScreenModel> {
        val io = StandardTestDispatcher(testScheduler)
        val engine = SyncEngine(testDatabase(), server, route, { 1_000 }, Random(1), io = io, newClientId = { "p1" })
        engine.syncOnce()
        val model = RouteScreenModel(engine, backgroundScope, now = { 2_000 })
        settle()
        return engine to model
    }

    @Test
    fun theNextStopIsTheFirstOpenOneInRouteOrder() = runTest {
        val (_, m) = model(Server(listOf(stop(3), stop(1), stop(2))))
        val s = m.state.value
        assertEquals("01", s.next?.number)
        assertEquals(listOf("02", "03"), s.upcoming.map { it.number })
        assertEquals(3, s.remaining)
        assertIs<SyncBanner.AllSent>(s.sync)
    }

    @Test
    fun deliveringMovesTheRouteOnAndShowsTheTape() = runTest {
        val server = Server(listOf(stop(1, parcels = 2), stop(2)))
        val (_, m) = model(server)
        server.online = false
        val first = m.state.value.next!!
        m.deliver(first.id, HandoffMethod.FRONT_DOOR)
        settle()
        val s = m.state.value
        assertEquals("02", s.next?.number)
        assertEquals(1, s.delivered)
        val done = s.done.single()
        assertTrue(done.unsynced)
        assertEquals("Delivered · front door", done.statusLabel)
        assertEquals(SyncBanner.Waiting(1, retrying = false, lastError = null), s.sync)
        assertEquals("Sending 1 change", (s.sync as SyncBanner.Waiting).title)
        assertEquals("1 waiting for signal", SyncBanner.Waiting(1, retrying = true, lastError = "x").title)
    }

    @Test
    fun anArrivedStopIsNextEvenOutOfOrder() = runTest {
        val (_, m) = model(Server(listOf(stop(1), stop(2), stop(3))))
        m.arrive(StopId("S-3"))
        settle()
        assertEquals("03", m.state.value.next?.number)
        assertEquals("Arrived · 0 of 1 scanned", m.state.value.next?.statusLabel)
    }

    @Test
    fun aRulingBecomesANoticeInTheDriversTerms() = runTest {
        val server = Server(listOf(stop(14)))
        val (engine, m) = model(server)
        server.reject = RejectReason.STOP_CANCELLED
        m.arrive(StopId("S-14"))
        engine.syncOnce()
        settle()
        val n = m.state.value.notices.single()
        assertEquals("Stop 14 was cancelled by dispatch", n.title)
        assertEquals("Keep its parcels on the van and bring them back to the station.", n.body)
        m.dismiss(n)
        settle()
        assertEquals(emptyList(), m.state.value.notices)
    }

    @Test
    fun stopDetailOffersOnlyLegalActions() = runTest {
        val (_, m) = model(Server(listOf(stop(1))))
        val before = m.stop(StopId("S-1")).first()!!
        assertTrue(before.canArrive && before.canDeliver && before.canAttempt)
        m.deliver(StopId("S-1"), HandoffMethod.LOCKER)
        settle()
        val after = m.stop(StopId("S-1")).first()!!
        assertEquals(StopStatus.DELIVERED, after.card.status)
        assertTrue(!after.canArrive && !after.canDeliver && !after.canAttempt && !after.canScan)
        assertEquals("P1-0", after.parcels.single().shortCode)
    }

    @Test
    fun aRejectedArrivalIsNotShownOnceTheStopIsDelivered() = runTest {
        val server = Server(listOf(stop(3)))
        val (engine, m) = model(server)
        server.reject = RejectReason.STOP_CANCELLED
        m.arrive(StopId("S-3"))
        engine.syncOnce()
        settle()
        assertEquals(1, m.state.value.notices.size)
        // The delivery stands (server accepts it, flagged) and the stop's state comes back delivered.
        server.reject = null
        server.stops = listOf(stop(3).copy(status = StopStatus.DELIVERED))
        m.deliver(StopId("S-3"), HandoffMethod.FRONT_DOOR)
        settle()
        assertEquals(emptyList(), m.state.value.notices)
    }
}
