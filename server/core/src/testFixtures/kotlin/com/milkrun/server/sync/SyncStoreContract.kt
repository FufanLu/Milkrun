package com.milkrun.server.sync

import com.milkrun.model.ClientId
import com.milkrun.model.DispatcherCommand
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
import com.milkrun.protocol.MutationEnvelope
import com.milkrun.protocol.MutationResult
import com.milkrun.protocol.Outcome
import com.milkrun.protocol.PushRequest
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * Behaviour every [SyncStore] must have. Subclass it once per implementation; the in-memory store
 * runs it in `:server:core`, the Postgres store in `:server:app` against a real database.
 */
abstract class SyncStoreContract {
    protected abstract fun newStore(): SyncStore

    private val counter = AtomicInteger()

    /** Fresh route ids per test, so implementations backed by one shared database stay isolated. */
    protected fun freshRoute(): RouteId = RouteId("R-CT-${System.nanoTime()}-${counter.incrementAndGet()}")

    protected fun stops(route: RouteId, n: Int = 3): List<Stop> = List(n) { i ->
        Stop(
            id = StopId("${route.value}-S$i"),
            routeId = route,
            sequence = i + 1,
            address = "$i Pine St",
            location = GeoPoint(47.61, -122.33),
            parcels = listOf(Parcel(ParcelId("${route.value}-P$i"), "box")),
        )
    }

    private val client = ClientId("c1")
    private val hlc = Hlc(1_000, 0, "c1")

    private fun env(seq: Long, m: Mutation) = MutationEnvelope(seq, hlc.copy(counter = seq.toInt()), m)

    @Test
    fun newRouteStartsAtVersionOneWithEverySnapshot() = runBlocking {
        val store = newStore()
        val route = freshRoute()
        store.createRoute(route, stops(route))
        store.transaction(route) {
            assertEquals(1, version())
            assertEquals(stops(route), changesSince(0))
            assertEquals(emptyList(), changesSince(1))
            assertEquals(0, lastProcessedSeq(client))
        }
    }

    @Test
    fun creatingARouteTwiceFails() = runBlocking {
        val store = newStore()
        val route = freshRoute()
        store.createRoute(route, stops(route))
        assertFailsWith<IllegalArgumentException> { store.createRoute(route, stops(route)) }
        Unit
    }

    @Test
    fun unknownRouteDoesNotExist() = runBlocking {
        assertEquals(false, newStore().transaction(freshRoute()) { exists() })
    }

    @Test
    fun writesBumpTheVersionAndAppearInChanges() = runBlocking {
        val store = newStore()
        val route = freshRoute()
        val s = stops(route)
        store.createRoute(route, s)
        val v = store.transaction(route) { writeStop(s[1].copy(status = StopStatus.ARRIVED)) }
        assertEquals(2, v)
        store.transaction(route) {
            assertEquals(2, version())
            assertEquals(listOf(s[1].copy(status = StopStatus.ARRIVED)), changesSince(1))
            assertEquals(StopStatus.ARRIVED, stop(s[1].id)?.status)
        }
    }

    @Test
    fun anExceptionRollsBackEveryWriteInTheTransaction() = runBlocking {
        val store = newStore()
        val route = freshRoute()
        val s = stops(route)
        store.createRoute(route, s)
        runCatching {
            store.transaction(route) {
                writeStop(s[0].copy(note = "x"))
                recordResult(client, env(1, Mutation.Arrive(s[0].id, 1)), MutationResult(1, Outcome.ACCEPTED))
                error("boom")
            }
        }
        store.transaction(route) {
            assertEquals(1, version())
            assertEquals("", stop(s[0].id)?.note)
            assertEquals(0, lastProcessedSeq(client))
            assertNull(result(client, 1))
        }
    }

    @Test
    fun rulingsAreStoredPerClientAndRoute() = runBlocking {
        val store = newStore()
        val a = freshRoute()
        val b = freshRoute()
        store.createRoute(a, stops(a))
        store.createRoute(b, stops(b))
        val r = MutationResult(1, Outcome.REJECTED, RejectReason.STOP_CANCELLED)
        store.transaction(a) { recordResult(client, env(1, Mutation.Arrive(stops(a)[0].id, 1)), r) }
        store.transaction(a) {
            assertEquals(1, lastProcessedSeq(client))
            assertEquals(r, result(client, 1))
        }
        store.transaction(b) { assertEquals(0, lastProcessedSeq(client), "same client id on another route") }
    }

    @Test
    fun theServiceIsIdempotentOnThisStore() = runBlocking {
        val store = newStore()
        val route = freshRoute()
        val s = stops(route)
        val service = SyncService(store, { 5_000 })
        service.createRoute(route, s)
        val push = PushRequest(
            client,
            listOf(
                env(1, Mutation.Arrive(s[0].id, 1)),
                env(2, Mutation.Deliver(s[0].id, listOf(s[0].parcels[0].id), HandoffMethod.FRONT_DOOR, 2)),
            ),
        )
        val first = service.push(route, push)
        val again = service.push(route, push)
        assertEquals(first.results, again.results)
        assertEquals(2, again.lastProcessedSeq)
        val pull = service.pull(route, client, 0)
        assertEquals(3, pull.cursor, "two writes, not four")
        assertEquals(StopStatus.DELIVERED, pull.changes.first { it.id == s[0].id }.status)
        assertFailsWith<SyncException.SeqGap> {
            service.push(route, PushRequest(client, listOf(env(5, Mutation.Arrive(s[1].id, 3)))))
        }
        Unit
    }

    /**
     * Concurrent writers on one route must serialise: 8 coroutines × 25 dispatcher commands give
     * exactly 200 distinct versions, so a pull cursor can never skip a change.
     */
    @Test
    fun concurrentTransactionsOnOneRouteGetDistinctGapFreeVersions() = runBlocking {
        val store = newStore()
        val route = freshRoute()
        val s = stops(route, n = 8)
        val service = SyncService(store, { 5_000 })
        service.createRoute(route, s)
        coroutineScope {
            s.mapIndexed { i, stop ->
                async(kotlinx.coroutines.Dispatchers.IO) {
                    repeat(25) { k -> service.dispatch(route, DispatcherCommand.Resequence(stop.id, 100 * i + k + 2)) }
                }
            }.awaitAll()
        }
        store.transaction(route) {
            assertEquals(201, version())
            assertEquals(8, changesSince(1).size)
        }
    }
}
