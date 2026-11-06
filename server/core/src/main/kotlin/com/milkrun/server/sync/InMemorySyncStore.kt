package com.milkrun.server.sync

import com.milkrun.model.ClientId
import com.milkrun.model.RouteId
import com.milkrun.model.Stop
import com.milkrun.model.StopId
import com.milkrun.protocol.MutationEnvelope
import com.milkrun.protocol.MutationResult
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Reference [SyncStore]: copy-on-write per transaction, one lock for the whole store.
 * Fast enough for the simulator (routes of tens of stops) and obviously correct.
 */
public class InMemorySyncStore : SyncStore {
    private data class Versioned(val stop: Stop, val version: Long)

    private data class RouteState(
        val version: Long,
        val stops: Map<StopId, Versioned>,
        val lastSeq: Map<ClientId, Long>,
        val results: Map<Pair<ClientId, Long>, MutationResult>,
    )

    private val lock = Mutex()
    private val routes = HashMap<RouteId, RouteState>()

    override suspend fun createRoute(route: RouteId, stops: List<Stop>): Unit = lock.withLock {
        require(route !in routes) { "route $route exists" }
        require(stops.all { it.routeId == route }) { "stop on another route" }
        routes[route] = RouteState(1, stops.associate { it.id to Versioned(it, 1) }, emptyMap(), emptyMap())
    }

    /** Opaque copy of the whole store, for simulating a restore from backup. */
    public class Checkpoint internal constructor(internal val routes: Map<RouteId, Any>)

    public suspend fun checkpoint(): Checkpoint = lock.withLock { Checkpoint(HashMap(routes)) }

    public suspend fun restore(checkpoint: Checkpoint): Unit = lock.withLock {
        routes.clear()
        @Suppress("UNCHECKED_CAST")
        routes.putAll(checkpoint.routes as Map<RouteId, RouteState>)
    }

    override suspend fun <T> transaction(route: RouteId, block: SyncTransaction.() -> T): T = lock.withLock {
        val tx = Tx(route, routes[route])
        val result = tx.block()
        tx.state?.let { routes[route] = it } // commit; an exception above discards tx.state
        result
    }

    private class Tx(override val route: RouteId, var state: RouteState?) : SyncTransaction {
        private val s: RouteState get() = checkNotNull(state) { "route $route does not exist" }

        override fun exists(): Boolean = state != null

        override fun version(): Long = s.version

        override fun stop(id: StopId): Stop? = s.stops[id]?.stop

        override fun writeStop(stop: Stop): Long {
            require(stop.routeId == route)
            val v = s.version + 1
            state = s.copy(version = v, stops = s.stops + (stop.id to Versioned(stop, v)))
            return v
        }

        override fun changesSince(cursor: Long): List<Stop> = s.stops.values
            .filter { it.version > cursor }
            .sortedWith(compareBy({ it.stop.sequence }, { it.stop.id.value }))
            .map { it.stop }

        override fun lastProcessedSeq(client: ClientId): Long = s.lastSeq[client] ?: 0L

        override fun result(client: ClientId, seq: Long): MutationResult? = s.results[client to seq]

        override fun recordResult(client: ClientId, envelope: MutationEnvelope, result: MutationResult) {
            val key = client to envelope.seq
            check(key !in s.results) { "ruling for $client/${envelope.seq} recorded twice" }
            state = s.copy(lastSeq = s.lastSeq + (client to envelope.seq), results = s.results + (key to result))
        }
    }
}
