package com.milkrun.server

import com.milkrun.model.GeoPoint
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Orders a driver's stops: nearest-neighbour from the depot, then 2-opt until no reversal
 * shortens the tour. Tours are closed (the van returns to the station).
 *
 * This is a heuristic on great-circle distance, not road distance. Production planning would use a
 * road graph and time windows. The point here is a deterministic, testable first pass that the
 * dispatcher can then re-sequence by hand.
 */
public object RoutePlanner {
    public data class Plan(val order: List<Int>, val nearestNeighbourKm: Double, val optimisedKm: Double)

    public fun plan(depot: GeoPoint, stops: List<GeoPoint>, maxPasses: Int = 50): Plan {
        if (stops.isEmpty()) return Plan(emptyList(), 0.0, 0.0)
        val points = listOf(depot) + stops // index 0 is the depot
        val d = Array(points.size) { i -> DoubleArray(points.size) { j -> haversineKm(points[i], points[j]) } }

        val nn = nearestNeighbour(d)
        val nnKm = tourKm(nn, d)
        val tour = nn.toIntArray()
        var improved = true
        var passes = 0
        while (improved && passes < maxPasses) {
            improved = false
            passes++
            // tour[0] is the depot and stays put; reverse segments tour[i..j] for 1 <= i < j.
            for (i in 1 until tour.size - 1) {
                for (j in i + 1 until tour.size) {
                    val a = tour[i - 1]
                    val b = tour[i]
                    val c = tour[j]
                    val e = tour[(j + 1) % tour.size]
                    val delta = d[a][c] + d[b][e] - d[a][b] - d[c][e]
                    if (delta < -EPSILON) {
                        tour.reverse(i, j + 1)
                        improved = true
                    }
                }
            }
        }
        val order = tour.drop(1).map { it - 1 }
        return Plan(order, nnKm, tourKm(tour.toList(), d))
    }

    private fun nearestNeighbour(d: Array<DoubleArray>): List<Int> {
        val visited = BooleanArray(d.size).also { it[0] = true }
        val tour = mutableListOf(0)
        repeat(d.size - 1) {
            val from = tour.last()
            var best = -1
            for (k in d.indices) if (!visited[k] && (best == -1 || d[from][k] < d[from][best])) best = k
            visited[best] = true
            tour += best
        }
        return tour
    }

    internal fun tourKm(tour: List<Int>, d: Array<DoubleArray>): Double =
        tour.indices.sumOf { d[tour[it]][tour[(it + 1) % tour.size]] }

    public fun haversineKm(a: GeoPoint, b: GeoPoint): Double {
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLng = Math.toRadians(b.lng - a.lng)
        val h = sin(dLat / 2).pow(2) + cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLng / 2).pow(2)
        return 2 * EARTH_RADIUS_KM * asin(sqrt(h))
    }

    private const val EARTH_RADIUS_KM = 6371.0088
    private const val EPSILON = 1e-9
}
