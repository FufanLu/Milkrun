package com.milkrun.presentation

import com.milkrun.model.AttemptReason
import com.milkrun.model.HandoffMethod
import com.milkrun.model.ParcelId
import com.milkrun.model.StopId
import com.milkrun.model.StopStatus

/** Everything the route screen draws. Platform UIs render it; they decide nothing. */
public data class RouteUiState(
    val routeLabel: String,
    val loaded: Boolean,
    val remaining: Int,
    val delivered: Int,
    val attempted: Int,
    val sync: SyncBanner,
    /** The one stop drawn at full strength. Null when the route is finished. */
    val next: StopCard?,
    val upcoming: List<StopCard>,
    val done: List<StopCard>,
    val notices: List<Notice>,
) {
    public val finished: Boolean get() = loaded && next == null

    public companion object {
        public val LOADING: RouteUiState =
            RouteUiState("", false, 0, 0, 0, SyncBanner.AllSent(null), null, emptyList(), emptyList(), emptyList())
    }
}

/** The tape strip under the header. */
public sealed interface SyncBanner {
    /** Nothing waiting. [lastSuccessAtMillis] is null before the first successful sync. */
    public data class AllSent(val lastSuccessAtMillis: Long?) : SyncBanner

    /** Work is recorded on the phone and not yet confirmed. */
    public data class Waiting(val pending: Long, val retrying: Boolean, val lastError: String?) : SyncBanner {
        /** Says "waiting for signal" only once a send has failed; before that it is simply sending. */
        val title: String get() = when {
            retrying && pending == 1L -> "1 waiting for signal"
            retrying -> "$pending waiting for signal"
            pending == 1L -> "Sending 1 change"
            else -> "Sending $pending changes"
        }
        val detail: String get() = if (retrying) "Retrying" else ""
    }
}

public data class StopCard(
    val id: StopId,
    val sequence: Int,
    /** "07": the headline of a stop, as stencilled on the tote. */
    val number: String,
    val address: String,
    val parcelCount: Int,
    val scannedCount: Int,
    val status: StopStatus,
    val attemptReason: AttemptReason?,
    val handoff: HandoffMethod?,
    val unsynced: Boolean,
    /** Set when a delivery overrode a cancel the driver never saw. */
    val deliveredAfterCancel: Boolean,
    val note: String,
) {
    val statusLabel: String get() = Copy.status(this)

    /** Stable string identity for UI lists (Swift sees value-class ids as opaque objects). */
    val key: String get() = id.value
}

/** A ruling from the server the driver has not acknowledged yet. */
public data class Notice(val seq: Long, val stopNumber: String, val title: String, val body: String)

public data class StopDetailState(
    val card: StopCard,
    val parcels: List<ParcelRow>,
    val canArrive: Boolean,
    val canScan: Boolean,
    val canDeliver: Boolean,
    val canAttempt: Boolean,
)

public data class ParcelRow(
    val id: ParcelId,
    // NSObject already owns `description` in Swift; name it plainly there.
    @OptIn(kotlin.experimental.ExperimentalObjCName::class)
    @kotlin.native.ObjCName("itemDescription")
    val description: String,
    val scanned: Boolean,
    val delivered: Boolean,
) {
    /** Last five characters of the label barcode, the part drivers read aloud. */
    val shortCode: String get() = id.value.takeLast(5)

    val key: String get() = id.value
}
