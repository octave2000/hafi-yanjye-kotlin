package com.hafiyanjye.app.data.location

import com.hafiyanjye.app.ui.location.GeoPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/** A road route to draw on [com.hafiyanjye.app.ui.location.HafiMap] and to quote distance and time from. */
data class Route(val points: List<GeoPoint>, val distanceMeters: Int, val durationSeconds: Int)

/**
 * Road routes from Google's Routes API. Every failure (no key, API off, no
 * network, no road) is a null, and the caller draws a straight dashed line.
 */
class Routing(
    http: OkHttpClient,
    private val key: String = MapsKey.value,
    private val endpoint: String = "https://routes.googleapis.com/directions/v2:computeRoutes",
) {
    private val http = http.newBuilder().callTimeout(10, TimeUnit.SECONDS).build()

    /** The road route, trying motorbike first and then car; null when no route service answers. */
    suspend fun route(from: GeoPoint, to: GeoPoint): Route? {
        if (key.isBlank() || !from.valid() || !to.valid()) return null
        return withContext(Dispatchers.IO) {
            // Motos are how most people move in Kigali; two-wheeler routes are not offered everywhere.
            TravelModes.firstNotNullOfOrNull { mode ->
                runCatching { fetch(from, to, mode) }.getOrNull()
            }
        }
    }

    private fun fetch(from: GeoPoint, to: GeoPoint, mode: String): Route? {
        val body = buildJsonObject {
            putJsonObject("origin") { waypoint(from) }
            putJsonObject("destination") { waypoint(to) }
            put("travelMode", mode)
        }.toString().toRequestBody("application/json".toMediaType())
        val request = MapsKey.sign(Request.Builder().url(endpoint), key)
            .header("X-Goog-FieldMask", "routes.distanceMeters,routes.duration,routes.polyline.encodedPolyline")
            .post(body)
            .build()
        return http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) null else parseRoute(response.body.string())
        }
    }

    private fun JsonObjectBuilder.waypoint(p: GeoPoint) {
        putJsonObject("location") {
            putJsonObject("latLng") {
                put("latitude", p.lat)
                put("longitude", p.lng)
            }
        }
    }

    companion object {
        private val TravelModes = listOf("TWO_WHEELER", "DRIVE")

        /** Reads a `computeRoutes` answer; null when it holds no usable route. */
        fun parseRoute(json: String): Route? = runCatching {
            val routes = Json.parseToJsonElement(json).jsonObject["routes"]?.jsonArray
            val route = routes?.firstOrNull()?.jsonObject ?: return null
            val encoded = (route["polyline"] as? JsonObject)?.get("encodedPolyline")?.jsonPrimitive?.contentOrNull
                ?: return null
            val points = decodePolyline(encoded)
            if (points.size < 2 || points.any { !it.valid() }) return null
            val distance = route["distanceMeters"]?.jsonPrimitive?.intOrNull ?: return null
            // The API writes durations as "431s".
            val duration = route["duration"]?.jsonPrimitive?.contentOrNull
                ?.removeSuffix("s")
                ?.toDoubleOrNull()
                ?: return null
            if (distance < 0 || !duration.isFinite() || duration < 0 || duration > Int.MAX_VALUE) return null
            Route(
                points = points,
                distanceMeters = distance,
                durationSeconds = duration.roundToInt(),
            )
        }.getOrNull()
    }
}

private fun GeoPoint.valid(): Boolean = lat in -90.0..90.0 && lng in -180.0..180.0

/** Google's encoded polyline format (precision 5). Stops quietly at a truncated tail. */
fun decodePolyline(encoded: String): List<GeoPoint> {
    val points = ArrayList<GeoPoint>()
    var index = 0
    var lat = 0
    var lng = 0
    while (index < encoded.length) {
        val dLat = readVarint(encoded, index) ?: break
        index = dLat.second
        val dLng = readVarint(encoded, index) ?: break
        index = dLng.second
        lat += dLat.first
        lng += dLng.first
        points.add(GeoPoint(lat / 1e5, lng / 1e5))
    }
    return points
}

/** One signed value starting at `start`: (value, next index), or null when the text ends mid-value. */
private fun readVarint(text: String, start: Int): Pair<Int, Int>? {
    var index = start
    var shift = 0
    var result = 0
    while (true) {
        if (index >= text.length) return null
        val chunk = text[index++].code - 63
        if (chunk !in 0..63 || shift >= 30) return null
        result = result or ((chunk and 0x1f) shl shift)
        shift += 5
        if (chunk < 0x20) break
    }
    val value = if (result and 1 != 0) (result shr 1).inv() else result shr 1
    return value to index
}
