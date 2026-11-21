package com.hafiyanjye.app.listings

import com.hafiyanjye.app.data.api.HafiJson
import com.hafiyanjye.app.data.rentals.HomeType
import com.hafiyanjye.app.data.rentals.ListingDto
import com.hafiyanjye.app.data.rentals.ListingKind
import com.hafiyanjye.app.data.rentals.PricePeriod
import com.hafiyanjye.app.ui.listings.CarDraft
import com.hafiyanjye.app.ui.listings.DraftField
import com.hafiyanjye.app.ui.listings.HomeDraft
import com.hafiyanjye.app.ui.listings.ListingDraft
import com.hafiyanjye.app.ui.listings.moved
import com.hafiyanjye.app.ui.listings.priceDigits
import com.hafiyanjye.app.ui.listings.withCover
import com.hafiyanjye.app.ui.location.PickedLocation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ListingDraftTest {
    private val kigali = PickedLocation(-1.9441, 30.0619, "Kigali")

    private fun home() = ListingDraft(kind = ListingKind.Home, price = "350000", location = kigali)

    private fun car() = ListingDraft(
        kind = ListingKind.Car,
        price = "45000",
        location = kigali,
        car = CarDraft(make = "Toyota", model = "RAV4", year = "2019"),
    )

    @Test
    fun `a room is always one bedroom and homes default to per month`() {
        val draft = home().copy(home = HomeDraft(homeType = HomeType.Room, bedrooms = 3))
        assertTrue(draft.problems(needsPhone = false, thisYear = 2026).isEmpty())
        val input = requireNotNull(draft.toInput(id = null, thisYear = 2026))
        assertEquals(PricePeriod.Month, input.pricePeriod)
        assertEquals(1, input.home?.bedrooms)
        assertNull(input.car)
    }

    @Test
    fun `cars are per day whatever period was chosen before`() {
        val input = requireNotNull(car().copy(homePeriod = PricePeriod.Night).toInput(id = null, thisYear = 2026))
        assertEquals(PricePeriod.Day, input.pricePeriod)
        assertNull(input.home)
    }

    @Test
    fun `nothing chosen asks for car or home first`() {
        val problems = ListingDraft().problems(needsPhone = false)
        assertEquals(DraftField.Kind, DraftField.entries.first { it in problems })
    }

    @Test
    fun `the engine's limits are checked before sending`() {
        val problems = car().copy(
            price = "50000001",
            car = CarDraft(make = "T", model = " ", year = "2028", seats = "61"),
            location = PickedLocation(0.3, 32.5),
            title = "ab",
            description = "x".repeat(2_001),
        ).problems(needsPhone = false, thisYear = 2026)
        assertEquals(
            setOf(
                DraftField.Price,
                DraftField.Make,
                DraftField.Model,
                DraftField.Year,
                DraftField.Seats,
                DraftField.Location,
                DraftField.Title,
                DraftField.Description,
            ),
            problems.keys,
        )
        assertEquals("Put the pin somewhere in Rwanda.", problems[DraftField.Location])
    }

    @Test
    fun `price needs at least 1,000 and next year's car is fine`() {
        assertTrue(DraftField.Price in car().copy(price = "999").problems(needsPhone = false, thisYear = 2026))
        val nextYear = car().copy(car = CarDraft(make = "Toyota", model = "RAV4", year = "2027"))
        assertTrue(nextYear.problems(needsPhone = false, thisYear = 2026).isEmpty())
    }

    @Test
    fun `a phone is asked only when the account has none, and Rwandan numbers pass`() {
        assertFalse(DraftField.Phone in home().problems(needsPhone = false))
        assertTrue(DraftField.Phone in home().problems(needsPhone = true))
        assertFalse(DraftField.Phone in home().copy(phone = "078 123 4567").problems(needsPhone = true))
        assertFalse(DraftField.Phone in home().copy(phone = "+250-788-123-401").problems(needsPhone = true))
        assertTrue(DraftField.Phone in home().copy(phone = "0612345678").problems(needsPhone = true))
    }

    @Test
    fun `the suggested title follows the engine's default`() {
        assertEquals("Toyota RAV4 2019", car().titlePlaceholder)
        assertEquals("Toyota RAV4 2019", ListingDraft(kind = ListingKind.Car).titlePlaceholder)
        assertEquals("2-bedroom apartment", home().titlePlaceholder)
        assertEquals("Studio house", home().copy(home = HomeDraft(HomeType.House, bedrooms = 0)).titlePlaceholder)
        assertEquals("Room", home().copy(home = HomeDraft(HomeType.Room)).titlePlaceholder)
    }

    @Test
    fun `a saved listing fills the form and saves back unchanged`() {
        val listing = HafiJson.decodeFromString<ListingDto>(ListingsFixtures.LISTING)
        val draft = ListingDraft.from(listing)
        assertEquals("48000", draft.price)
        assertEquals("Remera, Kigali", draft.location?.label)
        val input = requireNotNull(draft.toInput(id = listing.id, thisYear = 2026))
        assertEquals(listing.attributes.car, input.car)
        assertEquals(listing.photos, input.photos)
        assertEquals(listing.title, input.title)
        assertEquals(listing.exact?.lat, input.lat)
    }

    @Test
    fun `photos reorder within bounds and the cover moves first`() {
        val photos = listOf("a", "b", "c")
        assertEquals(listOf("b", "a", "c"), photos.moved(0, 1))
        assertEquals(listOf("a", "c", "b"), photos.moved(2, -1))
        assertEquals(photos, photos.moved(0, -1))
        assertEquals(listOf("c", "a", "b"), photos.withCover("c"))
    }

    @Test
    fun `price keeps digits only, eight at most`() {
        assertEquals("45000", priceDigits("45,000 RWF"))
        assertEquals("12345678", priceDigits("123456789"))
    }
}
