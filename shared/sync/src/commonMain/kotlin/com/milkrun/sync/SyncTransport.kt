package com.milkrun.sync

import com.milkrun.model.ClientId
import com.milkrun.model.RouteId
import com.milkrun.protocol.PullResponse
import com.milkrun.protocol.PushRequest
import com.milkrun.protocol.PushResponse

/**
 * How the engine reaches the server. Production uses the Ktor implementation in `:shared:network`;
 * tests use an in-process server behind a simulated network.
 *
 * Implementations throw on any failure (timeout, 5xx, no connectivity). The engine treats every
 * throw as "outcome unknown" and relies on idempotent push to retry safely.
 */
public interface SyncTransport {
    public suspend fun push(route: RouteId, request: PushRequest): PushResponse

    public suspend fun pull(route: RouteId, client: ClientId, cursor: Long): PullResponse
}

/** The server expected a different next seq (HTTP 409 `seq_gap`). */
public class SeqGapException(public val expectedSeq: Long) : RuntimeException("server expects seq $expectedSeq")
