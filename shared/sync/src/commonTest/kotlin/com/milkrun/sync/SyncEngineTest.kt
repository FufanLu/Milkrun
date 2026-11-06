package com.milkrun.sync

import com.milkrun.database.MilkrunDatabase
import com.milkrun.model.ClientId
import com.milkrun.model.GeoPoint
import com.milkrun.model.HandoffMethod
import com.milkrun.model.Hlc
import com.milkrun.model.Mutation
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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SyncEngineTest {
    private val route = RouteId("R-1")
    private val s1 = StopId("S-001")
    private val s2 = StopId("S-002")
    private val parcel = ParcelId("TBA000001")

    private fun stop(id: StopId, seq: Int, parcels: List<ParcelId> = emptyList()) = Stop(
        id = id,
        routeId = route,
        sequence = seq,
        address = "$seq Main St",
        location = GeoPoint(47.0, -122.0),
        parcels = parcels.map { Parcel(it, "box") },
    )

    /** A server that answers from a script and records what it was sent. */
    private class ScriptedServer : SyncTransport {
        val pushes = mutableListOf<PushRequest>()
        var stops: List<Stop> = emptyList()
        var version = 1L
        var lastProcessed = 0L
        var reject: Map<Long, RejectReason> = emptyMap()
        var failNext = false
        var processPushes = true
        private val hlc = Hlc(1_000, 0, "server")

        override suspend fun push(route: RouteId, request: PushRequest): PushResponse {
            if (failNext) {
                failNext = false
                error("offline")
            }
            pushes += request
            if (!processPushes) return PushResponse(lastProcessed, emptyList(), hlc)
            val results =
                request.mutations.map {
                    val reason = reject[it.seq]
                    MutationResult(it.seq, if (reason == null) Outcome.ACCEPTED else Outcome.REJECTED, reason)
                }
            lastProcessed = request.mutations.last().seq
            return PushResponse(lastProcessed, results, hlc)
        }

        override suspend fun pull(route: RouteId, client: ClientId, cursor: Long): PullResponse =
            PullResponse(version, lastProcessed, if (cursor < version) stops else emptyList(), hlc)
    }

    private fun TestScope.engine(db: MilkrunDatabase, server: ScriptedServer) = SyncEngine(
        database = db,
        transport = server,
        routeId = route,
        wallClock = { 1_000 + testScheduler.currentTime },
        random = Random(1),
        io = StandardTestDispatcher(testScheduler),
        newClientId = { "phone1" },
    )

    @Test
    fun nothingCanBeRecordedBeforeTheRouteIsLoaded() = runTest {
        val e = engine(testDatabase(), ScriptedServer())
        assertEquals(RecordResult.Refused(RejectReason.UNKNOWN_STOP), e.record(Mutation.Arrive(s1, 1)))
    }

    @Test
    fun recordingUpdatesTheViewAndQueuesTheMutationAtomically() = runTest {
        val server = ScriptedServer().apply { stops = listOf(stop(s1, 1, listOf(parcel)), stop(s2, 2)) }
        val e = engine(testDatabase(), server)
        e.syncOnce()

        val deliver = Mutation.Deliver(s1, listOf(parcel), HandoffMethod.FRONT_DOOR, atMillis = 5)
        val r = assertIs<RecordResult.Recorded>(e.record(deliver))
        assertEquals(1, r.seq)
        val items = e.stops().first()
        assertEquals(StopStatus.DELIVERED, items.first { it.stop.id == s1 }.stop.status)
        assertTrue(items.first { it.stop.id == s1 }.unsynced)
        assertEquals(1, e.status.value.pending)
        assertEquals(RouteProgress(total = 2, delivered = 1, attempted = 0, unsynced = 1), e.progress().first())
    }

    @Test
    fun locallyInvalidMutationsAreRefusedAndNeverQueued() = runTest {
        val server = ScriptedServer().apply { stops = listOf(stop(s1, 1, listOf(parcel))) }
        val e = engine(testDatabase(), server)
        e.syncOnce()
        e.record(Mutation.Deliver(s1, listOf(parcel), HandoffMethod.IN_PERSON, 5))
        assertEquals(
            RecordResult.Refused(RejectReason.ALREADY_DELIVERED),
            e.record(Mutation.Deliver(s1, listOf(parcel), HandoffMethod.IN_PERSON, 6)),
        )
        assertEquals(1, e.status.value.pending)
    }

    @Test
    fun theOutboxSurvivesAnAppRestart() = runTest {
        val db = testDatabase()
        val server = ScriptedServer().apply { stops = listOf(stop(s1, 1, listOf(parcel))) }
        engine(db, server).apply {
            syncOnce()
            record(Mutation.Arrive(s1, 5))
            server.failNext = true
            runCatching { syncOnce() } // killed while offline
        }
        val relaunched = engine(db, server)
        assertEquals(1, relaunched.status.value.pending)
        val r = assertIs<RecordResult.Recorded>(relaunched.record(Mutation.ScanParcel(s1, parcel, 6)))
        assertEquals(2, r.seq, "seq continues across restarts")
        relaunched.syncOnce()
        assertEquals(
            listOf(1L, 2L),
            server.pushes
                .single()
                .mutations
                .map { it.seq },
        )
        assertEquals(ClientId("phone1"), server.pushes.single().clientId)
    }

    @Test
    fun pendingMutationsAreReplayedOnTopOfNewServerState() = runTest {
        val server = ScriptedServer().apply { stops = listOf(stop(s1, 1, listOf(parcel)), stop(s2, 2)) }
        val e = engine(testDatabase(), server)
        e.syncOnce()
        e.record(Mutation.Arrive(s1, 5))

        // The server does not get to the arrival this round, but the dispatcher has meanwhile moved
        // S-001 to the end of the route.
        server.processPushes = false
        server.stops = listOf(stop(s1, 9, listOf(parcel)))
        server.version = 2
        e.syncOnce()
        val pending = e.snapshot().first { it.id == s1 }
        assertEquals(9, pending.sequence, "server's new order")
        assertEquals(StopStatus.ARRIVED, pending.status, "with the phone's pending arrival on top")
        assertEquals(1, e.status.value.pending)

        server.processPushes = true
        server.stops = listOf(stop(s1, 9, listOf(parcel)).copy(status = StopStatus.ARRIVED, arrivedAtMillis = 5))
        server.version = 3
        e.syncOnce()
        assertEquals(0, e.status.value.pending)
        assertEquals(server.stops.single(), e.snapshot().first { it.id == s1 })
    }

    @Test
    fun serverRejectionsAreSurfacedToTheDriver() = runTest {
        val server =
            ScriptedServer().apply {
                stops = listOf(stop(s1, 1, listOf(parcel)))
                reject = mapOf(1L to RejectReason.STOP_CANCELLED)
            }
        val e = engine(testDatabase(), server)
        e.syncOnce()
        e.record(Mutation.Arrive(s1, 5))
        e.syncOnce()
        assertEquals(listOf(Rejection(1, s1, "arrive", RejectReason.STOP_CANCELLED)), e.rejections().first())
        e.markRejectionSeen(1)
        assertEquals(emptyList(), e.rejections().first())
    }

    @Test
    fun aFailedSyncIsReportedNotLeftSpinning() = runTest {
        val server = ScriptedServer().apply { stops = listOf(stop(s1, 1, listOf(parcel))) }
        val e = engine(testDatabase(), server)
        e.syncOnce()
        e.record(Mutation.Arrive(s1, 5))
        server.failNext = true
        runCatching { e.syncOnce() }
        val st = e.status.value
        assertEquals(SyncStatus.Phase.IDLE, st.phase)
        assertEquals(1, st.consecutiveFailures)
        assertEquals("offline", st.lastError)
        assertEquals(1, st.pending)
    }
}
