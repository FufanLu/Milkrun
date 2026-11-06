package com.milkrun.server.sync

import com.milkrun.model.ClientId
import com.milkrun.model.RouteId
import com.milkrun.model.Stop
import com.milkrun.model.StopId
import com.milkrun.protocol.MutationEnvelope
import com.milkrun.protocol.MutationResult

/**
 * Storage behind [SyncService]. The Postgres implementation lives in `:server:app`;
 * [InMemorySyncStore] backs the simulator and unit tests. Both must pass `SyncStoreContract`.
 */
public interface SyncStore {
    /**
     * Runs [block] as one serialisable unit of work on [route]: all writes commit together or
     * none do, and no other transaction on the same route interleaves (Postgres: the route row is
     * locked `FOR UPDATE`). Serialising per route is what makes the per-route version a gap-free
     * pull cursor.
     */
    public suspend fun <T> transaction(route: RouteId, block: SyncTransaction.() -> T): T

    /** Creates a route with its stops at version 1. Fails if it exists. */
    public suspend fun createRoute(route: RouteId, stops: List<Stop>)
}

public interface SyncTransaction {
    public val route: RouteId

    public fun exists(): Boolean

    /** Highest version of any stop on the route; the pull cursor. */
    public fun version(): Long

    public fun stop(id: StopId): Stop?

    /** Stores [stop] under a new version and returns it. */
    public fun writeStop(stop: Stop): Long

    public fun changesSince(cursor: Long): List<Stop>

    public fun lastProcessedSeq(client: ClientId): Long

    public fun result(client: ClientId, seq: Long): MutationResult?

    /** Records the ruling on [envelope] and advances the client's lastProcessedSeq to its seq. */
    public fun recordResult(client: ClientId, envelope: MutationEnvelope, result: MutationResult)
}
