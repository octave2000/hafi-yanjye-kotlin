package com.hafiyanjye.app.pings

import com.hafiyanjye.app.data.api.ApiClient
import com.hafiyanjye.app.data.api.HafiJson
import com.hafiyanjye.app.data.api.Importance
import com.hafiyanjye.app.data.api.Ping
import com.hafiyanjye.app.data.api.PingStatus
import com.hafiyanjye.app.data.api.SessionState
import com.hafiyanjye.app.data.api.Urgency
import com.hafiyanjye.app.data.pings.Availability
import com.hafiyanjye.app.data.pings.PingsApi
import com.hafiyanjye.app.data.pings.PriceUnit
import com.hafiyanjye.app.data.pings.ProfileStage
import com.hafiyanjye.app.data.pings.WorkLevel
import com.hafiyanjye.app.data.pings.WorkStats
import com.hafiyanjye.app.ui.pings.HabitBanner
import com.hafiyanjye.app.ui.pings.PingView
import com.hafiyanjye.app.ui.pings.QuoteInput
import com.hafiyanjye.app.ui.pings.chosenPings
import com.hafiyanjye.app.ui.pings.formatCountdown
import com.hafiyanjye.app.ui.pings.habitBanner
import com.hafiyanjye.app.ui.pings.otherPings
import com.hafiyanjye.app.ui.pings.parseQuote
import com.hafiyanjye.app.ui.pings.pingView
import com.hafiyanjye.app.ui.pings.points
import com.hafiyanjye.app.ui.pings.quoteChips
import com.hafiyanjye.app.ui.pings.roundedEarnings
import com.hafiyanjye.app.ui.pings.unreadCount
import com.hafiyanjye.app.ui.pings.withArticle
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
import org.junit.Test
import java.time.Instant

class PingsTest {
    private val server = MockWebServer().apply { start() }
    private val api = PingsApi(ApiClient(server.url("/").toString(), OkHttpClient(), { "worker-token" }))

    @After
    fun tearDown() = server.close()

