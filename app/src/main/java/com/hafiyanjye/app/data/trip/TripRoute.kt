package com.hafiyanjye.app.data.trip

import com.hafiyanjye.app.ui.location.GeoPoint
import com.hafiyanjye.app.ui.location.distanceKm
import java.util.Locale
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** One turn of a road route, pinned to how far along the route it begins. */
data class RouteStep(
    val instructions: String,
    /** Routes API maneuver, e.g. "TURN_LEFT"; "" when unknown. */
    val maneuver: String,
    /** Metres from the start of the route to where this step begins. */
    val startAlong: Double,
)

/** A road route with its turns, ready for progress maths (web: `TripRoute` in route.ts). */
data class TripRoute(
    val path: List<GeoPoint>,
    /** cumulative[i] = metres from path[0] to path[i]. */
    val cumulative: List<Double>,
    val totalMeters: Double,
    val durationSeconds: Double,
    val steps: List<RouteStep>,
    val mode: String,
)

/** The next turn ahead and how far away it is. */
data class NextTurn(val step: RouteStep, val inMeters: Double)

/** Where the person is on the route and what is still ahead (web: `RouteProgress`). */
data class RouteProgress(
    val along: Double,
    /** Metres from the person to the closest point of the route. */
    val offRoute: Double,
    /** The rest of the route, starting at the person's closest point. */
    val remainingPath: List<GeoPoint>,
    val remainingMeters: Double,
    val remainingSeconds: Double,
    /** Null when only the arrival is left. */
    val next: NextTurn?,
)

/** A step as the Routes API gives it, before it is pinned to the path. */
data class RawStep(val instructions: String, val maneuver: String, val start: GeoPoint?)

/** Road-route maths for the live trip, with no map involved. Distances are metres. */
object TripRoutes {

    /** Metres between two points. */
    fun meters(a: GeoPoint, b: GeoPoint): Double = distanceKm(a, b) * 1000

    /**
     * Builds a route from the Routes API pieces. Steps are pinned to the path by
     * their start point; the "depart" step is dropped (it is where they are).
     */
    fun build(path: List<GeoPoint>, durationSeconds: Double, steps: List<RawStep>, mode: String): TripRoute? {
        val clean = path.filter { it.lat.isFinite() && it.lng.isFinite() }
        if (clean.size < 2) return null
        val cumulative = cumulative(clean)
        val pinned = mutableListOf<RouteStep>()
        var from = 0
        for (step in steps) {
            val start = step.start ?: continue
            if (step.instructions.isBlank()) continue
            val index = nearestIndex(clean, start, from)
            from = index
            if (step.maneuver == "DEPART") continue
            pinned += RouteStep(step.instructions.trim(), step.maneuver, cumulative[index])
        }
        return TripRoute(
            path = clean,
            cumulative = cumulative,
            totalMeters = cumulative.last(),
            durationSeconds = max(0.0, durationSeconds),
            steps = pinned,
            mode = mode,
        )
    }

    /** Where [position] is on [route], and what is still ahead. */
    fun progress(route: TripRoute, position: GeoPoint): RouteProgress {
        var bestDistance = Double.POSITIVE_INFINITY
        var bestSegment = 0
        var bestPoint = route.path.first()
        var bestT = 0.0
        for (i in 0 until route.path.size - 1) {
            val (t, point) = project(position, route.path[i], route.path[i + 1])
            val distance = meters(position, point)
            if (distance < bestDistance) {
                bestDistance = distance
                bestSegment = i
                bestPoint = point
                bestT = t
            }
        }
        val segmentLength = route.cumulative[bestSegment + 1] - route.cumulative[bestSegment]
        val along = route.cumulative[bestSegment] + segmentLength * bestT
        val remainingMeters = max(0.0, route.totalMeters - along)
        val share = if (route.totalMeters > 0) remainingMeters / route.totalMeters else 0.0
        // A few metres of slack, so a turn the person is standing on is not announced again.
        val ahead = route.steps.firstOrNull { it.startAlong > along + StepSlackMeters }
        return RouteProgress(
            along = along,
            offRoute = bestDistance,
            remainingPath = listOf(bestPoint) + route.path.drop(bestSegment + 1),
            remainingMeters = remainingMeters,
            remainingSeconds = route.durationSeconds * share,
            next = ahead?.let { NextTurn(it, it.startAlong - along) },
        )
    }

    /** Off the route by more than GPS can explain: time to ask for a new one. */
    fun isOffRoute(progress: RouteProgress, accuracyMeters: Double?): Boolean =
        progress.offRoute > max(60.0, min(accuracyMeters ?: 0.0, 150.0))

    /** "150 m", "1.2 km": rounded the way people say it while moving. */
    fun roadDistanceText(meters: Double): String =
        if (meters < 1000) {
            "${max(10, (meters / 10).roundToInt() * 10)} m"
        } else {
            String.format(Locale.US, "%.1f km", meters / 1000)
        }

    /** "about 4 min" from seconds, never less than a minute. */
    fun etaText(seconds: Double): String {
        val minutes = max(1, (seconds / 60).roundToInt())
        return if (minutes >= 60) {
            "about ${String.format(Locale.US, "%.1f", (minutes / 6.0).roundToInt() / 10.0).removeSuffix(".0")} h"
        } else {
            "about $minutes min"
        }
    }

    private const val StepSlackMeters = 5.0

    /** Closest point to [p] on segment a→b in a local flat projection (fine for city distances); t is 0 at a. */
    private fun project(p: GeoPoint, a: GeoPoint, b: GeoPoint): Pair<Double, GeoPoint> {
        val kx = cos(Math.toRadians(a.lat))
        val dx = (b.lng - a.lng) * kx
        val dy = b.lat - a.lat
        val px = (p.lng - a.lng) * kx
        val py = p.lat - a.lat
        val lengthSquared = dx * dx + dy * dy
        val t = if (lengthSquared == 0.0) 0.0 else ((px * dx + py * dy) / lengthSquared).coerceIn(0.0, 1.0)
        return t to GeoPoint(a.lat + dy * t, a.lng + (b.lng - a.lng) * t)
    }

    private fun cumulative(path: List<GeoPoint>): List<Double> {
        val result = ArrayList<Double>(path.size)
        result += 0.0
        for (i in 1 until path.size) result += result[i - 1] + meters(path[i - 1], path[i])
        return result
    }

    /** Index of the path point closest to [p], searching from [from] on, so steps stay in route order. */
    private fun nearestIndex(path: List<GeoPoint>, p: GeoPoint, from: Int): Int {
        var best = from
        var bestDistance = Double.POSITIVE_INFINITY
        for (i in from until path.size) {
            val distance = meters(path[i], p)
            if (distance < bestDistance) {
                bestDistance = distance
                best = i
            }
        }
        return best
    }
}
