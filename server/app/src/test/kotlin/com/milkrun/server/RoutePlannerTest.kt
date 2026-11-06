package com.milkrun.server

import com.milkrun.model.GeoPoint
import org.junit.jupiter.api.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RoutePlannerTest {
    private val depot = GeoPoint(47.6205, -122.3493) // Seattle Center

    private fun randomStops(r: Random, n: Int) = List(n) {
        GeoPoint(
            47.60 + r.nextDouble() * 0.05,
            -122.36 + r.nextDouble() * 0.06,
        )
    }

    @Test
    fun orderIsAPermutationAndNeverWorseThanNearestNeighbour() {
        val r = Random(42)
        repeat(200) {
            val stops = randomStops(r, r.nextInt(1, 40))
            val plan = RoutePlanner.plan(depot, stops)
            assertEquals(stops.indices.toSet(), plan.order.toSet())
            assertEquals(stops.size, plan.order.size)
            assertTrue(plan.optimisedKm <= plan.nearestNeighbourKm + 1e-9)
        }
    }

    @Test
    fun pointsOnACircleAreVisitedInCircleOrder() {
        // Shuffled points on a circle with the depot on it: the optimal closed tour is the circle.
        val n = 12
        val circle = List(n) { k ->
            val a = 2 * PI * (k + 1) / (n + 1)
            GeoPoint(47.62 + 0.01 * cos(a), -122.34 + 0.01 * sin(a))
        }
        val dep = GeoPoint(47.63, -122.34)
        val shuffled = circle.shuffled(Random(7))
        val plan = RoutePlanner.plan(dep, shuffled)
        val visited = plan.order.map { circle.indexOf(shuffled[it]) }
        assertTrue(visited == (0 until n).toList() || visited == (0 until n).reversed().toList(), "$visited")
    }

    @Test
    fun withinTenPercentOfOptimalOnSmallInstances() {
        val r = Random(3)
        var worst = 0.0
        repeat(40) {
            val stops = randomStops(r, 7)
            val plan = RoutePlanner.plan(depot, stops)
            val best = bruteForceKm(stops)
            worst = maxOf(worst, plan.optimisedKm / best - 1)
        }
        assertTrue(worst < 0.10, "worst gap to optimal ${"%.1f".format(worst * 100)}%")
    }

    private fun bruteForceKm(stops: List<GeoPoint>): Double {
        fun perms(xs: List<Int>): Sequence<List<Int>> =
            if (xs.size <= 1) sequenceOf(xs) else xs.asSequence().flatMap { x -> perms(xs - x).map { listOf(x) + it } }
        return perms(stops.indices.toList()).minOf { p ->
            val path = listOf(depot) + p.map { stops[it] } + depot
            path.zipWithNext().sumOf { (a, b) -> RoutePlanner.haversineKm(a, b) }
        }
    }
}
