package com.hafiyanjye.app.browse

import com.hafiyanjye.app.data.api.ApiClient
import com.hafiyanjye.app.data.api.HafiJson
import com.hafiyanjye.app.data.browse.Availability
import com.hafiyanjye.app.data.browse.BrowseApi
import com.hafiyanjye.app.data.browse.BrowseFilters
import com.hafiyanjye.app.data.browse.DirectHireInput
import com.hafiyanjye.app.data.browse.FilterKey
import com.hafiyanjye.app.data.browse.MinLevel
import com.hafiyanjye.app.data.browse.PublicReview
import com.hafiyanjye.app.data.browse.PublicWorker
import com.hafiyanjye.app.data.browse.SearchSort
import com.hafiyanjye.app.data.browse.WorkerSearch
import com.hafiyanjye.app.data.match.CredentialStatus
import com.hafiyanjye.app.data.match.DocumentKind
import com.hafiyanjye.app.data.match.PriceUnit
import com.hafiyanjye.app.data.match.WorkerLevel
import com.hafiyanjye.app.ui.location.LocationSource
import com.hafiyanjye.app.ui.location.PickedLocation
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** Decoding real engine answers, the search query, and the direct hire request. */
class BrowseTest {

    @Test
    fun `search page decodes a real engine answer`() {
        val page = HafiJson.decodeFromString(WorkerSearch.serializer(), BrowseFixtures.Search)
        assertEquals(13, page.total)
        assertEquals(SearchSort.Distance, page.sort)
        assertTrue(page.located)
        assertEquals("electrician", page.category?.slug)
        assertEquals(13, page.categoryCounts["electrician"])
        assertEquals(43, page.allCount)
        val first = page.items.first()
        assertEquals(Availability.Online, first.availability)
        assertEquals(WorkerLevel.New, first.level)
        assertEquals(PriceUnit.Job, first.priceUnit)
        assertEquals(0.5, first.distanceKm ?: 0.0, 0.0)
        assertEquals(true, first.coversYou)
        assertEquals(listOf("rw", "en", "fr", "sw"), first.languages)
        assertEquals(WorkerLevel.Silver, page.items[1].level)
    }

    @Test
    fun `profile decodes credentials and reviews decode dates`() {
        val profile = HafiJson.decodeFromString(PublicWorker.serializer(), BrowseFixtures.Profile)
        assertEquals("Jean Bosco Habimana", profile.displayName)
        assertEquals(WorkerLevel.Gold, profile.level)
        assertEquals(32, profile.replyMedianSec)
        assertEquals(2, profile.credentials.size)
        assertEquals(DocumentKind.Cv, profile.credentials[0].kind)
        assertEquals(CredentialStatus.Checked, profile.credentials[1].status)
        assertEquals("IPRC Kigali", profile.credentials[1].issuer)
        val reviews = HafiJson.decodeFromString(
            ListSerializer(PublicReview.serializer()),
            BrowseFixtures.Reviews,
        )
        assertEquals(2, reviews.size)
        assertEquals("Patrick Rukundo", reviews[0].reviewerName)
        assertEquals(Instant.parse("2026-11-03T20:32:26.754Z"), reviews[0].createdAt)
        assertNull(reviews[1].reviewerName)
    }

    @Test
    fun `default search leaves defaults out like the web`() {
        val query = BrowseFilters(q = "  plumber  ", km = 5).toQuery(location = null)
        assertEquals("plumber", query["q"])
        assertNull(query["lat"])
        assertNull(query["sort"])
        assertNull(query["maxKm"])
        assertNull(query["onlineOnly"])
        assertNull(query["checkedOnly"])
        assertNull(query["offset"])
        assertEquals(24, query["limit"])
    }

    @Test
    fun `located search sends every filter with engine names`() {
        val filters = BrowseFilters(
            service = "electrician",
            sort = SearchSort.Price,
            km = 10,
            online = true,
            rating = 4.5,
            price = 20_000,
            level = MinLevel.Silver,
            checked = true,
            language = "rw",
        )
        val query = filters.toQuery(PickedLocation(-1.95, 30.06), offset = 24)
        assertEquals("electrician", query["category"])
        assertEquals("price", query["sort"])
        assertEquals(-1.95, query["lat"])
        assertEquals(30.06, query["lng"])
        assertEquals(10, query["maxKm"])
        assertEquals(true, query["onlineOnly"])
        assertEquals(4.5, query["minRating"])
        assertEquals(20_000, query["maxPriceRwf"])
        assertEquals("silver", query["minLevel"])
        assertEquals(true, query["checkedOnly"])
        assertEquals("rw", query["language"])
        assertEquals(24, query["offset"])
    }

    @Test
    fun `active filters count, chips and clearing keep service search and sort`() {
        val filters = BrowseFilters(
            service = "plumber",
            q = "leak",
            sort = SearchSort.Jobs,
            km = 5,
            online = true,
            price = 10_000,
            language = "fr",
        )
        assertEquals(4, filters.activeCount)
        val labels = filters.chips(located = true).map { it.label }
        assertEquals(listOf("Within 5 km", "Available now", "Up to 10,000 RWF", "Speaks French"), labels)
        assertFalse(filters.chips(located = false).any { it.key == FilterKey.Distance })
        assertEquals(BrowseFilters(service = "plumber", q = "leak", sort = SearchSort.Jobs), filters.cleared())
        assertFalse(filters.without(FilterKey.Online).online)
    }

    @Test
    fun `search query reaches the engine path`() = runTest {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(json("""{"ok":true,"data":${BrowseFixtures.Search},"meta":{}}"""))
            val api = BrowseApi(ApiClient(server.url("/").toString(), OkHttpClient(), { null }))
            val page = api.search(BrowseFilters(online = true, level = MinLevel.Gold).toQuery(null))
            assertEquals(2, page.items.size)
            val sent = server.takeRequest()
            assertEquals("/api/v1/providers/search", sent.url.encodedPath)
            assertEquals("true", sent.url.queryParameter("onlineOnly"))
            assertEquals("gold", sent.url.queryParameter("minLevel"))
            assertNull(sent.url.queryParameter("lat"))
        }
    }

    @Test
    fun `direct hire posts the job seeker id as providerId`() = runTest {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(json("""{"ok":true,"data":{"jobId":42,"sessionId":9},"meta":{}}"""))
            val api = BrowseApi(ApiClient(server.url("/").toString(), OkHttpClient(), { "test-token" }))
            val created = api.hire(
                DirectHireInput(7, "electrician", "Repair two sockets", -1.95, 30.06, "Kimironko", LocationSource.Gps),
            )
            assertEquals(42, created.jobId)
            val sent = server.takeRequest()
            assertEquals("POST", sent.method)
            assertEquals("/api/v1/jobs/direct", sent.url.encodedPath)
            val body = HafiJson.parseToJsonElement(sent.body?.utf8().orEmpty()).jsonObject
            assertEquals("7", body["providerId"]?.jsonPrimitive?.content)
            assertEquals("gps", body["locationSource"]?.jsonPrimitive?.content)
            assertNull(body["jobSeekerId"])
        }
    }

    private fun json(body: String) =
        MockResponse.Builder().addHeader("Content-Type", "application/json").body(body).build()
}
