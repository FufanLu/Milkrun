package com.milkrun.sync

import com.milkrun.model.RejectReason
import com.milkrun.model.Stop
import com.milkrun.model.StopId

public data class SyncStatus(
    /** Mutations recorded on this device and not yet ruled on by the server. */
    val pending: Long,
    val phase: Phase,
    val lastSuccessAtMillis: Long?,
    val consecutiveFailures: Int,
    val lastError: String?,
) {
    public enum class Phase { IDLE, SYNCING, BACKING_OFF }

    public companion object {
        public val INITIAL: SyncStatus = SyncStatus(0, Phase.IDLE, null, 0, null)
    }
}

/** A stop as the UI shows it, with whether this device still has unconfirmed changes to it. */
public data class StopItem(val stop: Stop, val unsynced: Boolean)

public data class RouteProgress(val total: Int, val delivered: Int, val attempted: Int, val unsynced: Int) {
    val remaining: Int get() = total - delivered - attempted
}

/** A mutation the server ruled against. */
public data class Rejection(val seq: Long, val stopId: StopId, val mutationType: String, val reason: RejectReason)

public sealed interface RecordResult {
    public data class Recorded(val seq: Long, val stop: Stop) : RecordResult

    /** The mutation was invalid against the current local view and was not recorded. */
    public data class Refused(val reason: RejectReason) : RecordResult
}

public data class SyncConfig(val pushBatchSize: Int = 50, val periodicIntervalMillis: Long = 60_000)
