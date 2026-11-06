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
