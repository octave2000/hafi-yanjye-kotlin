package com.hafiyanjye.app.data

import com.hafiyanjye.app.data.api.ApiException
import com.hafiyanjye.app.data.api.Envelope
import com.hafiyanjye.app.data.api.JobListItem
import com.hafiyanjye.app.data.api.JobStatus
import com.hafiyanjye.app.data.api.Me
import com.hafiyanjye.app.data.api.Ping
import com.hafiyanjye.app.data.api.Role
import com.hafiyanjye.app.data.api.SessionState
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.json.JsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class EnvelopeTest {
    private val json = "application/json; charset=utf-8"

    @Test
    fun `reads the account with the engine's role names`() {
        val body = """{"ok":true,"data":{"id":7,"email":"a@b.rw","name":"Aline Uwase","picture":null,
            "phone":"+250781234567","activeRole":"provider","hasProviderProfile":true,"hasPassword":true,
            "createdAt":"2026-11-01T08:00:00.000Z","somethingNew":1},"meta":{"requestId":"r","timestamp":"t"}}"""
        val me = Envelope.unwrap(200, json, body, Me.serializer())
        assertEquals(7, me.id)
        // Engine "provider" is the job seeker.
        assertEquals(Role.Worker, me.role)
        assertTrue(me.hasWorkProfile)
        assertEquals("Aline", me.firstName)
        assertNull(me.picture)
    }

    @Test
    fun `reads a list of requests with dates and null budgets`() {
        val body = """{"ok":true,"data":[{"id":3,"summary":"Fix a leaking tap","categoryName":"Plumber",
            "categoryIcon":"Droplets","status":"open","state":"shortlisting","budgetRwf":null,
            "addressLabel":"Kicukiro","createdAt":"2026-11-17T10:15:30.000Z"}],"meta":{}}"""
        val jobs = Envelope.unwrap(200, json, body, ListSerializer(JobListItem.serializer()))
        assertEquals(1, jobs.size)
        assertEquals(JobStatus.Open, jobs[0].status)
        assertEquals(SessionState.Shortlisting, jobs[0].state)
        assertNull(jobs[0].budgetRwf)
        assertEquals(Instant.parse("2026-11-17T10:15:30Z"), jobs[0].createdAt)
    }

    @Test
    fun `reads a matched ping`() {
        val body = """{"ok":true,"data":{"id":1,"jobId":2,"status":"available","wave":1,"distanceKm":1.4,
            "summary":"Paint a room","categoryName":"Painter",
            "requirements":[{"label":"Own tools","importance":"must"}],
            "urgency":"this_week","budgetRwf":30000,"areaLabel":"Remera","quoteRwf":28000,"note":null,
            "sentAt":"2026-11-17T10:00:00.000Z","expiresAt":"2026-11-17T10:05:00.000Z","direct":false,
            "match":{"sessionId":9,"seekerName":"Eric","seekerPhone":null,"addressLabel":null,"lat":-1.95,
            "lng":30.06,"state":"matched","hired":true}},"meta":{}}"""
        val ping = Envelope.unwrap(200, json, body, Ping.serializer())
        assertEquals("Eric", ping.match?.hirerName)
        assertTrue(ping.match?.hired == true)
        assertEquals(1, ping.requirements.size)
    }

    @Test
    fun `an empty answer decodes to null`() {
        val body = """{"ok":true,"data":null,"meta":{}}"""
        assertNull(Envelope.unwrap(200, json, body, JsonElement.serializer().nullable))
        assertNull(Envelope.unwrap(200, json, """{"ok":true,"meta":{}}""", JsonElement.serializer().nullable))
    }

    @Test
    fun `an engine error keeps its status, code, message and request id`() {
        val body = """{"ok":false,"error":{"code":"UNAUTHORIZED","message":"Email or password is incorrect"},
            "meta":{"requestId":"abc","timestamp":"t"}}"""
        val error = assertThrows(ApiException::class.java) { Envelope.unwrap(401, json, body, Me.serializer()) }
        assertEquals(401, error.status)
        assertEquals("UNAUTHORIZED", error.code)
        assertEquals("Email or password is incorrect", error.message)
        assertEquals("abc", error.requestId)
    }

    @Test
    fun `a failure that is not an envelope gets a plain message`() {
        val error = assertThrows(ApiException::class.java) {
            Envelope.unwrap(502, "text/html", "<html>Bad gateway</html>", Me.serializer())
        }
        assertEquals(ApiException.REQUEST_FAILED, error.code)
        assertEquals("Our server had a problem. Try again in a moment.", error.message)
    }

    @Test
    fun `a 200 that is not from the engine is refused`() {
        val html = assertThrows(ApiException::class.java) {
            Envelope.unwrap(200, "text/html", "<html>Sign in to Wi-Fi</html>", Me.serializer())
        }
        assertEquals(ApiException.BAD_RESPONSE, html.code)
        val broken = assertThrows(ApiException::class.java) { Envelope.unwrap(200, json, "{not json", Me.serializer()) }
        assertEquals(ApiException.BAD_RESPONSE, broken.code)
    }

    @Test
    fun `data of the wrong shape is refused, not crashed on`() {
        val body = """{"ok":true,"data":{"id":"seven"},"meta":{}}"""
        val error = assertThrows(ApiException::class.java) { Envelope.unwrap(200, json, body, Me.serializer()) }
        assertEquals(ApiException.BAD_RESPONSE, error.code)
    }
}
