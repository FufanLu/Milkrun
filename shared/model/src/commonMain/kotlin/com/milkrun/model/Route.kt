package com.milkrun.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
public enum class StopStatus {
    /** On the route, not yet reached. */
    @SerialName("planned")
    PLANNED,

    /** Driver is at the address (geofence entered or tapped "Arrived"). */
    @SerialName("arrived")
    ARRIVED,

    /** Parcels handed over. Terminal. */
    @SerialName("delivered")
    DELIVERED,

    /** Driver was there but could not deliver (no access, nobody home…). Can be re-attempted. */
    @SerialName("attempted")
    ATTEMPTED,

    /** Removed by the dispatcher. */
    @SerialName("cancelled")
    CANCELLED,
    ;

    public val isTerminal: Boolean get() = this == DELIVERED
}

@Serializable
public enum class HandoffMethod {
    @SerialName("in_person")
    IN_PERSON,

    @SerialName("front_door")
    FRONT_DOOR,

    @SerialName("mailroom")
    MAILROOM,

    @SerialName("locker")
    LOCKER,
}

@Serializable
public enum class AttemptReason {
    @SerialName("no_access")
    NO_ACCESS,

    @SerialName("nobody_home")
    NOBODY_HOME,

    @SerialName("unsafe_location")
    UNSAFE_LOCATION,

    @SerialName("business_closed")
    BUSINESS_CLOSED,
}

/** Set by the server when a physical fact overrode a plan (see ADR 0002). */
@Serializable
public enum class ConflictFlag {
    /** The dispatcher cancelled the stop while the driver, offline, delivered it. Delivery stands. */
    @SerialName("delivered_after_cancel")
    DELIVERED_AFTER_CANCEL,
}

@Serializable
public data class GeoPoint(val lat: Double, val lng: Double)

@Serializable
public data class Parcel(
    val id: ParcelId,
    val description: String,
    val scanned: Boolean = false,
    val delivered: Boolean = false,
)

@Serializable
public data class Stop(
    val id: StopId,
    val routeId: RouteId,
    /** 1-based position in the driver's order. Changed by the dispatcher, never by the phone. */
    val sequence: Int,
    val address: String,
    val location: GeoPoint,
    val parcels: List<Parcel>,
    val status: StopStatus = StopStatus.PLANNED,
    val attemptReason: AttemptReason? = null,
    val handoff: HandoffMethod? = null,
    val arrivedAtMillis: Long? = null,
    val completedAtMillis: Long? = null,
    val note: String = "",
    val noteHlc: Hlc = Hlc.ZERO,
    val conflict: ConflictFlag? = null,
) {
    public fun parcel(id: ParcelId): Parcel? = parcels.firstOrNull { it.id == id }
}
