package com.hafiyanjye.app.ui.trip

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hafiyanjye.app.R
import com.hafiyanjye.app.data.api.Role
import com.hafiyanjye.app.data.trip.Trip
import com.hafiyanjye.app.data.trip.TripRoute
import com.hafiyanjye.app.ui.kit.HafiIconButton
import com.hafiyanjye.app.ui.location.GeoPoint
import com.hafiyanjye.app.ui.location.HafiMap
import com.hafiyanjye.app.ui.location.MapCircle
import com.hafiyanjye.app.ui.location.MapLine
import com.hafiyanjye.app.ui.location.MapMarker
import com.hafiyanjye.app.ui.location.MarkerKind
import com.hafiyanjye.app.ui.location.distanceKm
import com.hafiyanjye.app.ui.location.zoomForRadius
import com.hafiyanjye.app.ui.theme.Hafi
import com.hafiyanjye.app.ui.theme.rememberReducedMotion
import kotlin.math.max
import kotlin.math.min

/** How long a marker takes to glide to a new position; positions come every ~4 s. */
private const val GlideMs = 900

/** Farther than this is a jump (a first fix, a phone that lost GPS): no glide across town. */
private const val GlideMaxKm = 2.0

/** Accuracy below this is not worth a circle (web: 15 m). */
private const val AccuracyCircleMinMeters = 15.0

/** Where the camera looks: moved only when the person leaves it, so someone zooming in is not pulled away. */
internal data class Frame(val center: GeoPoint, val radiusKm: Double)

/**
 * The trip on our own map: where the work is, the hired person gliding along
 * as positions arrive, the road still to go (or a dashed straight line when
 * no route is known), and how sure the phone is of its position.
 */
@Composable
internal fun TripMap(
    trip: Trip,
    nav: TripNav?,
    full: Boolean,
    onToggleFull: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val destination = TripText.destination(trip)
    val target = trip.position?.let { GeoPoint(it.lat, it.lng) }
    val shown = rememberGlide(target)
    val accuracyKm = trip.position?.accuracy
        ?.takeIf { it >= AccuracyCircleMinMeters }
        ?.let { min(it, TripText.MaxTrustedAccuracyMeters) / 1000 }
    val frame = remember(destination) { FrameKeeper(destination) }.update(target, accuracyKm, nav?.route)

    val markers = listOfNotNull(
        MapMarker(
            id = "destination",
            position = destination,
            kind = MarkerKind.Home,
            title = placeTitle(trip),
        ),
        shown?.let {
            MapMarker(
                id = "worker",
                position = it,
                kind = MarkerKind.Person,
                title = "${trip.worker.name}, last shared position",
                heading = trip.position?.heading?.toFloat(),
                selected = true,
            )
        },
    )
    Box(modifier) {
        HafiMap(
            modifier = Modifier.matchParentSize(),
            center = frame.center,
            zoom = zoomForRadius(frame.radiusKm),
            markers = markers,
            lines = shown?.let { tripLines(it, destination, nav) }.orEmpty(),
            circles = listOfNotNull(
                shown?.let { position -> accuracyKm?.let { MapCircle(position, it) } },
            ),
        )
        HafiIconButton(
            icon = if (full) R.drawable.ic_minimize_2 else R.drawable.ic_maximize_2,
            label = if (full) "Exit full screen" else "Full screen map",
            onClick = onToggleFull,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
                .background(Hafi.Surface)
                .border(1.dp, Hafi.LineStrong),
        )
    }
}

private fun placeTitle(trip: Trip): String {
    val label = trip.destination.label?.let { ", $it" }.orEmpty()
    return if (trip.role == Role.Hirer) {
        "Your place$label"
    } else {
        "${TripText.capitalized(TripText.hirer(trip))}'s place$label"
    }
}

/**
 * The road still to go, with dashed links from the person to the road and
 * from the road to the door (people rarely stand on the road itself); with no
 * route, one dashed straight line.
 */
private fun tripLines(position: GeoPoint, destination: GeoPoint, nav: TripNav?): List<MapLine> {
    val remaining = nav?.progress?.remainingPath
    if (remaining == null || remaining.size < 2) return listOf(MapLine(listOf(position, destination), dashed = true))
    return listOf(
        MapLine(remaining),
        MapLine(listOf(position, remaining.first()), dashed = true),
        MapLine(listOf(remaining.last(), destination), dashed = true),
    )
}

/** Moves the marker smoothly from where it is drawn to each new position; jumps when motion is reduced. */
@Composable
private fun rememberGlide(target: GeoPoint?): GeoPoint? {
    val reducedMotion = rememberReducedMotion()
    var from by remember { mutableStateOf(target) }
    var to by remember { mutableStateOf(target) }
    val fraction = remember { Animatable(1f) }
    LaunchedEffect(target) {
        if (target == null) {
            from = null
            to = null
            return@LaunchedEffect
        }
        val start = between(from, to, fraction.value) ?: target
        from = start
        to = target
        if (reducedMotion || distanceKm(start, target) > GlideMaxKm) {
            fraction.snapTo(1f)
        } else {
            fraction.snapTo(0f)
            fraction.animateTo(1f, tween(GlideMs, easing = LinearOutSlowInEasing))
        }
    }
    return between(from, to, fraction.value)
}

private fun between(from: GeoPoint?, to: GeoPoint?, fraction: Float): GeoPoint? {
    if (from == null || to == null) return to
    return GeoPoint(
        from.lat + (to.lat - from.lat) * fraction,
        from.lng + (to.lng - from.lng) * fraction,
    )
}

/**
 * Fits the work place and the person (with room for the accuracy circle) once,
 * fits a new road route once, and otherwise moves only when the person leaves
 * the frame, as the web map does. Plain fields, not state: the answer is read
 * in the same composition and must not trigger another.
 */
internal class FrameKeeper(private val destination: GeoPoint) {
    var frame: Frame = frameAround(listOf(destination), 0.0)
        private set
    private var hadPosition = false
    private var fittedRoute: TripRoute? = null

    fun update(position: GeoPoint?, accuracyKm: Double?, route: TripRoute?): Frame {
        when {
            position == null -> if (hadPosition) {
                hadPosition = false
                frame = frameAround(listOf(destination), 0.0)
            }
            route != null && fittedRoute !== route -> {
                fittedRoute = route
                hadPosition = true
                frame = frameAround(route.path + destination + position, accuracyKm ?: 0.0)
            }
            !hadPosition || distanceKm(frame.center, position) > frame.radiusKm -> {
                hadPosition = true
                frame = frameAround(listOf(destination, position), accuracyKm ?: 0.0)
            }
        }
        return frame
    }
}

private fun frameAround(points: List<GeoPoint>, paddingKm: Double): Frame {
    val south = points.minOf { it.lat }
    val north = points.maxOf { it.lat }
    val west = points.minOf { it.lng }
    val east = points.maxOf { it.lng }
    val center = GeoPoint((south + north) / 2, (west + east) / 2)
    // At least 40 m around both pins, so the map never zooms in past what GPS can tell.
    val radius = distanceKm(center, GeoPoint(north, east)) + max(MinPaddingKm, paddingKm)
    return Frame(center, radius)
}

private const val MinPaddingKm = 0.04
