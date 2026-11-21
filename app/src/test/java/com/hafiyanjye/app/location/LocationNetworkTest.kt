package com.hafiyanjye.app.location

import com.hafiyanjye.app.data.location.Geocoding
import com.hafiyanjye.app.data.location.PlaceSuggestion
import com.hafiyanjye.app.data.location.Routing
import com.hafiyanjye.app.ui.location.GeoPoint
import com.hafiyanjye.app.ui.location.Kigali
import com.hafiyanjye.app.ui.location.LocationSource
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** The requests sent to Nominatim, Google Places and Google Routes, and how answers and failures are read. */
class LocationNetworkTest {
    private val server = MockWebServer()
    private val http = OkHttpClient()

    @Before
    fun setUp() = server.start()

    @After
    fun tearDown() = server.close()

    private fun answer(code: Int, body: String) {
        val response = MockResponse.Builder()
            .code(code)
            .addHeader("Content-Type", "application/json")
            .body(body)
            .build()
        server.enqueue(response)
    }

    private fun geocoding(key: String = "") = Geocoding(
        http = http,
        key = key,
        placesBase = server.url("/v1").toString(),
        nominatimUrl = server.url("/search").toString(),
    )

    @Test
    fun typedPlace_asksNominatimOnce_inRwanda_namingTheApp() = runTest {
        answer(200, Fixtures.Nominatim)
        val found = geocoding().findTypedPlace("  Kimironko   market ")
        assertEquals(1, server.requestCount)
        val request = server.takeRequest()
        val url = request.url
        assertEquals("/search", url.encodedPath)
        assertEquals("Kimironko market", url.queryParameter("q"))
        assertEquals("rw", url.queryParameter("countrycodes"))
        assertEquals("jsonv2", url.queryParameter("format"))
        assertEquals("1", url.queryParameter("limit"))
        val agent = request.headers["User-Agent"].orEmpty()
        assertTrue(agent.startsWith("HafiYanjye-Android/"))
        assertFalse(agent.contains("@"))
        assertEquals(LocationSource.Typed, found.source)
        assertFalse(found.approximate)
        assertEquals("Kimironko market", found.label)
        assertEquals(-1.9497712, found.lat, 1e-9)
    }

    @Test
    fun typedPlace_notOnTheMap_fallsBackToANamedArea_thenToApproximateKigali() = runTest {
        answer(200, Fixtures.NominatimEmpty)
        val area = geocoding().findTypedPlace("behind the church in Remera")
        assertFalse(area.approximate)
        assertEquals(GeoPoint(-1.9578, 30.1127), area.point)

        answer(503, "busy")
        val guess = geocoding().findTypedPlace("Mama Aline's shop")
        assertTrue(guess.approximate)
        assertEquals(Kigali.Center, guess.point)
        assertEquals("Mama Aline's shop", guess.label)
    }

    @Test
    fun typedPlace_neverUsesGoogle_evenWithAKey() = runTest {
        answer(200, Fixtures.Nominatim)
        geocoding(key = "test-key").findTypedPlace("Kimironko market")
        assertEquals(1, server.requestCount)
        assertEquals("/search", server.takeRequest().url.encodedPath)
    }

    @Test
    fun suggestions_withoutKey_areNotAsked() = runTest {
        assertNull(geocoding().suggest("Kimironko", "token"))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun suggestions_sendRwandaOnly_withTheSessionAndKey_andReadARealAnswer() = runTest {
        answer(200, Fixtures.Autocomplete)
        val list = geocoding(key = "test-key").suggest("Kimironko mar", "session-1")
        assertEquals(2, list?.size)
        assertEquals("Kimironko Market", list?.first()?.main)
        assertEquals("KG 145 Street, Kigali, Rwanda", list?.get(1)?.secondary)
        val request = server.takeRequest()
        assertEquals("/v1/places:autocomplete", request.url.encodedPath)
        assertEquals("test-key", request.headers["X-Goog-Api-Key"])
        assertEquals("com.hafiyanjye.app", request.headers["X-Android-Package"]?.removeSuffix(".debug"))
        val body = Json.parseToJsonElement(request.body?.utf8().orEmpty()).jsonObject
        assertEquals("session-1", body["sessionToken"]?.jsonPrimitive?.content)
        assertEquals("""["rw"]""", body["includedRegionCodes"].toString())
    }

    @Test
    fun suggestions_whenGoogleRefuses_areNull_soThePickerStopsAsking() = runTest {
        answer(403, """{"error":{"code":403,"status":"PERMISSION_DENIED"}}""")
        assertNull(geocoding(key = "test-key").suggest("Kimironko", "token"))
    }

    @Test
    fun placeDetails_readARealAnswer_andFallBackToTheAreaWhenGoogleFails() = runTest {
        val suggestion = PlaceSuggestion("ChIJqcGXxg-n3BkRxuHf9tGbnhA", "Kimironko Market", "Kigali, Rwanda")
        answer(200, Fixtures.Place)
        val place = geocoding(key = "test-key").place(suggestion, "session-1")
        assertEquals(LocationSource.Places, place.source)
        assertEquals("Kimironko Market", place.label)
        assertEquals(-1.9498217, place.lat, 1e-9)
        val request = server.takeRequest()
        assertEquals("/v1/places/ChIJqcGXxg-n3BkRxuHf9tGbnhA", request.url.encodedPath)
        assertEquals("session-1", request.url.queryParameter("sessionToken"))
        assertEquals("displayName,location", request.headers["X-Goog-FieldMask"])

        answer(500, "oops")
        val fallback = geocoding(key = "test-key").place(suggestion, "session-2")
        assertEquals(LocationSource.Typed, fallback.source)
        assertEquals(GeoPoint(-1.9497, 30.126), fallback.point)
    }

    @Test
    fun routing_triesMotorbikeThenCar_andReturnsNullWhenNeitherAnswers() = runTest {
        val routing = Routing(http, key = "test-key", endpoint = server.url("/route").toString())
        answer(400, """{"error":{"code":400,"status":"INVALID_ARGUMENT"}}""")
        answer(200, Fixtures.Route)
        val route = routing.route(Kigali.Center, GeoPoint(-1.9497, 30.126))
        assertEquals(10220, route?.distanceMeters)
        val first = server.takeRequest()
        val body = Json.parseToJsonElement(first.body?.utf8().orEmpty()).jsonObject
        assertEquals("TWO_WHEELER", body["travelMode"]?.jsonPrimitive?.content)
        val origin = body["origin"]?.jsonObject?.get("location")?.jsonObject?.get("latLng")?.jsonObject
        assertEquals(Kigali.Center.lat, origin?.get("latitude")?.jsonPrimitive?.double ?: 0.0, 1e-9)
        assertTrue(first.headers["X-Goog-FieldMask"].orEmpty().contains("routes.polyline.encodedPolyline"))
        val second = Json.parseToJsonElement(server.takeRequest().body?.utf8().orEmpty()).jsonObject
        assertEquals("DRIVE", second["travelMode"]?.jsonPrimitive?.content)

        answer(500, "down")
        answer(500, "down")
        assertNull(routing.route(Kigali.Center, GeoPoint(-1.9497, 30.126)))
        assertNull(Routing(http, key = "").route(Kigali.Center, GeoPoint(-1.9497, 30.126)))
    }
}
