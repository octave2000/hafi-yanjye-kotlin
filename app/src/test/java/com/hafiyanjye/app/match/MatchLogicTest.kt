package com.hafiyanjye.app.match

import com.hafiyanjye.app.data.api.HafiJson
import com.hafiyanjye.app.data.api.SessionState
import com.hafiyanjye.app.data.match.MatchSession
import com.hafiyanjye.app.data.match.PriceUnit
import com.hafiyanjye.app.ui.location.GeoPoint
import com.hafiyanjye.app.ui.match.PersonState
import com.hafiyanjye.app.ui.match.SessionView
import com.hafiyanjye.app.ui.match.VsBudget
import com.hafiyanjye.app.ui.match.canCancel
import com.hafiyanjye.app.ui.match.canEditBudget
import com.hafiyanjye.app.ui.match.candidateLabel
import com.hafiyanjye.app.ui.match.categoryNoun
import com.hafiyanjye.app.ui.match.countdownText
import com.hafiyanjye.app.ui.match.fitRadiusKm
import com.hafiyanjye.app.ui.match.keepLookingSummary
import com.hafiyanjye.app.ui.match.parseBudget
import com.hafiyanjye.app.ui.match.peopleItems
import com.hafiyanjye.app.ui.match.pollIntervalMs
import com.hafiyanjye.app.ui.match.priceInfo
import com.hafiyanjye.app.ui.match.scatterAround
import com.hafiyanjye.app.ui.match.searchRadiusKm
import com.hafiyanjye.app.ui.match.sessionView
import com.hafiyanjye.app.ui.match.shortlistSecondsLeft
import com.hafiyanjye.app.ui.match.withArticle
import com.hafiyanjye.app.ui.location.distanceKm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class MatchLogicTest {
    private fun session(json: String): MatchSession = HafiJson.decodeFromString(MatchSession.serializer(), json)

    @Test
    fun `direct waiting and declined requests use their own views`() {
        val waiting = session(
            """{"id":8,"jobId":12,"state":"pinging",
               "direct":{"providerId":4,"displayName":"Jean Claude","picture":null,"answer":"waiting"}}""",
        )
        assertEquals(SessionView.DirectWaiting, sessionView(waiting))
        assertEquals(SessionView.DirectEnded, sessionView(waiting.copy(state = SessionState.NoProviders)))
        assertEquals(SessionView.NobodyFree, sessionView(waiting.copy(state = SessionState.NoProviders, direct = null)))
        assertEquals(4, waiting.direct?.workerId)
    }

    @Test
    fun `finished sessions stop polling and cannot be cancelled`() {
        listOf(SessionState.Completed, SessionState.Cancelled).forEach {
            assertNull(pollIntervalMs(it))
            assertFalse(canCancel(it))
            assertFalse(canEditBudget(it))
        }
        assertEquals(4_000L, pollIntervalMs(SessionState.Pinging))
        assertEquals(15_000L, pollIntervalMs(SessionState.Matched))
    }

    @Test
    fun `budget changes only while the search is open, as the engine allows`() {
        assertTrue(canEditBudget(SessionState.Pinging))
        assertTrue(canEditBudget(SessionState.Eliminating))
        // The engine answers 400 "The budget can't change after a match" here (checked on the demo engine).
        assertFalse(canEditBudget(SessionState.NoProviders))
        assertFalse(canEditBudget(SessionState.Matched))
    }

    @Test
    fun `budget validates engine limits and prices compare like units`() {
        assertNull(parseBudget("999"))
        assertEquals(1000, parseBudget("1,000"))
        assertEquals(10_000_000, parseBudget("10000000"))
        assertNull(parseBudget("10000001"))
        assertNull(priceInfo(null, 2000, null, PriceUnit.Hour, 10000)?.vsBudget)
        assertEquals("From 2,000 RWF / hour", priceInfo(null, 2000, null, PriceUnit.Hour, 10000)?.text)
        assertEquals(VsBudget.Within, priceInfo(2000, null, null, PriceUnit.Hour, 10000)?.vsBudget)
        assertEquals(VsBudget.Above, priceInfo(null, 20000, null, PriceUnit.Job, 10000)?.vsBudget)
        assertNull(priceInfo(null, null, null, PriceUnit.Job, 10000))
        assertNull(priceInfo(5000, null, null, PriceUnit.Job, null)?.vsBudget)
    }

    @Test
    fun `countdown runs to the engine deadline`() {
        val pinging = session("""{"id":8,"jobId":12,"state":"pinging","shortlistDueAt":"2026-11-17T23:31:43.464Z"}""")
        val now = Instant.parse("2026-11-17T23:30:28.464Z")
        val left = shortlistSecondsLeft(pinging, now)
        assertEquals(75L, left)
        assertEquals("1:15", countdownText(75))
        assertEquals("0:05", countdownText(5))
        assertEquals("Any moment", countdownText(0))
        assertNull(shortlistSecondsLeft(pinging.copy(shortlistDueAt = null), now))
    }

    @Test
    fun `wording follows the web`() {
        assertEquals("electricians", categoryNoun("Electrician", 2))
        assertEquals("mover", categoryNoun("Movers", 1))
        assertEquals("house cleaning pros", categoryNoun("House cleaning", 2))
        assertEquals("an electrician", withArticle("electrician"))
        assertEquals("Candidate C", candidateLabel("c3"))
        assertEquals("Nobody else has said yes yet · ask more people", keepLookingSummary(0, 0))
        assertEquals("Asking 2 more people… 1 said yes so far", keepLookingSummary(1, 2))
    }

    @Test
    fun `anonymous people come from engine counts and preserve reply distances`() {
        val pinging = session(
            """{"id":8,"jobId":12,"state":"pinging",
               "replies":[{"distanceKm":1.2,"quoteRwf":null,"secondsAfterStart":4,"level":"silver"}]}""",
        )
        val people = peopleItems(4, 1, pinging.replies)
        assertEquals(4, people.size)
        assertEquals(PersonState.Available, people[0].state)
        assertEquals(1.2, people[0].distanceKm)
        assertEquals(PersonState.Declined, people[1].state)
        assertEquals(PersonState.Pinged, people[2].state)
        assertNull(people[2].distanceKm)
        assertTrue(peopleItems(0, 0, emptyList()).isEmpty())
    }

    @Test
    fun `scattered spots are stable, inside the circle and at the reported distance`() {
        val center = GeoPoint(-1.9536, 30.0606)
        val people = peopleItems(6, 1, emptyList()) + peopleItems(1, 0, emptyList()).map { it.copy(distanceKm = 1.2) }
        val first = scatterAround(7, center, 3.0, people)
        assertEquals(first, scatterAround(7, center, 3.0, people))
        first.dropLast(1).forEach { assertTrue(distanceKm(center, it) <= 3.0) }
        assertEquals(1.2, distanceKm(center, first.last()), 0.01)
    }

    @Test
    fun `search radius falls back to the wave and map radius fits everyone`() {
        val stats = session("""{"id":1,"jobId":1,"state":"pinging","stats":{"wave":2,"radiusKm":0}}""").stats
        assertEquals(6.0, searchRadiusKm(stats), 0.0)
        assertEquals(2.0, fitRadiusKm(listOf(0.4)), 0.0)
        assertEquals(10.0, fitRadiusKm(listOf(7.3, 1.0)), 0.0)
    }
}
