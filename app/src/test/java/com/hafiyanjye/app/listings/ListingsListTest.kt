package com.hafiyanjye.app.listings

import com.hafiyanjye.app.data.api.HafiJson
import com.hafiyanjye.app.data.listings.ListingStatusChange
import com.hafiyanjye.app.data.rentals.ListingDto
import com.hafiyanjye.app.data.rentals.ListingStatus
import com.hafiyanjye.app.ui.listings.applying
import com.hafiyanjye.app.ui.listings.listingsSummary
import com.hafiyanjye.app.ui.listings.noticeFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ListingsListTest {
    private val paused = HafiJson.decodeFromString<ListingDto>(ListingsFixtures.LISTING)
    private val live = paused.copy(id = 100, status = ListingStatus.Active)

    @Test
    fun `the heading counts live and paused, or invites a first listing`() {
        assertEquals("1 live · 1 paused", listingsSummary(listOf(paused, live)))
        assertEquals("Rent out a car or a home to people nearby.", listingsSummary(emptyList()))
    }

    @Test
    fun `an accepted change shows at once`() {
        val rows = listOf(paused, live)
        assertEquals(ListingStatus.Active, rows.applying(99, ListingStatusChange.Active).first().status)
        assertEquals(ListingStatus.Paused, rows.applying(100, ListingStatusChange.Paused).last().status)
        assertTrue(rows.applying(99, ListingStatusChange.Deleted).none { it.id == 99 })
    }

    @Test
    fun `the toasts use the web's words`() {
        assertEquals("Paused: people can't find it now", noticeFor(ListingStatusChange.Paused))
        assertEquals("Live again", noticeFor(ListingStatusChange.Active))
        assertEquals("Listing deleted", noticeFor(ListingStatusChange.Deleted))
    }
}
