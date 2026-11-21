package com.hafiyanjye.app.ui.location

import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/*
 * Privacy: before a match nobody's exact pin is shown. People are drawn around
 * the centre at roughly their distance, at a bearing that means nothing.
 */

private const val KmPerDegLat = 110.574

/** A point `distanceKm` away from `center` in direction `bearingDeg` (0 = north). */
fun placeAround(center: GeoPoint, distanceKm: Double, bearingDeg: Double): GeoPoint {
    val rad = Math.toRadians(bearingDeg)
    val kmPerDegLng = 111.32 * cos(Math.toRadians(center.lat))
    return GeoPoint(
        lat = center.lat + distanceKm * cos(rad) / KmPerDegLat,
        lng = center.lng + distanceKm * sin(rad) / kmPerDegLng,
    )
}

/** Deterministic 0–1 noise for (seed, index), so markers do not jump between redraws. */
fun scatterNoise(seed: Int, index: Int): Double {
    var h = seed * 374761393 + index * 668265263
    h = (h xor (h ushr 13)) * 1274126177
    return (h xor (h ushr 16)).toUInt().toDouble() / 4294967296.0
}

/** Spreads `count` people evenly round the circle with some jitter, so they never bunch on one side. */
fun bearingFor(seed: Int, index: Int, count: Int): Double {
    val slice = 360.0 / max(count, 1)
    val jitter = (scatterNoise(seed, index) - 0.5) * slice * 0.8
    val offset = scatterNoise(seed, 9999) * 360
    return (offset + index * slice + jitter).mod(360.0)
}

/** Someone to place: `distanceKm` is their known distance from the centre, or null to spread them. */
data class ScatterItem(val id: String, val distanceKm: Double? = null)

/** Where a scattered person is drawn: near their distance, never their real spot. */
data class ScatteredPoint(val id: String, val position: GeoPoint)

/**
 * Puts people around `center` at roughly their distance, never at a real spot:
 * known distances are kept between 30 % and 95 % of the radius (nobody sits on
 * the pin), unknown ones spread between 35 % and 90 %. Stable for a given seed.
 */
fun scatterPeople(seed: Int, center: GeoPoint, radiusKm: Double, items: List<ScatterItem>): List<ScatteredPoint> =
    items.mapIndexed { i, item ->
        val distance = item.distanceKm?.coerceIn(radiusKm * 0.3, radiusKm * 0.95)
            ?: (radiusKm * (0.35 + scatterNoise(seed, i) * 0.55))
        ScatteredPoint(item.id, placeAround(center, distance, bearingFor(seed, i, items.size)))
    }
