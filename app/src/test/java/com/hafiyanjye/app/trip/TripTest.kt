package com.hafiyanjye.app.trip

import com.hafiyanjye.app.data.api.HafiJson
import com.hafiyanjye.app.data.api.Role
import com.hafiyanjye.app.data.trip.DelayReason
import com.hafiyanjye.app.data.trip.RawStep
import com.hafiyanjye.app.data.trip.Trip
import com.hafiyanjye.app.data.trip.TripRoutes
import com.hafiyanjye.app.data.trip.TripRouting
import com.hafiyanjye.app.data.trip.TripStatus
import com.hafiyanjye.app.ui.location.GeoPoint
import com.hafiyanjye.app.ui.trip.TripText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TripTest {

    /** Captured from the local engine (session 4513) while on the way. */
    private val onTheWay = """
        {"sessionId":4513,"jobId":4514,"role":"seeker",
         "destination":{"lat":-1.9493,"lng":30.1262,"label":"Kimironko"},
         "provider":{"name":"Josiane Uwamahoro","picture":null},
         "seeker":{"name":"Aimable Nshuti","picture":null},
         "status":"on_the_way","startedAt":"2026-11-17T23:40:28.443Z","arrivedAt":null,
         "position":{"lat":-1.955,"lng":30.11,"heading":80,"accuracy":24,"at":"2026-11-17T23:40:28.463Z"},
         "delay":null,"live":true}
    """.trimIndent()

    private fun trip(json: String = onTheWay): Trip = HafiJson.decodeFromString(Trip.serializer(), json)

    @Test
    fun `engine trip decodes with money role names`() {
        val trip = trip()
        assertEquals(Role.Hirer, trip.role)
        assertEquals("Josiane Uwamahoro", trip.worker.name)
        assertEquals("Aimable Nshuti", trip.hirer.name)
        assertEquals(TripStatus.OnTheWay, trip.status)
        assertEquals(24.0, trip.position?.accuracy)
        assertTrue(trip.live)
    }

    @Test
    fun `delay decodes its reason`() {
        val json = onTheWay.replace(
            "\"delay\":null",
            "\"delay\":{\"minutes\":15,\"reason\":\"previous_job\",\"note\":null,\"at\":\"2026-11-17T23:41:00.000Z\"}",
        )
        assertEquals(DelayReason.PreviousJob, trip(json).delay?.reason)
        assertEquals(
            "Josiane is running about 15 min late · Finishing another job",
            TripText.delayLine(trip(json)),
        )
    }

    @Test
    fun `headline follows each state for both sides`() {
        val watching = trip()
        assertEquals("Josiane is 1.9 km away in a straight line", TripText.headline(watching, null))
        val seeing = trip(onTheWay.replace("\"role\":\"seeker\"", "\"role\":\"provider\""))
        assertEquals("You're 1.9 km away in a straight line", TripText.headline(seeing, null))
        val notStarted = trip(
            onTheWay
                .replace("\"on_the_way\"", "\"not_started\"")
                .replace("\"role\":\"seeker\"", "\"role\":\"provider\""),
        )
        assertEquals(
            "Tap Start trip when you leave, so Aimable can follow you on the map.",
            TripText.headline(notStarted, null),
        )
        val arrived = trip(onTheWay.replace("\"on_the_way\"", "\"arrived\""))
        assertEquals("Josiane has arrived.", TripText.headline(arrived, null))
        val finished = trip(onTheWay.replace("\"live\":true", "\"live\":false"))
        assertEquals("The job is finished.", TripText.headline(finished, null))
    }

    @Test
    fun `very close uses the phone's accuracy instead of a distance`() {
        val near = trip(onTheWay.replace("\"lat\":-1.955,\"lng\":30.11", "\"lat\":-1.9495,\"lng\":30.1262"))
        assertEquals("Josiane is very close · within about 20 m", TripText.headline(near, null))
        assertTrue(TripText.isVeryClose(0.2, 250.0))
        assertFalse(TripText.isVeryClose(0.2, 5_000.0))
    }

    @Test
    fun `road progress shortens the route and finds the next turn`() {
        val start = GeoPoint(-1.95, 30.06)
        val middle = GeoPoint(-1.95, 30.07)
        val end = GeoPoint(-1.95, 30.08)
        val route = TripRoutes.build(
            path = listOf(start, middle, end),
            durationSeconds = 600.0,
            steps = listOf(
                RawStep("Head east", "DEPART", start),
                RawStep("Turn right onto KG 11 Ave", "TURN_RIGHT", middle),
            ),
            mode = "TWO_WHEELER",
        )
        assertNotNull(route)
        requireNotNull(route).let { built ->
            assertEquals(1, built.steps.size)
            val atStart = TripRoutes.progress(built, start)
            assertEquals("Turn right onto KG 11 Ave", atStart.next?.step?.instructions)
            assertEquals(600.0, atStart.remainingSeconds, 1.0)
            val half = TripRoutes.progress(built, middle)
            assertEquals(listOf(middle, end), half.remainingPath.distinct())
            assertEquals(300.0, half.remainingSeconds, 1.0)
            assertNull(half.next)
            assertFalse(TripRoutes.isOffRoute(half, 20.0))
            assertTrue(TripRoutes.isOffRoute(TripRoutes.progress(built, GeoPoint(-1.96, 30.07)), 20.0))
        }
    }

    @Test
    fun `distance and time read the way the web words them`() {
        assertEquals("150 m", TripRoutes.roadDistanceText(147.0))
        assertEquals("1.2 km", TripRoutes.roadDistanceText(1_234.0))
        assertEquals("about 1 min", TripRoutes.etaText(10.0))
        assertEquals("about 1.5 h", TripRoutes.etaText(5_400.0))
        assertEquals("350 m", TripText.straightDistanceText(0.34))
    }

    @Test
    fun `routes answer without a route gives null so a straight line is drawn`() {
        assertNull(TripRouting.parse("""{"routes":[]}""", "DRIVE"))
    }
}
