package com.hafiyanjye.app.listings

import com.hafiyanjye.app.data.api.ApiClient
import com.hafiyanjye.app.data.api.ApiException
import com.hafiyanjye.app.data.api.HafiJson
import com.hafiyanjye.app.data.listings.ListingStatusChange
import com.hafiyanjye.app.data.listings.ListingsApi
import com.hafiyanjye.app.data.rentals.CarBody
import com.hafiyanjye.app.data.rentals.ListingDto
import com.hafiyanjye.app.data.rentals.ListingStatus
import com.hafiyanjye.app.data.rentals.PricePeriod
import com.hafiyanjye.app.ui.listings.ListingDraft
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class ListingsApiTest {
    private val server = MockWebServer()
    private lateinit var api: ListingsApi

    @Before
    fun setUp() {
        server.start()
        api = ListingsApi(ApiClient(server.url("/").toString(), OkHttpClient(), { "owner-token" }))
    }

    @After
    fun tearDown() = server.close()

    private fun enqueue(code: Int, body: String) {
        server.enqueue(
            MockResponse.Builder()
                .code(code)
                .addHeader("Content-Type", "application/json")
                .body(body)
                .build(),
        )
    }

    private fun sentJson(): JsonObject {
        val body = server.takeRequest().body?.utf8().orEmpty()
        return Json.parseToJsonElement(body).jsonObject
    }

    @Test
    fun `my listings decode the real answer with typed status and the exact pin`() = runTest {
        enqueue(200, ListingsFixtures.MINE)
        val listing = api.mine().single()
        assertEquals(99, listing.id)
        assertEquals(ListingStatus.Paused, listing.status)
        assertEquals(PricePeriod.Day, listing.pricePeriod)
        assertEquals(CarBody.Suv, listing.attributes.car?.body)
        assertEquals(-1.9536, listing.exact?.lat ?: 0.0, 0.0)
        assertTrue(listing.mine)

        val sent = server.takeRequest()
        assertEquals("GET", sent.method)
        assertEquals("/api/v1/listings/mine", sent.url.encodedPath)
        assertEquals("Bearer owner-token", sent.headers["Authorization"])
    }

    @Test
    fun `a new listing is sent without an id and without an empty title`() = runTest {
        enqueue(200, ListingsFixtures.ONE)
        val draft = ListingDraft.from(savedListing()).copy(title = "  ")
        api.save(requireNotNull(draft.toInput(id = null, thisYear = 2026)))

        val body = sentJson()
        assertFalse("id" in body)
        assertFalse("title" in body)
        assertFalse("home" in body)
        assertEquals("car", body.getValue("kind").jsonPrimitive.content)
        assertEquals("day", body.getValue("pricePeriod").jsonPrimitive.content)
        assertEquals("Remera, Kigali", body.getValue("addressLabel").jsonPrimitive.content)
        assertEquals("suv", body.getValue("car").jsonObject.getValue("body").jsonPrimitive.content)
    }

    @Test
    fun `an edit loads by id and saves with its id`() = runTest {
        enqueue(200, ListingsFixtures.ONE)
        val listing = api.listing(99)
        assertEquals("/api/v1/listings/99", server.takeRequest().url.encodedPath)
        enqueue(200, ListingsFixtures.ONE)
        val saved = api.save(requireNotNull(ListingDraft.from(listing).toInput(id = 99, thisYear = 2026)))
        assertEquals(99, saved.id)

        val sent = server.takeRequest()
        assertEquals("POST", sent.method)
        assertEquals("/api/v1/listings/save", sent.url.encodedPath)
        val body = Json.parseToJsonElement(sent.body?.utf8().orEmpty()).jsonObject
        assertEquals("99", body.getValue("id").jsonPrimitive.content)
        assertEquals("Toyota RAV4 2019, self drive", body.getValue("title").jsonPrimitive.content)
        assertEquals("48000", body.getValue("priceRwf").jsonPrimitive.content)
    }

    @Test
    fun `the engine's refusal reaches the form as its own sentence`() = runTest {
        enqueue(400, ListingsFixtures.SAVE_REFUSED)
        try {
            api.save(requireNotNull(ListingDraft.from(savedListing()).toInput(id = null, thisYear = 2026)))
            fail("expected the engine's refusal")
        } catch (refused: ApiException) {
            assertEquals("Price must be between 1,000 and 50,000,000 RWF", refused.message)
        }
    }

    @Test
    fun `status changes post the engine's words`() = runTest {
        for ((change, word) in listOf(
            ListingStatusChange.Paused to "paused",
            ListingStatusChange.Active to "active",
            ListingStatusChange.Deleted to "deleted",
        )) {
            enqueue(200, ListingsFixtures.STATUS_OK)
            api.setStatus(17, change)
            val sent = server.takeRequest()
            assertEquals("POST", sent.method)
            assertEquals("/api/v1/listings/status", sent.url.encodedPath)
            val body = Json.parseToJsonElement(sent.body?.utf8().orEmpty()).jsonObject
            assertEquals("17", body.getValue("id").jsonPrimitive.content)
            assertEquals(word, body.getValue("status").jsonPrimitive.content)
        }
    }

    @Test
    fun `a photo goes up raw as a listing photo and reports its progress to the end`() = runTest {
        enqueue(201, ListingsFixtures.UPLOADED)
        val progress = mutableListOf<Float>()
        val jpeg = ByteArray(20_000) { it.toByte() }
        val stored = api.uploadPhoto(jpeg, 1600, 1200) { progress += it }
        assertEquals("/api/media/c2a3019d-602a-4af6-9273-c18f1af58018", stored.url)

        val sent = server.takeRequest()
        assertEquals("/api/media", sent.url.encodedPath)
        assertEquals("listing", sent.url.queryParameter("kind"))
        assertEquals("1600", sent.url.queryParameter("w"))
        assertEquals("1200", sent.url.queryParameter("h"))
        assertTrue(sent.headers["Content-Type"].orEmpty().startsWith("image/jpeg"))
        assertEquals(20_000L, sent.body?.size?.toLong())
        assertEquals(1f, progress.last(), 0f)
    }

    @Test
    fun `someone else's paused listing is a 404 the form treats as not found`() = runTest {
        enqueue(404, ListingsFixtures.NOT_AVAILABLE)
        try {
            api.listing(99)
            fail("expected not found")
        } catch (missing: ApiException) {
            assertEquals(404, missing.status)
        }
    }

    private fun savedListing(): ListingDto = HafiJson.decodeFromString(ListingsFixtures.LISTING)
}
