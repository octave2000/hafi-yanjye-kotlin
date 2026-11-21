package com.hafiyanjye.app.push

import com.hafiyanjye.app.data.api.ApiClient
import com.hafiyanjye.app.data.push.FirebaseConfig
import com.hafiyanjye.app.data.push.PushApi
import com.hafiyanjye.app.data.push.PushContent
import com.hafiyanjye.app.data.push.PushPermission
import com.hafiyanjye.app.data.push.PushState
import com.hafiyanjye.app.ui.push.pushDescription
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PushApiTest {

    private fun json(body: String) = MockResponse.Builder()
        .code(200)
        .addHeader("Content-Type", "application/json")
        .body(body)
        .build()

    @Test
    fun `register and unregister send what the engine reads`() = runTest {
        MockWebServer().use { server ->
            server.start()
            val api = PushApi(ApiClient(server.url("/").toString(), OkHttpClient(), { "account-session" }))

            server.enqueue(json("""{"ok":true,"data":{"registered":true},"meta":{}}"""))
            assertTrue(api.register("fcm-device-token").registered)
            val register = server.takeRequest()
            assertEquals("POST", register.method)
            assertEquals("/api/v1/push/devices/register", register.url.encodedPath)
            assertEquals("Bearer account-session", register.headers["Authorization"])
            assertEquals("""{"token":"fcm-device-token","platform":"android"}""", register.body?.utf8())

            // The engine's real answer for a token it does not hold (local engine).
            server.enqueue(json("""{"ok":true,"data":{"removed":false},"meta":{}}"""))
            assertFalse(api.unregister("fcm-device-token").removed)
            val unregister = server.takeRequest()
            assertEquals("POST", unregister.method)
            assertEquals("/api/v1/push/devices/unregister", unregister.url.encodedPath)
            assertEquals("""{"token":"fcm-device-token"}""", unregister.body?.utf8())
        }
    }
}

class PushLogicTest {

    @Test
    fun `firebase starts only with all four values`() {
        assertNull(FirebaseConfig.of("", "", "", ""))
        assertNull(FirebaseConfig.of("1:2:android:3", "key", "hafi", " "))
        assertNotNull(FirebaseConfig.of("1:2:android:3", "key", "hafi", "1234"))
    }

    @Test
    fun `engine messages become notifications`() {
        val job = PushContent.from(
            "Kevin is on the way",
            "Follow them live on the map in your request.",
            mapOf("url" to "/jobs/4533", "tag" to "job-4533"),
        )
        assertEquals("/jobs/4533", job?.url)
        assertEquals("job-4533", job?.tag)

        // Without a tag, repeats for the same screen replace each other.
        assertEquals("/provider", PushContent.from("New ping", "", mapOf("url" to "/provider"))?.tag)
        // Only paths inside the app are kept.
        assertNull(PushContent.from("Hi", "There", mapOf("url" to "https://example.com"))?.url)
        assertNull(PushContent.from(null, "No title", emptyMap()))
        assertEquals("Hello", PushContent.from(null, null, mapOf("title" to "Hello"))?.title)
    }

    @Test
    fun `the switch says what is true on this phone`() {
        val notSetUp = PushState(configured = false)
        assertTrue(pushDescription(notSetUp).startsWith("Push notifications are not set up in this build"))

        val on = PushState(configured = true, permission = PushPermission.Granted, registered = true)
        assertTrue(on.on)
        assertTrue(pushDescription(on).startsWith("On:"))

        val notAsked = PushState(configured = true, permission = PushPermission.NotAsked)
        assertFalse(notAsked.on)
        assertTrue(pushDescription(notAsked).startsWith("Off:"))

        val blocked = PushState(configured = true, permission = PushPermission.Blocked, registered = true)
        assertFalse(blocked.on)
        assertTrue(pushDescription(blocked).startsWith("Notifications are blocked"))

        assertFalse(on.copy(wanted = false).on)
    }
}
