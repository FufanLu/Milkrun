package com.milkrun.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Something a driver did. Mutations are recorded on the phone, replayed during rebase and ruled on
 * by the server, all through [StopRules]. They name *intent*, not resulting state: "delivered these
 * parcels", not "set status = delivered".
 *
 * Every timestamp is the device's wall clock at the moment of the action, kept for the audit trail;
 * ordering between devices uses the [Hlc] on the envelope instead.
 */
@Serializable
public sealed interface Mutation {
    public val stopId: StopId

    @Serializable
    @SerialName("arrive")
    public data class Arrive(override val stopId: StopId, val atMillis: Long) : Mutation

    @Serializable
    @SerialName("scan_parcel")
    public data class ScanParcel(override val stopId: StopId, val parcelId: ParcelId, val atMillis: Long) : Mutation

    @Serializable
    @SerialName("deliver")
    public data class Deliver(
        override val stopId: StopId,
        val parcelIds: List<ParcelId>,
        val handoff: HandoffMethod,
        val atMillis: Long,
    ) : Mutation

    @Serializable
    @SerialName("attempt")
    public data class Attempt(override val stopId: StopId, val reason: AttemptReason, val atMillis: Long) : Mutation

    @Serializable
    @SerialName("set_note")
    public data class SetNote(override val stopId: StopId, val text: String) : Mutation

    /**
     * A proof-of-delivery photo for a finished stop. The bytes travel separately (resumable
     * upload, `uploadId`); this records which upload belongs to the stop and its SHA-256.
     */
    @Serializable
    @SerialName("attach_proof")
    public data class AttachProof(override val stopId: StopId, val uploadId: String, val sha256: String) : Mutation
}

/** Something a dispatcher did. Only the server issues these. */
@Serializable
public sealed interface DispatcherCommand {
    public val stopId: StopId

    @Serializable
    @SerialName("cancel")
    public data class Cancel(override val stopId: StopId) : DispatcherCommand

    @Serializable
    @SerialName("resequence")
    public data class Resequence(override val stopId: StopId, val sequence: Int) : DispatcherCommand
}

/** The `type` discriminator on the wire; also what the outbox, the server's log and metrics record. */
public val Mutation.wireName: String
    get() = when (this) {
        is Mutation.Arrive -> "arrive"
        is Mutation.ScanParcel -> "scan_parcel"
        is Mutation.Deliver -> "deliver"
        is Mutation.Attempt -> "attempt"
        is Mutation.SetNote -> "set_note"
        is Mutation.AttachProof -> "attach_proof"
    }
