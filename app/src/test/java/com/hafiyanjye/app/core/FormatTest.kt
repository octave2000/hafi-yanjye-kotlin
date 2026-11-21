package com.hafiyanjye.app.core

import com.hafiyanjye.app.data.api.JobStatus
import com.hafiyanjye.app.data.api.SessionState
import com.hafiyanjye.app.ui.auth.passwordStrength
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class FormatTest {
    private val kigali = ZoneId.of("Africa/Kigali")
    private val now = Instant.parse("2026-11-17T12:00:00Z")

    @Test
    fun money() {
        assertEquals("20,000 RWF", Format.rwf(20000))
        assertEquals("No budget", Format.budget(null))
        assertEquals("1,500,000 RWF", Format.budget(1_500_000))
    }

    @Test
    fun distance() {
        assertEquals("850 m", Format.km(0.85))
        assertEquals("10 m", Format.km(0.001))
        assertEquals("2.4 km", Format.km(2.43))
        assertEquals("13 km", Format.km(12.6))
        assertEquals("—", Format.km(Double.NaN))
    }

    @Test
    fun `time ago`() {
        assertEquals("just now", Format.timeAgo(now.minusSeconds(30), now, kigali))
        assertEquals("3 min ago", Format.timeAgo(now.minusSeconds(180), now, kigali))
        assertEquals("2 h ago", Format.timeAgo(now.minusSeconds(7200), now, kigali))
        // 17 Nov 2026 is a Tuesday; two days before is Sunday.
        assertEquals("Sun", Format.timeAgo(now.minusSeconds(2 * 86400), now, kigali))
        assertEquals("1 Nov", Format.timeAgo(Instant.parse("2026-11-01T08:00:00Z"), now, kigali))
    }

    @Test
    fun countdowns() {
        assertEquals(0, Format.secondsUntil(now.minusSeconds(5), now))
        assertEquals(90, Format.secondsUntil(now.plusSeconds(90), now))
        assertEquals(1, Format.secondsUntil(now.plusMillis(200), now))
        assertEquals("48 s", Format.duration(48))
        assertEquals("2 min", Format.duration(130))
        assertEquals("1 h 5 min", Format.duration(3900))
    }

    @Test
    fun phones() {
        assertEquals("+250 78 123 4567", Format.phone("+250781234567"))
        assertEquals("+250 78 123 4567", Format.phone("0781234567"))
        assertEquals("+250781234567", Format.fullPhone("078 123 4567"))
        assertEquals("+254712345678", Format.fullPhone("+254712345678"))
        assertEquals("", Format.fullPhone("   "))
    }

    @Test
    fun `request status words`() {
        assertEquals("Finding people", jobStatusView(JobStatus.Open, SessionState.Pinging).label)
        assertTrue(jobStatusView(JobStatus.Open, SessionState.Eliminating).live)
        assertEquals("Matched", jobStatusView(JobStatus.Matched, SessionState.Matched).label)
        assertEquals("No one nearby", jobStatusView(JobStatus.Open, SessionState.NoProviders).label)
        assertEquals("Cancelled", jobStatusView(JobStatus.Cancelled, SessionState.Matched).label)
        assertEquals("Done", jobStatusView(JobStatus.Completed, SessionState.Completed).label)
    }

    @Test
    fun `password strength`() {
        assertEquals(0, passwordStrength("").score)
        assertEquals("1 more character", passwordStrength("1234567").label)
        assertEquals("Okay", passwordStrength("abcdefgh").label)
        assertEquals("Good", passwordStrength("Abcdefg1").label)
        assertEquals("Strong", passwordStrength("Abcdefghijk1").label)
    }
}
