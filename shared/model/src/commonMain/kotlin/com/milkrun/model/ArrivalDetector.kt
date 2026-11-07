package com.milkrun.model

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** One GPS reading: where, how sure, and when (device wall clock). */
public data class Fix(val point: GeoPoint, val accuracyMeters: Double, val atMillis: Long)

/**
 * Decides when the van has *arrived* at the next stop, from a stream of GPS fixes.
 *
 * Urban GPS jumps by tens of metres (glass towers, parking garages), so a single fix inside the
 * radius proves nothing. Arrival needs the driver to **stay** inside the enter radius for
 * [dwellMillis], counting only fixes whose accuracy is good enough to mean something. Leaving
 * needs them to go past a wider exit radius (hysteresis), so a fix that bounces across the edge
 * does not toggle arrival on and off.
 *
 * Pure and deterministic: both apps feed it platform location updates, and tests feed it recorded
 * or synthetic tracks.
 */
public class ArrivalDetector(
    private val enterRadiusMeters: Double = 40.0,
    private val exitRadiusMeters: Double = 80.0,
    private val dwellMillis: Long = 20_000,
    private val maxAccuracyMeters: Double = 50.0,
) {
    public sealed interface Event {
        public data class Arrived(val stop: StopId, val atMillis: Long) : Event

        public data class Left(val stop: StopId, val atMillis: Long) : Event
    }

    private var target: Pair<StopId, GeoPoint>? = null
    private var insideSince: Long? = null
    private var arrived = false

    /** Switches to a new stop; any dwell in progress is discarded. */
    public fun target(stop: StopId, location: GeoPoint) {
        if (target?.first == stop) return
        target = stop to location
        insideSince = null
        arrived = false
    }

    /** Feeds one fix; returns an event when arrival or departure is decided. */
    public fun onFix(fix: Fix): Event? {
        val (stop, place) = target ?: return null
        if (fix.accuracyMeters > maxAccuracyMeters) return null // too vague to count either way
        val d = distanceMeters(fix.point, place)
        if (!arrived) {
            if (d <= enterRadiusMeters) {
                val since = insideSince ?: fix.atMillis.also { insideSince = it }
                if (fix.atMillis - since >= dwellMillis) {
                    arrived = true
                    return Event.Arrived(stop, fix.atMillis)
                }
            } else {
                insideSince = null
            }
            return null
        }
        if (d > exitRadiusMeters) {
            arrived = false
            insideSince = null
            return Event.Left(stop, fix.atMillis)
        }
        return null
    }

    /**
     * Time passing with no new fix. Location services go quiet when the device stops moving
     * (Core Location's stationary pause, a fused provider with nothing new to report), which is
     * exactly what parking looks like. So silence after a good fix inside the radius counts as
     * staying there; any movement brings a fresh fix through [onFix], which can reset the dwell.
     */
    public fun onTick(nowMillis: Long): Event? {
        val (stop, _) = target ?: return null
        val since = insideSince ?: return null
        if (arrived || nowMillis - since < dwellMillis) return null
        arrived = true
        return Event.Arrived(stop, nowMillis)
    }

    public companion object {
        private const val EARTH_RADIUS_M = 6_371_008.8

        public fun distanceMeters(a: GeoPoint, b: GeoPoint): Double {
            val toRad = kotlin.math.PI / 180
            val dLat = (b.lat - a.lat) * toRad
            val dLng = (b.lng - a.lng) * toRad
            val h = sin(dLat / 2).pow(2) + cos(a.lat * toRad) * cos(b.lat * toRad) * sin(dLng / 2).pow(2)
            return 2 * EARTH_RADIUS_M * asin(sqrt(h))
        }
    }
}
