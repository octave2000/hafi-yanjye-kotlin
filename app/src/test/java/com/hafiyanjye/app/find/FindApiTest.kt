package com.hafiyanjye.app.find

import com.hafiyanjye.app.data.api.ApiClient
import com.hafiyanjye.app.data.api.ApiException
import com.hafiyanjye.app.data.api.HafiJson
import com.hafiyanjye.app.data.find.CreateRequestInput
import com.hafiyanjye.app.data.find.CreateRequestResult
import com.hafiyanjye.app.data.find.FindApi
import com.hafiyanjye.app.data.find.ListingAsk
import com.hafiyanjye.app.ui.location.LocationSource
import kotlinx.coroutines.test.runTest
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
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class FindApiTest {
    private lateinit var server: MockWebServer
    private lateinit var api: FindApi

    @Before
    fun start() {
        server = MockWebServer()
        server.start()
        api = FindApi(ApiClient(server.url("/").toString(), OkHttpClient(), { "token" }))
    }

    @After
    fun stop() {
        server.close()
    }

    private fun answer(body: String, code: Int = 200) {
        server.enqueue(
            MockResponse.Builder()
                .code(code)
                .addHeader("Content-Type", "application/json")
                .body(body)
                .build(),
        )
    }

    private val sink = CreateRequestInput(
        text = "Electrician: kitchen socket sparks when I plug in the kettle",
        lat = -1.9536,
        lng = 30.0606,
        locationSource = LocationSource.Places,
        addressLabel = "Kiyovu",
        categorySlug = "electrician",
    )

    @Test
    fun `create posts the place and service and leaves out a missing budget`() = runTest {
        answer(FindFixtures.Created)
        val result = api.create(sink)
        assertEquals(CreateRequestResult.Created(jobId = 4539, sessionId = 4536), result)
        val sent = server.takeRequest()
        assertEquals("POST", sent.method)
        assertEquals("/api/v1/jobs/create", sent.url.encodedPath)
        assertEquals("Bearer token", sent.headers["Authorization"])
        val body = HafiJson.parseToJsonElement(sent.body?.utf8().orEmpty()).jsonObject
        assertEquals("places", body["locationSource"]?.jsonPrimitive?.content)
        assertEquals("electrician", body["categorySlug"]?.jsonPrimitive?.content)
        assertEquals("Kiyovu", body["addressLabel"]?.jsonPrimitive?.content)
        assertFalse(body.containsKey("budgetRwf"))
    }

    @Test
    fun `create sends a given budget as a whole number`() = runTest {
        answer(FindFixtures.Created)
        api.create(sink.copy(budgetRwf = 15_000))
        val body = HafiJson.parseToJsonElement(server.takeRequest().body?.utf8().orEmpty()).jsonObject
        assertEquals("15000", body["budgetRwf"]?.jsonPrimitive?.content)
    }

    @Test
    fun `create reads the rental, pick and uncovered outcomes`() = runTest {
        answer(FindFixtures.Rental)
        assertEquals(CreateRequestResult.Rental, api.create(sink))

        answer(FindFixtures.PickCategory)
        val pick = api.create(sink) as CreateRequestResult.PickCategory
        assertFalse(pick.uncovered)
        assertEquals(listOf("dj", "photographer"), pick.suggestions.map { it.slug })
        assertFalse(pick.suggestions.first().listed)
        assertEquals("Umufotozi", pick.suggestions[1].nameRw)

        answer(FindFixtures.Uncovered)
        val uncovered = api.create(sink) as CreateRequestResult.PickCategory
        assertTrue(uncovered.uncovered)
        assertTrue(uncovered.suggestions.isEmpty())
        assertEquals("General help needed around the home", uncovered.summary)
    }

    @Test
    fun `create passes the engine's refusal message through`() = runTest {
        answer(FindFixtures.BudgetRefused, code = 400)
        try {
            api.create(sink.copy(budgetRwf = 50))
            fail("expected a refusal")
        } catch (error: ApiException) {
            assertEquals("Budget must be between 1,000 and 10,000,000 RWF", error.message)
        }
    }

    @Test
    fun `ask tells rentals with their kind from services`() = runTest {
        answer(FindFixtures.AskRentalHome)
        val rental = api.ask("2 bedroom furnished apartment in Kimironko under 400k") as ListingAsk.Rental
        assertEquals("home", rental.filters.kind)
        assertEquals("Kimironko", rental.place)
        val sent = server.takeRequest()
        assertEquals("/api/v1/listings/ask", sent.url.encodedPath)
        assertEquals(
            "2 bedroom furnished apartment in Kimironko under 400k",
            HafiJson.parseToJsonElement(sent.body?.utf8().orEmpty()).jsonObject["text"]?.jsonPrimitive?.content,
        )

        answer(FindFixtures.AskService)
        assertEquals(ListingAsk.Service, api.ask("fix my sink"))
    }

    @Test
    fun `nearby rounds the position and leaves out empty filters`() = runTest {
        answer(FindFixtures.NearbyPlumber)
        val nearby = api.nearby(-1.953649, 30.060612, "my sink leaks", null)
        assertEquals(2, nearby.online)
        assertEquals("plumber", nearby.category?.slug)
        assertEquals(178, nearby.typicalReplySeconds)
        val url = server.takeRequest().url
        assertEquals("/api/v1/providers/nearby", url.encodedPath)
        assertEquals("-1.954", url.queryParameter("lat"))
        assertEquals("30.061", url.queryParameter("lng"))
        assertEquals("my sink leaks", url.queryParameter("text"))
        assertNull(url.queryParameter("categorySlug"))
    }

    @Test
    fun `categories keep services that are not tiles yet`() = runTest {
        answer(FindFixtures.Categories)
        val list = api.categories()
        assertEquals(listOf(true, false), list.map { it.listed })
        assertEquals("/api/v1/categories", server.takeRequest().url.encodedPath)
    }
}
