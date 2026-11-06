package com.milkrun.server.sync

import com.milkrun.model.ClientId
import com.milkrun.model.Decision
import com.milkrun.model.DispatcherCommand
import com.milkrun.model.Hlc
import com.milkrun.model.HlcClock
import com.milkrun.model.RejectReason
import com.milkrun.model.RouteId
import com.milkrun.model.Stop
import com.milkrun.model.StopRules
import com.milkrun.protocol.MutationEnvelope
import com.milkrun.protocol.MutationResult
import com.milkrun.protocol.Outcome
import com.milkrun.protocol.PullResponse
import com.milkrun.protocol.PushRequest
import com.milkrun.protocol.PushResponse

public sealed class SyncException(message: String) : RuntimeException(message) {
    public class UnknownRoute(public val route: RouteId) : SyncException("unknown route $route")

    public class SeqGap(public val client: ClientId, public val expected: Long, public val got: Long) :
        SyncException("client $client sent seq $got, expected $expected")
}

/** Hooks for metrics and the simulator's invariant checks. */
public interface SyncObserver {
    public fun onRuling(route: RouteId, client: ClientId, envelope: MutationEnvelope, result: MutationResult) {}

    public fun onDuplicate(route: RouteId, client: ClientId, seq: Long) {}

    public companion object {
        public val NONE: SyncObserver = object : SyncObserver {}
    }
}

/**
 * The server half of the sync protocol (ADR 0002). Stateless: everything lives in [store].
 *
 * Push, per mutation, in one route-serialised transaction:
 * ```
 *   last = lastProcessedSeq(client)
 *   seq <= last      -> duplicate: return the stored ruling, change nothing
 *   seq >  last + 1  -> SeqGap (the client skipped a mutation; never happens with a healthy outbox)
 *   seq == last + 1  -> StopRules.apply(stop, mutation)  -> write stop at version+1 if it changed
 *                       record ruling, lastProcessedSeq = seq
 * ```
 */
public class SyncService(
    private val store: SyncStore,
    wallClock: () -> Long,
    private val observer: SyncObserver = SyncObserver.NONE,
) {
    private val clock = HlcClock("server", wallClock)

    public suspend fun push(route: RouteId, request: PushRequest): PushResponse {
        val results = ArrayList<MutationResult>(request.mutations.size)
        var last = 0L
        for (env in request.mutations) {
            val (result, lastSeq) = store.transaction(route) { processOne(request.clientId, env) }
            results += result
            last = lastSeq
        }
        if (request.mutations.isEmpty()) {
            last =
                store.transaction(route) {
                    if (!exists()) throw SyncException.UnknownRoute(route)
                    lastProcessedSeq(request.clientId)
                }
        }
        return PushResponse(lastProcessedSeq = last, results = results, serverHlc = tick())
    }

    private fun SyncTransaction.processOne(client: ClientId, env: MutationEnvelope): Pair<MutationResult, Long> {
        if (!exists()) throw SyncException.UnknownRoute(route)
        val last = lastProcessedSeq(client)
        if (env.seq <= last) {
            observer.onDuplicate(route, client, env.seq)
            val stored = checkNotNull(result(client, env.seq)) { "no stored ruling for $client/${env.seq}" }
            return stored to last
        }
        if (env.seq != last + 1) throw SyncException.SeqGap(client, expected = last + 1, got = env.seq)

        observe(env.hlc)
        val stop = stop(env.mutation.stopId)
        val result =
            if (stop == null) {
                MutationResult(env.seq, Outcome.REJECTED, RejectReason.UNKNOWN_STOP)
            } else {
                when (val d = StopRules.apply(stop, env.mutation, env.hlc)) {
                    is Decision.Rejected -> {
                        MutationResult(env.seq, Outcome.REJECTED, d.reason)
                    }

                    is Decision.Accepted -> {
                        if (d.stop != stop) writeStop(d.stop)
                        MutationResult(env.seq, Outcome.ACCEPTED)
                    }
                }
            }
        recordResult(client, env, result)
        observer.onRuling(route, client, env, result)
        return result to env.seq
    }

    public suspend fun pull(route: RouteId, client: ClientId, cursor: Long): PullResponse {
        val (changes, version, last) =
            store.transaction(route) {
                if (!exists()) throw SyncException.UnknownRoute(route)
                val version = version()
                // A cursor from the future means the server was restored from a backup: resend everything.
                val from = if (cursor > version) 0 else cursor
                Triple(changesSince(from), version, lastProcessedSeq(client))
            }
        return PullResponse(cursor = version, lastProcessedSeq = last, changes = changes, serverHlc = tick())
    }

    /** A dispatcher action, ruled on by the same state machine as driver mutations. */
    public suspend fun dispatch(route: RouteId, command: DispatcherCommand): Decision = store.transaction(route) {
        if (!exists()) throw SyncException.UnknownRoute(route)
        val stop = stop(command.stopId) ?: return@transaction Decision.Rejected(RejectReason.UNKNOWN_STOP)
        val d = StopRules.apply(stop, command)
        if (d is Decision.Accepted && d.stop != stop) writeStop(d.stop)
        d
    }

    public suspend fun createRoute(route: RouteId, stops: List<Stop>): Unit = store.createRoute(route, stops)

    public suspend fun snapshot(route: RouteId): List<Stop> = store.transaction(route) { changesSince(0) }

    private fun tick(): Hlc = synchronized(clock) { clock.now() }

    private fun observe(remote: Hlc) {
        synchronized(clock) {
            // A phone with a clock far in the future must not drag the server's clock with it.
            runCatching { clock.observe(remote) }
        }
    }
}
