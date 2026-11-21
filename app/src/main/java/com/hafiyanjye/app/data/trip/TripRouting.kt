package com.hafiyanjye.app.data.trip

import com.hafiyanjye.app.data.location.MapsKey
import com.hafiyanjye.app.data.location.Routing
import com.hafiyanjye.app.ui.location.GeoPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
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

/**
 * Road routes with their turns, for the person on the way. The shared
 * [Routing] answers distance, time and the line only; the trip also needs
 * each step's instruction and maneuver for the next-turn banner, so it asks
 * Google's Routes API for those fields too and reads the line the same way.
 */
class TripRouting(
    http: OkHttpClient,
    private val key: String = MapsKey.value,
    private val endpoint: String = "https://routes.googleapis.com/directions/v2:computeRoutes",
) {
    private val http = http.newBuilder()
        .callTimeout(10, TimeUnit.SECONDS)
        .build()

    /** The route, trying [preferred] first (the mode that worked last time); null when nothing answers. */
    suspend fun route(from: GeoPoint, to: GeoPoint, preferred: String? = null): TripRoute? {
        if (key.isBlank()) return null
        val modes = if (preferred == null) Modes else listOf(preferred) + Modes.filter { it != preferred }
        return withContext(Dispatchers.IO) {
            // Two-wheeler routes are not offered everywhere: the next mode is tried then.
            modes.firstNotNullOfOrNull { mode ->
                runCatching { fetch(from, to, mode) }.getOrNull()
            }
        }
    }

    private fun fetch(from: GeoPoint, to: GeoPoint, mode: String): TripRoute? {
        val body = buildJsonObject {
            putJsonObject("origin") { waypoint(from) }
            putJsonObject("destination") { waypoint(to) }
            put("travelMode", mode)
        }.toString().toRequestBody("application/json".toMediaType())
        val request = MapsKey.sign(Request.Builder().url(endpoint), key)
            .header("X-Goog-FieldMask", FieldMask)
            .post(body)
            .build()
        return http.newCall(request).execute().use { response ->
            if (response.isSuccessful) parse(response.body.string(), mode) else null
        }
    }

    private fun JsonObjectBuilder.waypoint(point: GeoPoint) {
        putJsonObject("location") {
            putJsonObject("latLng") {
                put("latitude", point.lat)
                put("longitude", point.lng)
            }
        }
    }

    companion object {
        private val Modes = listOf("TWO_WHEELER", "DRIVE")
        private const val FieldMask = "routes.distanceMeters,routes.duration,routes.polyline.encodedPolyline," +
            "routes.legs.steps.startLocation,routes.legs.steps.navigationInstruction"

        /** Reads a `computeRoutes` answer with its steps; null when it holds no usable route. */
        fun parse(json: String, mode: String): TripRoute? {
            val line = Routing.parseRoute(json) ?: return null
            val first = runCatching {
                Json.parseToJsonElement(json).jsonObject["routes"]?.jsonArray?.firstOrNull()?.jsonObject
            }.getOrNull() ?: return null
            val steps = first["legs"]?.jsonArray.orEmpty().flatMap { leg ->
                (leg as? JsonObject)?.get("steps")?.jsonArray.orEmpty().mapNotNull { step ->
                    (step as? JsonObject)?.let(::readStep)
                }
            }
            return TripRoutes.build(line.points, line.durationSeconds.toDouble(), steps, mode)
        }

        private fun readStep(step: JsonObject): RawStep {
            val latLng = (step["startLocation"] as? JsonObject)?.get("latLng") as? JsonObject
            val lat = latLng?.get("latitude")?.jsonPrimitive?.doubleOrNull
            val lng = latLng?.get("longitude")?.jsonPrimitive?.doubleOrNull
            val instruction = step["navigationInstruction"] as? JsonObject
            return RawStep(
                instructions = instruction?.get("instructions")?.jsonPrimitive?.contentOrNull.orEmpty(),
                maneuver = instruction?.get("maneuver")?.jsonPrimitive?.contentOrNull.orEmpty(),
                start = if (lat != null && lng != null) GeoPoint(lat, lng) else null,
            )
        }
    }
}
