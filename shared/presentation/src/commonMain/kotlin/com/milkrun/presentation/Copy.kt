package com.milkrun.presentation

import com.milkrun.model.AttemptReason
import com.milkrun.model.HandoffMethod
import com.milkrun.model.RejectReason
import com.milkrun.model.StopStatus

/**
 * Every sentence a driver reads, in one place so both apps say the same thing. Rulings are told in
 * the driver's terms: what happened to which stop, and what to do now. Never a protocol code.
 */
public object Copy {
    public fun status(card: StopCard): String = when (card.status) {
        StopStatus.PLANNED -> parcels(card.parcelCount)
        StopStatus.ARRIVED -> "Arrived · ${card.scannedCount} of ${card.parcelCount} scanned"
        StopStatus.DELIVERED -> "Delivered · ${handoff(card.handoff)}"
        StopStatus.ATTEMPTED -> "Not delivered · ${attempt(card.attemptReason)}"
        StopStatus.CANCELLED -> "Cancelled by dispatch"
    }

    public fun parcels(n: Int): String = if (n == 1) "1 parcel" else "$n parcels"

    public fun handoff(h: HandoffMethod?): String = when (h) {
        HandoffMethod.IN_PERSON -> "handed over"
        HandoffMethod.FRONT_DOOR -> "front door"
        HandoffMethod.MAILROOM -> "mailroom"
        HandoffMethod.LOCKER -> "locker"
        null -> "delivered"
    }

    public fun handoffOption(h: HandoffMethod): String = when (h) {
        HandoffMethod.IN_PERSON -> "In person"
        HandoffMethod.FRONT_DOOR -> "Door"
        HandoffMethod.MAILROOM -> "Mailroom"
        HandoffMethod.LOCKER -> "Locker"
    }

    public fun attempt(r: AttemptReason?): String = when (r) {
        AttemptReason.NO_ACCESS -> "no access"
        AttemptReason.NOBODY_HOME -> "nobody home"
        AttemptReason.UNSAFE_LOCATION -> "unsafe location"
        AttemptReason.BUSINESS_CLOSED -> "business closed"
        null -> "reason not recorded"
    }

    public fun attemptOption(r: AttemptReason): String = when (r) {
        AttemptReason.NO_ACCESS -> "No access"
        AttemptReason.NOBODY_HOME -> "Nobody home"
        AttemptReason.UNSAFE_LOCATION -> "Unsafe location"
        AttemptReason.BUSINESS_CLOSED -> "Business closed"
    }

    public const val DELIVERED_AFTER_CANCEL: String = "Delivered after cancel · dispatch notified"
    public const val ALL_SENT: String = "All sent"
    public const val ROUTE_DONE_TITLE: String = "Route done"

    public fun routeDoneBody(delivered: Int, attempted: Int): String =
        "$delivered delivered, $attempted not delivered. Head back to the station."

    /** A server ruling against an action on stop [stop]. */
    public fun rejection(stop: String, reason: RejectReason): Pair<String, String> = when (reason) {
        RejectReason.ALREADY_DELIVERED ->
            "Stop $stop was already delivered" to
                "Another device on your route delivered it first. Nothing more to do here."

        RejectReason.STOP_CANCELLED ->
            // A Deliver is never rejected for this: it is accepted and flagged (DELIVERED_AFTER_CANCEL).
            "Stop $stop was cancelled by dispatch" to "Keep its parcels on the van and bring them back to the station."

        RejectReason.UNKNOWN_STOP ->
            "Stop $stop is no longer on your route" to "Dispatch moved it. Keep its parcels on the van."

        RejectReason.UNKNOWN_PARCEL ->
            "A parcel at stop $stop isn't on this route" to "Check the label against the stop before you leave it."

        RejectReason.NO_PARCELS ->
            "Stop $stop needs at least one parcel" to "Scan the parcels you are leaving, then deliver again."

        RejectReason.NOTE_TOO_LONG ->
            "The note on stop $stop was too long" to "Keep notes under 500 characters."
    }
}
