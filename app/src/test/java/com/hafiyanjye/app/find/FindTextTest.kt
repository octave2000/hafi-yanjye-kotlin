package com.hafiyanjye.app.find

import com.hafiyanjye.app.data.find.FindCategory
import com.hafiyanjye.app.data.find.Nearby
import com.hafiyanjye.app.ui.find.budgetError
import com.hafiyanjye.app.ui.find.budgetFromInput
import com.hafiyanjye.app.ui.find.budgetFromText
import com.hafiyanjye.app.ui.find.categoryNoun
import com.hafiyanjye.app.ui.find.detailsOf
import com.hafiyanjye.app.ui.find.nearbyQuery
import com.hafiyanjye.app.ui.find.nearbySentence
import com.hafiyanjye.app.ui.find.requestText
import com.hafiyanjye.app.ui.find.searchExamples
import com.hafiyanjye.app.ui.find.tileServices
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FindTextTest {
    private val electrician = FindCategory(1, "electrician", "Electrician", icon = "Zap")
    private val hair = FindCategory(9, "hair", "Hair & beauty", icon = "Scissors")
    private val guard = FindCategory(246, "security_guard", "Security guard", icon = "Shield", listed = false)

    @Test
    fun `budgets are read only when tied to money and in range`() {
        assertEquals(18000, budgetFromText("Fix a sink, budget 18,000"))
        assertEquals(20000, budgetFromText("about 20k"))
        assertEquals(15000, budgetFromText("frw 15 000"))
        assertEquals(18000, budgetFromText("I have 18000 budget"))
        assertEquals(25000, budgetFromText("I can pay 25000"))
        assertEquals(18000, budgetFromText("18 thousand francs"))
        assertNull(budgetFromText("2 bedrooms on KG 11 Ave"))
        assertNull(budgetFromText("budget 500"))
        assertNull(budgetFromText("budget 11000000"))
    }

    @Test
    fun `budget box keeps digits and checks the range`() {
        assertEquals(15000, budgetFromInput("15,000"))
        assertNull(budgetFromInput(""))
        assertEquals(12345678, budgetFromInput("123456789"))
        assertNull(budgetError(null))
        assertEquals("The smallest budget is 1,000 RWF.", budgetError(999))
        assertEquals("That's above 10,000,000 RWF. Try a smaller amount.", budgetError(10_000_001))
        assertNull(budgetError(1_000))
    }

    @Test
    fun `service prefix is split from the details`() {
        assertEquals("sockets spark", detailsOf("Electrician: sockets spark", "Electrician"))
        assertEquals("", detailsOf("Electrician: ", "Electrician"))
        assertEquals("Electrician: x", detailsOf("Electrician: x", null))
        assertEquals("Electrician: sockets spark", requestText("electrician: sockets spark", electrician))
        assertEquals("I need an electrician", requestText("Electrician: ", electrician))
        assertEquals("I need a hair & beauty pro", requestText("", hair))
        assertEquals("fix my sink", requestText("  fix my sink ", null))
    }

    @Test
    fun `service nouns read as people`() {
        assertEquals("electricians", categoryNoun("Electrician", 2))
        assertEquals("mover", categoryNoun("Movers", 1))
        assertEquals("hair & beauty pros", categoryNoun("Hair & beauty", 3))
        assertEquals("people", categoryNoun("", 2))
    }

    @Test
    fun `nearby sentence says only what the engine counted`() {
        val plumbers = Nearby(2, 10.0, FindCategory(2, "plumber", "Plumber"), 178)
        assertEquals(
            "2 plumbers online within 10 km · usually reply in about 3 min",
            nearbySentence(plumbers, hasLocation = true),
        )
        assertEquals("1 person online in Kigali", nearbySentence(Nearby(1, 10.0), hasLocation = false))
        assertEquals(
            "No plumbers online near you right now. Post your request and we'll tell you the moment one is.",
            nearbySentence(plumbers.copy(online = 0), hasLocation = true),
        )
        assertEquals("Fix the tap", nearbyQuery("Plumber: Fix the tap"))
        assertNull(nearbyQuery("Plumber: "))
    }

    @Test
    fun `tiles and examples use listed services only`() {
        assertEquals(listOf(electrician), tileServices(listOf(electrician, guard)))
        assertEquals(listOf(electrician, guard), tileServices(listOf(electrician, guard), keep = "security_guard"))
        assertEquals(
            listOf("Electrician: sockets, wiring, lights, breakers"),
            searchExamples(listOf(electrician, guard)),
        )
    }
}