    private fun respond(data: String) {
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .addHeader("Content-Type", "application/json")
                .body(PingsFixtures.envelope(data))
                .build(),
        )
    }

    private fun bodyOf(request: mockwebserver3.RecordedRequest) =
        HafiJson.parseToJsonElement(request.body?.utf8().orEmpty()).jsonObject

    private val chosen = HafiJson.decodeFromString<Ping>(PingsFixtures.chosenPing)
    private val declined = HafiJson.decodeFromString<Ping>(PingsFixtures.declinedPing)

    @Test
    fun `real engine pings decode with the hirer named for what they are`() {
        assertEquals(PingStatus.Available, chosen.status)
        assertEquals(Urgency.Today, chosen.urgency)
        assertEquals(Importance.Must, chosen.requirements.first().importance)
        assertEquals("Aimable Nshuti", chosen.match?.hirerName)
        assertEquals("+250788000001", chosen.match?.hirerPhone)
        assertEquals(SessionState.Matched, chosen.match?.state)
        assertFalse(chosen.match?.hired == true)
        assertEquals(PingView.Chosen, pingView(chosen, 0L))
        assertEquals(PingView.Declined, pingView(declined, 0L))
        assertNull(declined.quoteRwf)
    }

    @Test
    fun `real work profile stats and ranking decode`() = runTest {
        respond(PingsFixtures.profile)
        val profile = api.profile()
        assertEquals("/api/v1/providers/me", server.takeRequest().url.encodedPath)
        assertEquals(WorkLevel.Gold, profile?.level)
        assertEquals(PriceUnit.Job, profile?.priceUnit)
        assertEquals(ProfileStage.Ready, profile?.profileStage)
        assertEquals(Availability.Offline, profile?.availability)
        assertEquals("Zap", profile?.categories?.first()?.icon)

        respond("null")
        assertNull(api.profile())
        server.takeRequest()

        respond(PingsFixtures.newcomerStats)
        val stats = api.stats()
        assertEquals("/api/v1/providers/me/stats", server.takeRequest().url.encodedPath)
        assertEquals(WorkLevel.Silver, stats.nextLevel)
        assertEquals("8 of 10", stats.nextLevelChecklist.first().progress)
        assertEquals(7, stats.missedWhileOffline)
        assertEquals(HabitBanner.Paused, habitBanner(Availability.Offline, stats))

        respond(PingsFixtures.newcomerRanking)
        val ranking = api.ranking()
        assertEquals("/api/v1/providers/me/ranking", server.takeRequest().url.encodedPath)
        assertNull(ranking.parts.first().points)
        assertEquals(9, ranking.starter?.jobsLeft)
        assertEquals("28.1", points(ranking.standingTotal))
        assertEquals("9", points(ranking.parts[1].items.first().points))

        respond("[${PingsFixtures.review}]")
        val reviews = api.reviews(1)
        assertEquals("/api/v1/providers/1/reviews", server.takeRequest().url.encodedPath)
        assertEquals("Claudine Mukarugwiza", reviews.single().reviewerName)
    }

    @Test
    fun `answer is a PUT with a trimmed note and an optional quote`() = runTest {
        respond(PingsFixtures.chosenPing)
        api.answerAvailable(7, 2500, "  I bring tools.  ")
        val request = server.takeRequest()
        assertEquals("PUT", request.method)
        assertEquals("/api/v1/pings/7/answer", request.url.encodedPath)
        assertEquals("Bearer worker-token", request.headers["Authorization"])
        val body = bodyOf(request)
        assertEquals("true", body.getValue("available").jsonPrimitive.content)
        assertEquals("2500", body.getValue("quoteRwf").jsonPrimitive.content)
        assertEquals("I bring tools.", body.getValue("note").jsonPrimitive.content)

        respond(PingsFixtures.chosenPing)
        api.answerAvailable(7, null, "   ")
        val emptyBody = bodyOf(server.takeRequest())
        assertFalse(emptyBody.containsKey("note"))
        assertFalse(emptyBody.containsKey("quoteRwf"))
    }

    @Test
    fun `lists, seen, not now, withdraw and availability use the engine routes`() = runTest {
        respond("[${PingsFixtures.declinedPing}]")
        assertEquals(1, api.pings(PingsApi.Scope.History).size)
        assertEquals("history", server.takeRequest().url.queryParameter("scope"))

        respond(PingsFixtures.chosenPing)
        api.markSeen(7)
        val seen = server.takeRequest()
        assertEquals("PUT", seen.method)
        assertEquals("/api/v1/pings/7/seen", seen.url.encodedPath)

        respond(PingsFixtures.declinedPing)
        api.answerNotNow(7)
        val decline = server.takeRequest()
        assertEquals("/api/v1/pings/7/answer", decline.url.encodedPath)
        assertEquals("false", bodyOf(decline).getValue("available").jsonPrimitive.content)

        respond(PingsFixtures.declinedPing)
        api.withdraw(7)
        val withdraw = server.takeRequest()
        assertEquals("PUT", withdraw.method)
        assertEquals("/api/v1/pings/7/withdraw", withdraw.url.encodedPath)

        respond(PingsFixtures.profile)
        api.setAvailability(Availability.Online)
        val online = server.takeRequest()
        assertEquals("PUT", online.method)
        assertEquals("/api/v1/providers/me/availability", online.url.encodedPath)
        assertEquals("online", bodyOf(online).getValue("availability").jsonPrimitive.content)
    }

    @Test
    fun `an open ping is new, then needs an answer, then expires on the phone's clock`() {
        val open = declined.copy(status = PingStatus.Sent, expiresAt = Instant.now().plusSeconds(300))
        assertEquals(PingView.New, pingView(open, 300L))
        assertEquals(PingView.Seen, pingView(open.copy(status = PingStatus.Seen), 30L))
        assertEquals(PingView.Expired, pingView(open, 0L))
        assertEquals(PingView.Waiting, pingView(open.copy(status = PingStatus.Available), 30L))
        assertEquals(PingView.Withdrawn, pingView(open.copy(status = PingStatus.Withdrawn), 30L))

        val list = listOf(chosen, open, open.copy(id = 99, status = PingStatus.Seen))
        assertEquals(listOf(chosen), chosenPings(list))
        assertEquals(2, otherPings(list).size)
        assertEquals(1, unreadCount(list))
    }

    @Test
    fun `quotes obey the engine's whole RWF limits and chips round to 500`() {
        assertEquals(QuoteInput.None, parseQuote(" "))
        assertEquals(QuoteInput.Amount(500), parseQuote("500"))
        assertEquals(QuoteInput.Amount(10_000_000), parseQuote("10000000"))
        listOf("499", "10000001", "1.5", "999999999999999999999").forEach {
            assertTrue(it, parseQuote(it) is QuoteInput.Invalid)
        }
        assertEquals(listOf(18_500, 20_500, 22_500, 25_500), quoteChips(20_500).map { it.value })
        assertTrue(quoteChips(null).isEmpty())
    }

    @Test
    fun `banners, countdown and wording follow the web`() {
        val stats = WorkStats(missedWhileOffline = 3)
        assertEquals(HabitBanner.Missed, habitBanner(Availability.Offline, stats))
        assertNull(habitBanner(Availability.Online, stats))
        assertNull(habitBanner(Availability.Offline, WorkStats()))
        assertEquals("0:00", formatCountdown(0))
        assertEquals("1:15", formatCountdown(75))
        assertEquals("1:02:05", formatCountdown(3725))
        assertEquals("an electrician", withArticle("Electrician"))
        assertEquals("a plumber", withArticle("Plumber"))
        assertEquals(24_000, roundedEarnings(24_400))
        assertNull(roundedEarnings(0))
    }
}
