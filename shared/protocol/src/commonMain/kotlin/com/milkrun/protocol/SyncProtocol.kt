package com.milkrun.protocol

import com.milkrun.model.ClientId
import com.milkrun.model.Hlc
import com.milkrun.model.Mutation
import com.milkrun.model.RejectReason
import com.milkrun.model.RouteId
import com.milkrun.model.Stop
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Wire format of the sync API (ADR 0002). Shared verbatim by the server and both apps.
 *
 * ```
 * POST /v1/routes/{routeId}/push   PushRequest  -> PushResponse
 * GET  /v1/routes/{routeId}/pull?client={id}&cursor={n}  -> PullResponse
 * ```
 */
public object SyncApi {
    /** Bumped on breaking wire changes; the server rejects other majors with 426 Upgrade Required. */
    public const val PROTOCOL_VERSION: Int = 1
    public const val PROTOCOL_HEADER: String = "Milkrun-Protocol"
    public const val MAX_PUSH_BATCH: Int = 100

    public fun pushPath(route: RouteId): String = "/v1/routes/${route.value}/push"

    public fun pullPath(route: RouteId): String = "/v1/routes/${route.value}/pull"

    public val json: Json =
        Json {
            encodeDefaults = true
            ignoreUnknownKeys = true // forward compatible: an older app ignores fields it does not know
            explicitNulls = false
            classDiscriminator = "type"
        }
}

/** One recorded mutation as it travels: [seq] is dense per client and starts at 1. */
@Serializable
public data class MutationEnvelope(val seq: Long, val hlc: Hlc, val mutation: Mutation)

@Serializable
public data class PushRequest(val clientId: ClientId, val mutations: List<MutationEnvelope>) {
    init {
        require(mutations.size <= SyncApi.MAX_PUSH_BATCH) { "batch too large: ${mutations.size}" }
        require(mutations.zipWithNext().all { (a, b) -> b.seq == a.seq + 1 }) { "seqs must be contiguous" }
    }
}

@Serializable
public enum class Outcome {
    @SerialName("accepted")
    ACCEPTED,

    @SerialName("rejected")
    REJECTED,
}

/** The server's ruling on one mutation. Stored, so a retried push gets the same answer back. */
@Serializable
public data class MutationResult(val seq: Long, val outcome: Outcome, val reason: RejectReason? = null)

@Serializable
public data class PushResponse(
    /** Highest seq of this client the server has ruled on; every seq at or below it is final. */
    val lastProcessedSeq: Long,
    val results: List<MutationResult>,
    /** The server's clock after processing, merged into the client's HLC. */
    val serverHlc: Hlc,
)

@Serializable
public data class PullResponse(
    /** Pass back as `cursor` next time. Per-route version: every change has a higher one. */
    val cursor: Long,
    val lastProcessedSeq: Long,
    /** Full snapshots of every stop that changed after the requested cursor. */
    val changes: List<Stop>,
    val serverHlc: Hlc,
)

/** Body of every non-2xx response. */
@Serializable
public data class ApiError(
    val code: ErrorCode,
    val message: String,
    /** For [ErrorCode.SEQ_GAP]: the seq the server expects next. */
    val expectedSeq: Long? = null,
)

@Serializable
public enum class ErrorCode {
    @SerialName("seq_gap")
    SEQ_GAP,

    @SerialName("unknown_route")
    UNKNOWN_ROUTE,

    @SerialName("forbidden")
    FORBIDDEN,

    @SerialName("unsupported_protocol")
    UNSUPPORTED_PROTOCOL,

    @SerialName("bad_request")
    BAD_REQUEST,

    @SerialName("unavailable")
    UNAVAILABLE,
}
