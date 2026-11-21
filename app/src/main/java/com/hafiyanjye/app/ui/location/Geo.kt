package com.hafiyanjye.app.ui.location

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** A spot on the map, in degrees. */
@Serializable
data class GeoPoint(val lat: Double, val lng: Double)

/** How a place was chosen; the engine and web store the same words. */
@Serializable
enum class LocationSource {
    @SerialName("gps") Gps,
    @SerialName("places") Places,
    @SerialName("typed") Typed,
    @SerialName("pin") Pin,
}

/** A place someone chose for a form: where, what they called it, and how it was found. */
@Serializable
data class PickedLocation(
    val lat: Double,
    val lng: Double,
    val label: String? = null,
    val source: LocationSource = LocationSource.Pin,
    /** The typed place was not found on any map: the coordinates are a city-centre guess. */
    val approximate: Boolean = false,
) {
    val point: GeoPoint get() = GeoPoint(lat, lng)
}

/** Where maps start when nothing else is known. */
object Kigali {
    /** City centre (roughly Nyarugenge / CBD), the same default as the web. */
    val Center: GeoPoint = GeoPoint(-1.9441, 30.0619)
}

private const val EarthRadiusKm = 6371.0

/** Straight-line distance in kilometres (haversine). */
fun distanceKm(a: GeoPoint, b: GeoPoint): Double {
    val dLat = Math.toRadians(b.lat - a.lat)
    val dLng = Math.toRadians(b.lng - a.lng)
    val s = sin(dLat / 2).pow(2) +
        cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLng / 2).pow(2)
    return 2 * EarthRadiusKm * asin(min(1.0, sqrt(s)))
}

/** What a marker stands for; each kind has its own icon on the map. */
enum class MarkerKind { Pin, Me, Person, Car, Home, Destination }

/**
 * A square marker on [HafiMap]. `title` is read by screen readers and says what
 * the colour means; `selected` fills it green, `muted` greys it out (someone who
 * said not now), like the web's accent and muted tones.
 */
data class MapMarker(
    val id: String,
    val position: GeoPoint,
    val kind: MarkerKind = MarkerKind.Pin,
    val title: String? = null,
    /** Degrees clockwise from north; adds an arrow pointing the way it is going. */
    val heading: Float? = null,
    val selected: Boolean = false,
    val muted: Boolean = false,
)

/** A route on the map: solid for a road route, dashed for a straight line drawn when no route is known. */
data class MapLine(val points: List<GeoPoint>, val dashed: Boolean = false)

/** A shaded area, such as the radius a request reaches. */
data class MapCircle(val center: GeoPoint, val radiusKm: Double)

/** The road route when there is one, otherwise the straight dashed line the web falls back to. */
fun routeLine(from: GeoPoint, to: GeoPoint, road: List<GeoPoint>?): MapLine =
    if (road != null && road.size >= 2) MapLine(road) else MapLine(listOf(from, to), dashed = true)

/** Zoom that fits a circle of this radius comfortably in a card-sized map (web: `zoomFor`). */
fun zoomForRadius(radiusKm: Double?): Float {
    if (radiusKm == null || radiusKm <= 0.0) return 15f
    return Math.round(14.5 - Math.log(radiusKm) / Math.log(2.0)).toFloat().coerceIn(10f, 16f)
}
