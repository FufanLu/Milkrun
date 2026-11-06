package com.milkrun.model

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

/** A driver's route for one shift (for example `R-2026-11-06-DBO6-014`). */
@Serializable @JvmInline
public value class RouteId(public val value: String) {
    override fun toString(): String = value
}

/** One address on a route. A stop can carry several parcels. */
@Serializable @JvmInline
public value class StopId(public val value: String) {
    override fun toString(): String = value
}

/** One physical package, identified by the barcode on its label. */
@Serializable @JvmInline
public value class ParcelId(public val value: String) {
    override fun toString(): String = value
}

/**
 * One installation of the app. A driver who carries a phone and a handheld has two clients on the
 * same route; each has its own mutation sequence.
 */
@Serializable @JvmInline
public value class ClientId(public val value: String) {
    override fun toString(): String = value
}
