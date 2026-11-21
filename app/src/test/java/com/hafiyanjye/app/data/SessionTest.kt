package com.hafiyanjye.app.data

import com.hafiyanjye.app.data.api.ApiClient
import com.hafiyanjye.app.data.api.HafiApi
import com.hafiyanjye.app.data.api.Role
import com.hafiyanjye.app.data.session.Session
import com.hafiyanjye.app.data.session.SessionState
import com.hafiyanjye.app.data.session.TokenVault
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private class MemoryVault(var token: String? = null) : TokenVault {
    override fun read() = token
    override fun write(token: String) {
        this.token = token
    }
    override fun clear() {
        token = null
    }
}

class SessionTest {
    private val server = MockWebServer()
    private val vault = MemoryVault()
    private lateinit var session: Session

    private fun me(role: String = "seeker") =
        """{"id":1,"email":"a@b.rw","name":"Aline","activeRole":"$role",""" +
            """"hasProviderProfile":false,"hasPassword":true}"""

    private fun enqueue(code: Int, body: String) {
        server.enqueue(
            MockResponse.Builder()
                .code(code)
                .addHeader("Content-Type", "application/json")
                .body(body)
                .build(),
        )
    }

    private fun start(savedToken: String? = null) {
        vault.token = savedToken
        session = Session(vault)
        session.api = HafiApi(
            ApiClient(server.url("/").toString(), OkHttpClient(), session::token, session::onRejected),
        )
    }

    @Before
    fun setUp() = server.start()

    @After
    fun tearDown() = server.close()

    @Test
    fun `no saved token means signed out without asking the engine`() = runTest {
        start()
        assertEquals(SessionState.SignedOut, session.state.value)
        session.restore()
        assertEquals(SessionState.SignedOut, session.state.value)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `a saved token is confirmed and loads the account`() = runTest {
        start(savedToken = "session_abc")
        assertEquals(SessionState.Checking, session.state.value)
        enqueue(200, """{"ok":true,"data":${me()},"meta":{}}""")
        session.restore()
        assertEquals("Aline", (session.state.value as SessionState.SignedIn).me.name)
    }

    @Test
    fun `an expired token is dropped and the person is signed out`() = runTest {
        start(savedToken = "session_old")
        enqueue(401, """{"ok":false,"error":{"code":"UNAUTHORIZED","message":"Authentication required"},"meta":{}}""")
        session.restore()
        assertEquals(SessionState.SignedOut, session.state.value)
        assertNull(vault.token)
    }

    @Test
    fun `no connection keeps the token and offers a retry`() = runTest {
        start(savedToken = "session_abc")
        server.close()
        session.restore()
        assertTrue(session.state.value is SessionState.Unreachable)
        assertEquals("session_abc", vault.token)
    }

    @Test
    fun `login saves the token`() = runTest {
        start()
        enqueue(200, """{"ok":true,"data":{"user":${me()},"token":"session_new"},"meta":{}}""")
        session.login("a@b.rw", "secret-pass")
        assertEquals("session_new", vault.token)
        assertTrue(session.state.value is SessionState.SignedIn)
    }

    @Test
    fun `registering as a job seeker switches the new account to that side`() = runTest {
        start()
        enqueue(200, """{"ok":true,"data":{"user":${me()},"token":"session_new"},"meta":{}}""")
        enqueue(200, """{"ok":true,"data":${me("provider")},"meta":{}}""")
        session.register("Aline", "a@b.rw", "secret-pass", null, Role.Worker)
        assertEquals(Role.Worker, (session.state.value as SessionState.SignedIn).me.role)
        server.takeRequest()
        assertEquals("Bearer session_new", server.takeRequest().headers["Authorization"])
    }

    @Test
    fun `logout clears the phone even when the engine is unreachable`() = runTest {
        start(savedToken = "session_abc")
        server.close()
        session.logout()
        assertNull(vault.token)
        assertEquals(SessionState.SignedOut, session.state.value)
    }
}
