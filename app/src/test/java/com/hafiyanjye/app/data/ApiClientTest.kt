package com.hafiyanjye.app.data

import com.hafiyanjye.app.data.api.ApiClient
import com.hafiyanjye.app.data.api.ApiException
import com.hafiyanjye.app.data.api.HafiApi
import com.hafiyanjye.app.data.api.Role
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class ApiClientTest {
    private val server = MockWebServer()
    private var token: String? = null
    private var rejected = 0
    private lateinit var api: HafiApi

    @Before
    fun setUp() {
        server.start()
        val http = OkHttpClient.Builder().readTimeout(500, TimeUnit.MILLISECONDS).build()
        api = HafiApi(ApiClient(server.url("/").toString(), http, { token }, { rejected++ }))
    }

    @After
    fun tearDown() = server.close()

    private fun enqueue(code: Int, body: String) {
        server.enqueue(
            MockResponse.Builder()
                .code(code)
                .addHeader("Content-Type", "application/json")
                .body(body)
                .build(),
        )
    }

    private val meJson = """{"id":1,"email":"a@b.rw","name":"Aline","activeRole":"seeker",""" +
        """"hasProviderProfile":false,"hasPassword":true}"""

    @Test
    fun `login posts json without a token and returns the session`() = runTest {
        enqueue(
            200,
            """{"ok":true,"data":{"user":$meJson,"token":"session_abc",""" +
                """"expiresAt":"2026-12-01T00:00:00.000Z"},"meta":{}}""",
        )
        val session = api.login("a@b.rw", "secret-pass")
        assertEquals("session_abc", session.token)
        assertEquals(Role.Hirer, session.user.role)

        val sent = server.takeRequest()
        assertEquals("POST", sent.method)
        assertEquals("/api/v1/auth/token/login", sent.url.encodedPath)
        assertNull(sent.headers["Authorization"])
        assertTrue(checkNotNull(sent.headers["Content-Type"]).startsWith("application/json"))
        assertEquals("""{"email":"a@b.rw","password":"secret-pass"}""", sent.body?.utf8())
    }

    @Test
    fun `signed-in calls carry the bearer token and query`() = runTest {
        token = "session_abc"
        enqueue(200, """{"ok":true,"data":[],"meta":{}}""")
        assertTrue(api.pings(history = true).isEmpty())
        val sent = server.takeRequest()
        assertEquals("Bearer session_abc", sent.headers["Authorization"])
        assertEquals("/api/v1/pings", sent.url.encodedPath)
        assertEquals("history", sent.url.queryParameter("scope"))
    }

    @Test
    fun `switching role sends the engine's word`() = runTest {
        token = "session_abc"
        enqueue(200, """{"ok":true,"data":${meJson.replace("\"seeker\"", "\"provider\"")},"meta":{}}""")
        assertEquals(Role.Worker, api.setRole(Role.Worker).role)
        val sent = server.takeRequest()
        assertEquals("PUT", sent.method)
        assertEquals("""{"role":"provider"}""", sent.body?.utf8())
    }

    @Test
    fun `register leaves out an empty phone`() = runTest {
        enqueue(200, """{"ok":true,"data":{"user":$meJson,"token":"t"},"meta":{}}""")
        api.register("Aline", "a@b.rw", "secret-pass", phone = "  ")
        assertEquals(
            """{"name":"Aline","email":"a@b.rw","password":"secret-pass"}""",
            server.takeRequest().body?.utf8(),
        )
    }

    @Test
    fun `a refused token signs the person out`() = runTest {
        token = "session_old"
        enqueue(401, """{"ok":false,"error":{"code":"UNAUTHORIZED","message":"Authentication required"},"meta":{}}""")
        try {
            api.me()
            fail("expected a 401")
        } catch (error: ApiException) {
            assertEquals(401, error.status)
        }
        assertEquals(1, rejected)
    }

    @Test
    fun `a wrong password is not a rejected session`() = runTest {
        enqueue(
            401,
            """{"ok":false,"error":{"code":"UNAUTHORIZED","message":"Email or password is incorrect"},"meta":{}}""",
        )
        try {
            api.login("a@b.rw", "wrong")
            fail("expected a 401")
        } catch (error: ApiException) {
            assertEquals("Email or password is incorrect", error.message)
        }
        assertEquals(0, rejected)
    }

    @Test
    fun `an unreachable server is a network error with status 0`() = runTest {
        server.close()
        try {
            api.me()
            fail("expected a network error")
        } catch (error: ApiException) {
            assertTrue(error.isNetwork)
            assertEquals(ApiException.NETWORK_ERROR, error.code)
        }
    }

    @Test
    fun `a server that never answers is a timeout`() = runTest {
        server.enqueue(MockResponse.Builder().code(200).headersDelay(3, TimeUnit.SECONDS).body("{}").build())
        try {
            api.me()
            fail("expected a timeout")
        } catch (error: ApiException) {
            assertEquals(ApiException.TIMEOUT, error.code)
        }
    }
}
