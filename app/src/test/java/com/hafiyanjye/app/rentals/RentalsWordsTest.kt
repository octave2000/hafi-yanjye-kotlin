package com.hafiyanjye.app.rentals

import com.hafiyanjye.app.data.rentals.CarAttributes
import com.hafiyanjye.app.data.rentals.CarBody
import com.hafiyanjye.app.data.rentals.FuelType
import com.hafiyanjye.app.data.rentals.HomeAttributes
import com.hafiyanjye.app.data.rentals.HomeType
import com.hafiyanjye.app.data.rentals.ListingAttributes
import com.hafiyanjye.app.data.rentals.ListingFilters
import com.hafiyanjye.app.data.rentals.ListingKind
import com.hafiyanjye.app.data.rentals.Transmission
import com.hafiyanjye.app.ui.rentals.FilterKey
import com.hafiyanjye.app.ui.rentals.RentalPlace
import com.hafiyanjye.app.ui.rentals.cleaned
import com.hafiyanjye.app.ui.rentals.detailLine
import com.hafiyanjye.app.ui.rentals.filterChips
import com.hafiyanjye.app.ui.rentals.kicker
import com.hafiyanjye.app.ui.rentals.listingFacts
import com.hafiyanjye.app.ui.rentals.rentalStart
import com.hafiyanjye.app.ui.rentals.without
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RentalsWordsTest {
    private val suv = ListingAttributes(
        car = CarAttributes("Hyundai", "Tucson", 2019, 5, CarBody.Suv, Transmission.Automatic, FuelType.Petrol, false),
    )
    private val studio = ListingAttributes(home = HomeAttributes(HomeType.Apartment, 0, 1, false))
    private val room = ListingAttributes(home = HomeAttributes(HomeType.Room, 1, 1, true))

    @Test
    fun `detail lines read like the web`() {
        assertEquals("5 seats · Automatic · Self drive", detailLine(suv))
        assertEquals("Studio · 1 bath · Unfurnished", detailLine(studio))
        assertEquals("Room · 1 bath · Furnished", detailLine(room))
        assertEquals("SUV · 2019", kicker(suv))
        assertEquals("Apartment", kicker(studio))
    }

    @Test
    fun `facts use the web's labels`() {
        val car = listingFacts(suv).associate { it.label to it.value }
        assertEquals("You drive", car["Driver"])
        assertEquals("SUV", car["Type"])
        assertEquals("1 room", listingFacts(room).first { it.label == "Bedrooms" }.value)
        assertEquals("Studio", listingFacts(studio).first { it.label == "Bedrooms" }.value)
    }

    @Test
    fun `chips follow the web's order and only count the current kind`() {
        val filters = ListingFilters(
            kind = ListingKind.Home,
            maxPriceRwf = 400_000,
            minBedrooms = 2,
            furnished = true,
            withDriver = true,
        )
        val chips = filterChips(filters)
        assertEquals(
            listOf("Under 400,000 RWF a month", "2+ bedrooms", "Furnished"),
            chips.map { it.label },
        )
        val car = ListingFilters(ListingKind.Car, maxPriceRwf = 60_000, withDriver = false, make = "Toyota")
        assertEquals(listOf("Under 60,000 RWF a day", "Toyota", "Self drive"), filterChips(car).map { it.label })
    }

    @Test
    fun `removing and cleaning filters`() {
        val filters = ListingFilters(ListingKind.Car, maxPriceRwf = 0, minSeats = 7, make = "  ", furnished = true)
        val cleaned = filters.cleaned()
        assertNull(cleaned.maxPriceRwf)
        assertNull(cleaned.make)
        assertNull(cleaned.furnished)
        assertEquals(7, cleaned.minSeats)
        assertNull(cleaned.without(FilterKey.MinSeats).minSeats)
    }

    @Test
    fun `the hand-off route is read safely`() {
        val start = rentalStart(q = " furnished room ", kind = "home", lat = "-1.95", lng = "30.1")
        assertEquals("furnished room", start.q)
        assertEquals(ListingKind.Home, start.kind)
        assertEquals(RentalPlace(-1.95, 30.1, null), start.place)
        val bad = rentalStart(q = null, kind = "boat", lat = "NaN", lng = null)
        assertEquals(ListingKind.Car, bad.kind)
        assertNull(bad.place)
    }
}
