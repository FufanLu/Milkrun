package com.milkrun.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
public enum class RejectReason {
    @SerialName("unknown_stop")
    UNKNOWN_STOP,

    @SerialName("unknown_parcel")
    UNKNOWN_PARCEL,

    @SerialName("already_delivered")
    ALREADY_DELIVERED,

    @SerialName("stop_cancelled")
    STOP_CANCELLED,

    @SerialName("no_parcels")
    NO_PARCELS,

    @SerialName("note_too_long")
    NOTE_TOO_LONG,

    @SerialName("stop_not_finished")
    STOP_NOT_FINISHED,
}

public sealed interface Decision {
    /** [stop] is the new state; it equals the input when the mutation was a no-op. */
    public data class Accepted(val stop: Stop) : Decision

    public data class Rejected(val reason: RejectReason) : Decision
}

/**
 * The stop state machine. Pure and deterministic: the phone runs it for optimistic updates and
 * rebase, the server runs the same compiled code to decide what actually happened.
 *
 * ```
 *               arrive                deliver
 *   PLANNED ───────────▶ ARRIVED ───────────────▶ DELIVERED (terminal)
 *      │  ▲                │  ▲                       ▲
 *      │  └── arrive ──────┼──┘ attempt               │ deliver: physical fact wins,
 *      │                   ▼                          │ flagged DELIVERED_AFTER_CANCEL
 *      └── attempt ──▶ ATTEMPTED ── deliver ──▶ …     │
 *                                                     │
 *   PLANNED/ARRIVED/ATTEMPTED ── cancel (dispatcher) ─▶ CANCELLED
 * ```
 */
public object StopRules {
    public const val MAX_NOTE_LENGTH: Int = 500

    public fun apply(stop: Stop, mutation: Mutation, hlc: Hlc): Decision {
        require(stop.id == mutation.stopId) { "mutation for ${mutation.stopId} applied to ${stop.id}" }
        return when (mutation) {
            is Mutation.Arrive -> arrive(stop, mutation)
            is Mutation.ScanParcel -> scan(stop, mutation)
            is Mutation.Deliver -> deliver(stop, mutation)
            is Mutation.Attempt -> attempt(stop, mutation)
            is Mutation.SetNote -> setNote(stop, mutation, hlc)
            is Mutation.AttachProof -> attachProof(stop, mutation)
        }
    }

    public fun apply(stop: Stop, command: DispatcherCommand): Decision = when (command) {
        is DispatcherCommand.Cancel -> {
            when (stop.status) {
                StopStatus.DELIVERED -> Decision.Rejected(RejectReason.ALREADY_DELIVERED)
                StopStatus.CANCELLED -> Decision.Accepted(stop)
                else -> Decision.Accepted(stop.copy(status = StopStatus.CANCELLED))
            }
        }

        is DispatcherCommand.Resequence -> {
            Decision.Accepted(stop.copy(sequence = command.sequence))
        }
    }

    private fun arrive(stop: Stop, m: Mutation.Arrive): Decision = when (stop.status) {
        StopStatus.DELIVERED -> {
            Decision.Rejected(RejectReason.ALREADY_DELIVERED)
        }

        StopStatus.CANCELLED -> {
            Decision.Rejected(RejectReason.STOP_CANCELLED)
        }

        StopStatus.ARRIVED -> {
            Decision.Accepted(stop)
        }

        StopStatus.PLANNED, StopStatus.ATTEMPTED -> {
            Decision.Accepted(
                stop.copy(status = StopStatus.ARRIVED, arrivedAtMillis = m.atMillis, attemptReason = null),
            )
        }
    }

    private fun scan(stop: Stop, m: Mutation.ScanParcel): Decision {
        val parcel = stop.parcel(m.parcelId) ?: return Decision.Rejected(RejectReason.UNKNOWN_PARCEL)
        return when (stop.status) {
            StopStatus.DELIVERED -> {
                Decision.Rejected(RejectReason.ALREADY_DELIVERED)
            }

            StopStatus.CANCELLED -> {
                Decision.Rejected(RejectReason.STOP_CANCELLED)
            }

            else -> {
                if (parcel.scanned) {
                    Decision.Accepted(stop)
                } else {
                    Decision.Accepted(stop.withParcels(setOf(m.parcelId)) { it.copy(scanned = true) })
                }
            }
        }
    }

    private fun deliver(stop: Stop, m: Mutation.Deliver): Decision {
        if (m.parcelIds.isEmpty()) return Decision.Rejected(RejectReason.NO_PARCELS)
        if (m.parcelIds.any { stop.parcel(it) == null }) return Decision.Rejected(RejectReason.UNKNOWN_PARCEL)
        if (stop.status == StopStatus.DELIVERED) return Decision.Rejected(RejectReason.ALREADY_DELIVERED)
        val delivered =
            stop.withParcels(m.parcelIds.toSet()) { it.copy(scanned = true, delivered = true) }.copy(
                status = StopStatus.DELIVERED,
                handoff = m.handoff,
                attemptReason = null,
                completedAtMillis = m.atMillis,
                arrivedAtMillis = stop.arrivedAtMillis ?: m.atMillis,
                // The parcels are physically gone; a cancel the driver never saw cannot undo that.
                conflict =
                if (stop.status == StopStatus.CANCELLED) {
                    ConflictFlag.DELIVERED_AFTER_CANCEL
                } else {
                    stop.conflict
                },
            )
        return Decision.Accepted(delivered)
    }

    private fun attempt(stop: Stop, m: Mutation.Attempt): Decision = when (stop.status) {
        StopStatus.DELIVERED -> {
            Decision.Rejected(RejectReason.ALREADY_DELIVERED)
        }

        StopStatus.CANCELLED -> {
            Decision.Rejected(RejectReason.STOP_CANCELLED)
        }

        else -> {
            Decision.Accepted(
                stop.copy(status = StopStatus.ATTEMPTED, attemptReason = m.reason, completedAtMillis = m.atMillis),
            )
        }
    }

    /** A photo proves a delivery or an attempt; the latest one wins (a retake replaces it). */
    private fun attachProof(stop: Stop, m: Mutation.AttachProof): Decision = when (stop.status) {
        StopStatus.DELIVERED, StopStatus.ATTEMPTED ->
            Decision.Accepted(stop.copy(proof = ProofRef(m.uploadId, m.sha256)))

        else -> Decision.Rejected(RejectReason.STOP_NOT_FINISHED)
    }

    private fun setNote(stop: Stop, m: Mutation.SetNote, hlc: Hlc): Decision = when {
        m.text.length > MAX_NOTE_LENGTH -> Decision.Rejected(RejectReason.NOTE_TOO_LONG)

        // Last writer wins on the HLC; an older write is accepted and has no effect.
        hlc <= stop.noteHlc -> Decision.Accepted(stop)

        else -> Decision.Accepted(stop.copy(note = m.text, noteHlc = hlc))
    }

    private inline fun Stop.withParcels(ids: Set<ParcelId>, change: (Parcel) -> Parcel): Stop =
        copy(parcels = parcels.map { if (it.id in ids) change(it) else it })
}
