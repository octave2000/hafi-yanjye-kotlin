package com.hafiyanjye.app.match

import com.hafiyanjye.app.data.api.ApiClient
import com.hafiyanjye.app.data.api.HafiJson
import com.hafiyanjye.app.data.match.MatchApi
import com.hafiyanjye.app.data.match.RefineKind
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class MatchApiTest {
    private val server = MockWebServer()
    private lateinit var api: MatchApi

    @Before
    fun setUp() {
        server.start()
        api = MatchApi(ApiClient(server.url("/").toString(), OkHttpClient(), { "session-test" }))
    }

    @After
    fun tearDown() = server.close()

    private fun reply(data: String) {
        val response = MockResponse.Builder()
            .code(200)
            .addHeader("Content-Type", "application/json")
            .body("""{"ok":true,"data":$data,"meta":{}}""")
            .build()
        server.enqueue(response)
    }

    private val session = """{"id":8,"jobId":12,"state":"matched"}"""

    @Test
    fun `choose uses engine profile id while Kotlin exposes worker id`() = runTest {
        reply(session)
        api.choose(8, 42)
        val sent = server.takeRequest()
        assertEquals("POST", sent.method)
        assertEquals("/api/v1/match/8/choose", sent.url.encodedPath)
        assertEquals("Bearer session-test", sent.headers["Authorization"])
        assertEquals("""{"providerId":42}""", sent.body?.utf8())
    }

    @Test
    fun `hire and completion post to the session resource`() = runTest {
        reply(session)
        api.hire(8)
        assertEquals("/api/v1/match/8/hire", server.takeRequest().url.encodedPath)
        reply(session.replace("matched", "completed"))
        api.complete(8)
        val sent = server.takeRequest()
        assertEquals("POST", sent.method)
        assertEquals("/api/v1/match/8/complete", sent.url.encodedPath)
    }

    @Test
    fun `request actions use the job path and the engine's methods`() = runTest {
        val job = Fixtures.NobodyFreeJob
        reply(job)
        api.setBudget(12, 20_000)
        var sent = server.takeRequest()
        assertEquals("PUT", sent.method)
        assertEquals("/api/v1/jobs/12/budget", sent.url.encodedPath)
        assertEquals("""{"budgetRwf":20000}""", sent.body?.utf8())
        reply(job)
        api.cancel(12)
        sent = server.takeRequest()
        assertEquals("PUT", sent.method)
        assertEquals("/api/v1/jobs/12/cancel", sent.url.encodedPath)
        reply(job)
        api.setNotifyMe(12, true)
        sent = server.takeRequest()
        assertEquals("/api/v1/jobs/12/notify-me", sent.url.encodedPath)
        assertEquals("""{"notifyMe":true}""", sent.body?.utf8())
        reply(job)
        api.retry(12)
        sent = server.takeRequest()
        assertEquals("POST", sent.method)
        assertEquals("/api/v1/jobs/12/retry", sent.url.encodedPath)
    }

    @Test
    fun `reject leaves out an empty reason and answer sends the round`() = runTest {
        reply(session)
        api.reject(8, "   ")
        var sent = server.takeRequest()
        assertEquals("/api/v1/match/8/reject", sent.url.encodedPath)
        assertEquals("{}", sent.body?.utf8())
        reply(session.replace("matched", "eliminating"))
        api.answer(8, 2, "o1")
        sent = server.takeRequest()
        assertEquals("/api/v1/match/8/answer", sent.url.encodedPath)
        assertEquals("""{"roundNo":2,"optionId":"o1"}""", sent.body?.utf8())
    }

    @Test
    fun `refine and ping more decode the engine answers`() = runTest {
        reply("""{"kind":"none","providerId":null,"reason":"Nobody's profile mentions that.","source":"fallback"}""")
        assertEquals(RefineKind.None, api.refine(8, " speaks French ").kind)
        val sent = server.takeRequest()
        assertEquals("""{"text":"speaks French"}""", sent.body?.utf8())
        reply("""{"pinged":3,"session":$session}""")
        assertEquals(3, api.pingMore(8).pinged)
        assertEquals("/api/v1/match/8/ping-more", server.takeRequest().url.encodedPath)
    }

    @Test
    fun `review trims optional text and decodes server review`() = runTest {
        reply("""{"id":6,"rating":5,"text":"On time","reviewerName":"Aline","createdAt":"2026-11-17T12:00:00Z"}""")
        assertEquals("Aline", api.review(8, 5, "  On time  ").reviewerName)
        val sent = server.takeRequest()
        assertEquals("/api/v1/reviews/create", sent.url.encodedPath)
        val body = HafiJson.parseToJsonElement(requireNotNull(sent.body).utf8()).jsonObject
        assertEquals("8", body["sessionId"]?.jsonPrimitive?.content)
        assertEquals("5", body["rating"]?.jsonPrimitive?.content)
        assertEquals("On time", body["text"]?.jsonPrimitive?.content)
    }
}
