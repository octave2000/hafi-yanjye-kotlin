package com.hafiyanjye.app.rentals

import com.hafiyanjye.app.data.api.ApiClient
import com.hafiyanjye.app.data.api.ApiException
import com.hafiyanjye.app.data.api.HafiJson
import com.hafiyanjye.app.data.rentals.CarBody
import com.hafiyanjye.app.data.rentals.FuelType
import com.hafiyanjye.app.data.rentals.HomeType
import com.hafiyanjye.app.data.rentals.ListingAskKind
import com.hafiyanjye.app.data.rentals.ListingFilters
import com.hafiyanjye.app.data.rentals.ListingKind
import com.hafiyanjye.app.data.rentals.ListingStatus
import com.hafiyanjye.app.data.rentals.PricePeriod
import com.hafiyanjye.app.data.rentals.RentalsApi
import com.hafiyanjye.app.data.rentals.Transmission
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
import java.time.Instant

class RentalsApiTest {
    private val server = MockWebServer()
    private lateinit var api: RentalsApi

    @Before
    fun start() {
        server.start()
        api = RentalsApi(ApiClient(server.url("/").toString(), OkHttpClient(), { "token" }))
    }

    @After
    fun stop() {
        server.close()
    }

    private fun reply(body: String, status: Int = 200) {
        server.enqueue(
            MockResponse.Builder()
                .code(status)
                .addHeader("Content-Type", "application/json")
                .body(body)
                .build(),
        )
    }

    @Test
    fun `search sends the engine's words, keeps false, and drops the other kind's filters`() = runTest {
        reply(RentalsFixtures.carSearch)
        val filters = ListingFilters(
            kind = ListingKind.Car,
            maxPriceRwf = 60_000,
            withDriver = false,
            transmission = Transmission.Automatic,
            body = CarBody.Suv,
            make = "  Toyota ",
            furnished = true,
        )
        api.search(filters, lat = -1.95, lng = 30.06)
        val url = server.takeRequest().url
        assertEquals("/api/v1/listings/search", url.encodedPath)
        assertEquals("car", url.queryParameter("kind"))
        assertEquals("60000", url.queryParameter("maxPriceRwf"))
        assertEquals("false", url.queryParameter("withDriver"))
        assertEquals("automatic", url.queryParameter("transmission"))
        assertEquals("suv", url.queryParameter("body"))
        assertEquals("Toyota", url.queryParameter("make"))
        assertEquals("-1.95", url.queryParameter("lat"))
        assertNull(url.queryParameter("furnished"))
        assertNull(url.queryParameter("page"))
    }

    @Test
    fun `search asks for later pages and home filters`() = runTest {
        reply(RentalsFixtures.carSearch)
        val filters = ListingFilters(ListingKind.Home, minBedrooms = 2, homeType = HomeType.Room, furnished = false)
        api.search(filters, lat = null, lng = null, page = 2)
        val url = server.takeRequest().url
        assertEquals("home", url.queryParameter("kind"))
        assertEquals("2", url.queryParameter("minBedrooms"))
        assertEquals("room", url.queryParameter("homeType"))
        assertEquals("false", url.queryParameter("furnished"))
        assertEquals("2", url.queryParameter("page"))
        assertNull(url.queryParameter("lat"))
    }

    @Test
    fun `search decodes real cards`() = runTest {
        reply(RentalsFixtures.carSearch)
        val result = api.search(ListingFilters(ListingKind.Car), null, null)
        assertEquals(12, result.total)
        assertFalse(result.hasMore)
        assertEquals(-1.9441, result.center.lat, 0.0)
        val mercedes = result.items.first()
        assertEquals(PricePeriod.Day, mercedes.pricePeriod)
        assertEquals(CarBody.Sedan, mercedes.attributes.car?.body)
        assertEquals(FuelType.Petrol, mercedes.attributes.car?.fuel)
        assertNull(mercedes.attributes.home)
        assertEquals(1.0, mercedes.distanceKm, 0.0)
    }

    @Test
    fun `listing decodes the detail with its owner`() = runTest {
        reply(RentalsFixtures.homeDetail)
        val listing = api.listing(98)
        assertEquals("/api/v1/listings/98", server.takeRequest().url.encodedPath)
        assertEquals(ListingStatus.Active, listing.status)
        assertEquals(3, listing.photos.size)
        assertEquals(HomeType.Apartment, listing.attributes.home?.homeType)
        assertEquals("+250788000104", listing.owner.phone)
        assertEquals(Instant.parse("2026-11-14T13:09:34.929Z"), listing.owner.memberSince)
        assertNull(listing.exact)
        assertFalse(listing.mine)
    }

    @Test
    fun `a missing listing is a 404 the screen can tell apart`() = runTest {
        reply(RentalsFixtures.notFound, status = 404)
        try {
            api.listing(99_999)
            fail("expected ApiException")
        } catch (error: ApiException) {
            assertEquals(404, error.status)
        }
    }

    @Test
    fun `ask posts the text and decodes a rental`() = runTest {
        reply(RentalsFixtures.askRental)
        val answer = api.ask("2 bedroom furnished near Kimironko under 400k")
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/listings/ask", request.url.encodedPath)
        val body = HafiJson.parseToJsonElement(request.body?.utf8().orEmpty()).jsonObject
        assertEquals("2 bedroom furnished near Kimironko under 400k", body["text"]?.jsonPrimitive?.content)
        assertEquals(ListingAskKind.Rental, answer.kind)
        assertEquals("Kimironko", answer.place)
        assertEquals(ListingKind.Home, answer.filters?.kind)
        assertEquals(400_000, answer.filters?.maxPriceRwf)
        assertTrue(answer.filters?.furnished == true)
    }

    @Test
    fun `ask decodes the service answer`() = runTest {
        reply(RentalsFixtures.askService)
        val answer = api.ask("need a plumber")
        assertEquals(ListingAskKind.Service, answer.kind)
        assertNull(answer.filters)
    }
}
