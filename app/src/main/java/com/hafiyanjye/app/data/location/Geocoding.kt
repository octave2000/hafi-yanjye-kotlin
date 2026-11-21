package com.hafiyanjye.app.data.location

import com.hafiyanjye.app.BuildConfig
import com.hafiyanjye.app.ui.location.GeoPoint
import com.hafiyanjye.app.ui.location.Kigali
import com.hafiyanjye.app.ui.location.LocationSource
import com.hafiyanjye.app.ui.location.Neighbourhoods
import com.hafiyanjye.app.ui.location.PickedLocation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/** One line of the as-you-type list from Google place search. */
data class PlaceSuggestion(val placeId: String, val main: String, val secondary: String)

/**
 * Turns words into coordinates, the way the web does:
 *  - as the person types, Google place suggestions (Rwanda only, biased to Kigali; needs the key);
 *  - on submit ([findTypedPlace]): OpenStreetMap Nominatim (Rwanda only, biased to Kigali),
 *    then a known Kigali neighbourhood named in the text, then central Kigali flagged `approximate`.
 *
 * Nominatim's usage policy allows one search per user action (no search-as-you-type),
 * at most about one a second, and asks every app to name itself in the User-Agent.
 */
class Geocoding(
    http: OkHttpClient,
    private val key: String = MapsKey.value,
    private val placesBase: String = "https://places.googleapis.com/v1",
    private val nominatimUrl: String = "https://nominatim.openstreetmap.org/search",
) {
    // A slow lookup must not hold the form: the web gives up after 6 s too.
    private val http = http.newBuilder().callTimeout(6, TimeUnit.SECONDS).build()

    /** Whether as-you-type suggestions can be offered at all. */
    val suggests: Boolean get() = key.isNotBlank()

    /** Looks a typed place up once (call it on submit, never per keystroke). */
    suspend fun findTypedPlace(label: String): PickedLocation {
        val text = label.replace(Whitespace, " ").trim()
        searchOsm(text)?.let { return PickedLocation(it.lat, it.lng, text, LocationSource.Typed) }
        return areaGuess(text)
    }

    /**
     * As-you-type suggestions from Google. Empty when nothing matches; null when
     * Google could not be asked (no key, API off, offline), so the screen stops asking.
     */
    suspend fun suggest(query: String, sessionToken: String): List<PlaceSuggestion>? {
        val input = query.trim()
        if (key.isBlank()) return null
        if (input.length < 2) return emptyList()
        val body = buildJsonObject {
            put("input", input)
            putJsonArray("includedRegionCodes") { add(JsonPrimitive("rw")) }
            putJsonObject("locationBias") { kigaliCircle() }
            put("sessionToken", sessionToken)
        }
        val request = MapsKey.sign(Request.Builder().url("$placesBase/places:autocomplete"), key)
            .post(body.toString().toRequestBody(JsonType))
            .build()
        return call(request)?.let(::parseSuggestions)
    }

    /**
     * The chosen suggestion's coordinates. When Google names a place but will not
     * say where it is, falls back to the neighbourhood in its name, like the web.
     */
    suspend fun place(suggestion: PlaceSuggestion, sessionToken: String): PickedLocation {
        if (key.isBlank()) return areaGuess(suggestion.main)
        val url = "$placesBase/places/${suggestion.placeId}".toHttpUrl()
            .newBuilder()
            .addQueryParameter("sessionToken", sessionToken)
            .build()
        val request = MapsKey.sign(Request.Builder().url(url), key)
            .header("X-Goog-FieldMask", "displayName,location")
            .build()
        val details = call(request)?.let(::parsePlace) ?: return areaGuess(suggestion.main)
        return PickedLocation(
            lat = details.point.lat,
            lng = details.point.lng,
            label = details.name ?: suggestion.main,
            source = LocationSource.Places,
        )
    }

    private suspend fun searchOsm(text: String): GeoPoint? {
        osmGate.withLock {
            val wait = lastOsmCall + OsmGapMs - System.currentTimeMillis()
            if (wait > 0) delay(wait)
            lastOsmCall = System.currentTimeMillis()
        }
        val url = nominatimUrl.toHttpUrl()
            .newBuilder()
            .addQueryParameter("q", text)
            .addQueryParameter("format", "jsonv2")
            .addQueryParameter("countrycodes", "rw")
            .addQueryParameter("viewbox", KigaliViewbox)
            .addQueryParameter("limit", "1")
            .addQueryParameter("accept-language", "en")
            .build()
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", UserAgent)
            .build()
        return call(request)?.let(::parseNominatim)
    }

    private suspend fun call(request: Request): String? = withContext(Dispatchers.IO) {
        runCatching {
            http.newCall(request).execute().use { if (it.isSuccessful) it.body.string() else null }
        }.getOrNull()
    }

    companion object {
        private val JsonType = "application/json".toMediaType()
        private val Whitespace = Regex("\\s+")
        private const val OsmGapMs = 1_100L

        /** Names the app to Nominatim, as its policy asks; never a person's details. */
        internal val UserAgent = "HafiYanjye-Android/${BuildConfig.VERSION_NAME}"

        /** Kigali bounding box (lon/lat): biases results, does not exclude the rest of Rwanda. */
        private const val KigaliViewbox = "29.95,-1.85,30.25,-2.10"

        // Shared by every picker, so two screens cannot break the one-a-second rule together.
        private val osmGate = Mutex()
        private var lastOsmCall = 0L

        /** A known neighbourhood named in the text, or central Kigali flagged approximate. */
        fun areaGuess(text: String): PickedLocation {
            val area = Neighbourhoods.resolve(text)
            return PickedLocation(
                lat = area.point.lat,
                lng = area.point.lng,
                label = text,
                source = LocationSource.Typed,
                approximate = area.matched == null,
            )
        }

        /** First hit of a Nominatim `jsonv2` answer (it writes numbers as strings); null when there is none. */
        fun parseNominatim(json: String): GeoPoint? = runCatching {
            val hit = Json.parseToJsonElement(json).jsonArray.firstOrNull()?.jsonObject ?: return null
            val lat = hit["lat"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()
            val lng = hit["lon"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()
            if (lat != null && lng != null) validPoint(lat, lng) else null
        }.getOrNull()

        /** Reads a Places `autocomplete` answer; null when it is not one (an error page). */
        fun parseSuggestions(json: String): List<PlaceSuggestion>? = runCatching {
            val root = Json.parseToJsonElement(json).jsonObject
            root["suggestions"]?.jsonArray.orEmpty().mapNotNull { item ->
                val prediction = (item as? JsonObject)?.get("placePrediction") as? JsonObject
                    ?: return@mapNotNull null
                val id = prediction["placeId"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val format = prediction["structuredFormat"] as? JsonObject
                val main = textOf(format?.get("mainText")) ?: textOf(prediction["text"]) ?: return@mapNotNull null
                PlaceSuggestion(id, main, textOf(format?.get("secondaryText")).orEmpty())
            }
        }.getOrNull()

        /** A Places `place` answer's name and position. */
        internal fun parsePlace(json: String): PlaceDetails? = runCatching {
            val place = Json.parseToJsonElement(json).jsonObject
            val point = readLatLng(place["location"] as? JsonObject) ?: return null
            PlaceDetails(point, textOf(place["displayName"]))
        }.getOrNull()

        private fun textOf(element: JsonElement?): String? =
            (element as? JsonObject)?.get("text")?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }

        private fun readLatLng(location: JsonObject?): GeoPoint? {
            val lat = location?.get("latitude")?.jsonPrimitive?.doubleOrNull ?: return null
            val lng = location["longitude"]?.jsonPrimitive?.doubleOrNull ?: return null
            return validPoint(lat, lng)
        }

        private fun validPoint(lat: Double, lng: Double): GeoPoint? =
            if (lat in -90.0..90.0 && lng in -180.0..180.0) GeoPoint(lat, lng) else null

        private fun JsonObjectBuilder.kigaliCircle() {
            putJsonObject("circle") {
                putJsonObject("center") {
                    put("latitude", Kigali.Center.lat)
                    put("longitude", Kigali.Center.lng)
                }
                put("radius", 30_000.0)
            }
        }
    }
}

/** What the picker needs from a Places details answer. */
internal data class PlaceDetails(val point: GeoPoint, val name: String?)
