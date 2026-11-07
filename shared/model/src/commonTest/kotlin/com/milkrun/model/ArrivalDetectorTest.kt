package com.milkrun.model

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ArrivalDetectorTest {
    private val stop = StopId("S-014")
    private val door = GeoPoint(47.6223, -122.3365) // 410 Terry Ave N

    /** A point [north] metres north and [east] metres east of [door]. */
    private fun offset(north: Double, east: Double) = GeoPoint(
        door.lat + north / 111_320.0,
        door.lng + east / (111_320.0 * kotlin.math.cos(door.lat * kotlin.math.PI / 180)),
    )

    private fun detector() = ArrivalDetector().also { it.target(stop, door) }

    @Test
    fun arrivesOnlyAfterDwellingInsideTheRadius() {
        val d = detector()
        assertNull(d.onFix(Fix(offset(10.0, 0.0), 8.0, 0)))
        assertNull(d.onFix(Fix(offset(12.0, 3.0), 8.0, 10_000)))
        assertEquals(ArrivalDetector.Event.Arrived(stop, 20_000), d.onFix(Fix(offset(9.0, -2.0), 8.0, 20_000)))
        assertNull(d.onFix(Fix(offset(8.0, 0.0), 8.0, 30_000)), "arrival is reported once")
    }

    @Test
    fun drivingPastDoesNotCountAsArrival() {
        val d = detector()
        // 15 m/s along the street, passing within 20 m of the door: never 20 s inside 40 m.
        val events = (0..20).mapNotNull { s -> d.onFix(Fix(offset(20.0, -150.0 + 15.0 * s), 6.0, s * 1_000L)) }
        assertEquals(emptyList(), events)
    }

    @Test
    fun vagueFixesAreIgnored() {
        val d = detector()
        assertNull(d.onFix(Fix(door, 120.0, 0)))
        assertNull(d.onFix(Fix(door, 120.0, 30_000)), "a 120 m fix can't prove the van is at the door")
    }

    @Test
    fun jitterAcrossTheEdgeDoesNotFlapAfterArrival() {
        val d = detector()
        d.onFix(Fix(door, 5.0, 0))
        assertTrue(d.onFix(Fix(door, 5.0, 20_000)) is ArrivalDetector.Event.Arrived)
        // Bouncing between 30 m and 70 m: inside the exit radius, so still arrived.
        val r = Random(4)
        val events = (1..60).mapNotNull { i ->
            d.onFix(
                Fix(
                    offset(30.0 + r.nextDouble() * 40, 0.0),
                    10.0,
                    20_000L + i * 1_000,
                ),
            )
        }
        assertEquals(emptyList(), events)
        assertTrue(d.onFix(Fix(offset(120.0, 0.0), 10.0, 90_000)) is ArrivalDetector.Event.Left)
    }

    @Test
    fun retargetingDiscardsDwell() {
        val d = detector()
        d.onFix(Fix(door, 5.0, 0))
        d.target(StopId("S-015"), offset(500.0, 0.0))
        assertNull(d.onFix(Fix(door, 5.0, 25_000)))
    }

    @Test
    fun distanceIsMetres() {
        val m = ArrivalDetector.distanceMeters(door, offset(100.0, 0.0))
        assertTrue(m in 99.5..100.5, "$m")
    }
}
