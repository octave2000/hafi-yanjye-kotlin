package com.hafiyanjye.app.profile

import com.hafiyanjye.app.data.api.ApiClient
import com.hafiyanjye.app.data.api.HafiJson
import com.hafiyanjye.app.data.api.Me
import com.hafiyanjye.app.data.profile.Countries
import com.hafiyanjye.app.data.profile.CropView
import com.hafiyanjye.app.data.profile.Notifications
import com.hafiyanjye.app.data.profile.PhoneRules
import com.hafiyanjye.app.data.profile.ProfileApi
import com.hafiyanjye.app.data.profile.ProfileRules
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.util.concurrent.TimeUnit

class PhoneRulesTest {
    private val rwanda = Countries.byIso("RW")
    private val kenya = Countries.byIso("KE")

    @Test
    fun `the country list is the web's`() {
        assertEquals(245, Countries.all.size)
        assertEquals("250", Countries.default.dial)
        assertEquals(listOf("RW", "BI", "CD", "UG", "TZ", "KE"), Countries.nearby.map { it.iso })
        assertEquals("🇷🇼", rwanda.flag)
    }

    @Test
    fun `full number drops the trunk zero and keeps typed international numbers`() {
        assertEquals("+250781234567", PhoneRules.full(rwanda, "078 123 4567"))
        assertEquals("+250781234567", PhoneRules.full(rwanda, "78-123-4567"))
        assertEquals("+254712345678", PhoneRules.full(kenya, "0712 345 678"))
        assertEquals("+44 7400 123456", PhoneRules.full(rwanda, " +44 7400 123456 "))
        assertEquals("00243812345678", PhoneRules.full(rwanda, "00243812345678"))
        assertEquals("", PhoneRules.full(rwanda, "  "))
        assertEquals("", PhoneRules.full(rwanda, "000"))
    }

    @Test
    fun `rwandan numbers follow the web's rule`() {
        assertNull(PhoneRules.problem(rwanda, "078 123 4567"))
        assertNull(PhoneRules.problem(rwanda, "72 123 4567"))
        assertNull(PhoneRules.problem(kenya, "+250 79 123 4567"))
        assertNull(PhoneRules.problem(rwanda, ""))
        assertEquals("Enter a Rwandan mobile number, e.g. 078 123 4567.", PhoneRules.problem(rwanda, "078 123 456"))
        // 075 is not a Rwandan mobile prefix.
        assertNotNull(PhoneRules.problem(rwanda, "075 123 4567"))
        assertNotNull(PhoneRules.problem(rwanda, "0781234567a8"))
        assertNotNull(PhoneRules.problem(rwanda, "0781234567a"))
        assertNotNull(PhoneRules.problem(rwanda, "0781234567+"))
        assertNotNull(PhoneRules.problem(Countries.byIso("KE"), "0712345678x"))
    }

    @Test
    fun `other countries need a plausible international number`() {
        assertNull(PhoneRules.problem(kenya, "712 345 678"))
        assertNull(PhoneRules.problem(rwanda, "+44 7400 123456"))
        assertEquals("Enter a valid mobile number for Kenya.", PhoneRules.problem(kenya, "712"))
        assertNotNull(PhoneRules.problem(rwanda, "+44 12"))
        assertNotNull(PhoneRules.problem(kenya, "7123456789012345"))
    }

    @Test
    fun `a stored number opens on its country`() {
        assertEquals(rwanda to "781234567", Countries.split("+250781234567"))
        assertEquals(kenya to "712345678", Countries.split("+254712345678"))
        assertEquals("US", Countries.split("+12015550123").first.iso)
        assertEquals("AS", Countries.split("+16841234567").first.iso)
        assertEquals(rwanda to "", Countries.split(null))
        // What was split goes back together unchanged.
        val (country, national) = Countries.split("+250781234567")
        assertEquals("+250781234567", PhoneRules.full(country, national))
    }

    @Test
    fun `search finds by name, code and calling code`() {
        assertEquals("KE", Countries.search("ken").first().iso)
        assertTrue(Countries.search("+254").any { it.iso == "KE" })
        assertTrue(Countries.search("ke").any { it.iso == "KE" })
        assertEquals("CI", Countries.search("cote").first().iso)
        assertTrue(Countries.search("zzzz").isEmpty())
        assertEquals(Countries.all, Countries.search(" "))
    }
}

class ProfileRulesTest {
    private val me = Me(id = 1, email = "a@b.rw", name = "Aline Uwase", phone = "+250781234567", hasPassword = true)

    @Test
    fun `update body holds only what changed`() {
        assertNull(ProfileRules.updateBody(me, " Aline Uwase ", "+250781234567"))
        assertEquals("""{"name":"Aline U."}""", ProfileRules.updateBody(me, "Aline U. ", "+250781234567").toString())
        val kenyan = ProfileRules.updateBody(me, "Aline Uwase", "+254712345678")
        assertEquals("""{"phone":"+254712345678"}""", kenyan.toString())
        // An emptied phone is removed with an explicit null; an emptied name is never sent.
        assertEquals("""{"phone":null}""", ProfileRules.updateBody(me, "", "").toString())
        assertEquals("""{"picture":null}""", ProfileRules.removePictureBody.toString())
    }

    @Test
    fun `name is required and bounded`() {
        assertNull(ProfileRules.nameProblem("Aline"))
        assertNotNull(ProfileRules.nameProblem("  "))
        assertNotNull(ProfileRules.nameProblem("a".repeat(101)))
    }

    @Test
    fun `deleting needs the word and, with a password, the password`() {
        assertFalse(ProfileRules.canDelete(me, "delete", ""))
        assertTrue(ProfileRules.canDelete(me, " delete ", "secret"))
        assertFalse(ProfileRules.canDelete(me, "DELET", "secret"))
        val google = me.copy(hasPassword = false)
        assertTrue(ProfileRules.canDelete(google, "DELETE", ""))
        assertNull(ProfileRules.deleteInput(google, "DELETE", "typed anyway").password)
        assertEquals("secret", ProfileRules.deleteInput(me, " DELETE ", "secret").password)
        assertEquals("DELETE", ProfileRules.deleteInput(me, " DELETE ", "secret").confirm)
    }

    @Test
    fun `crop view always covers the frame`() {
        // A 400x200 photo at zoom 1: the short side fills the frame, so it can slide half a frame sideways only.
        val view = CropView(x = 5f, y = 5f).clamped(400, 200)
        assertEquals(0.5f, view.x, 0.0001f)
        assertEquals(0f, view.y, 0.0001f)
        // Turned a quarter, the long side is vertical.
        val turned = CropView(x = 5f, y = -5f, turn = 1).clamped(400, 200)
        assertEquals(0f, turned.x, 0.0001f)
        assertEquals(-0.5f, turned.y, 0.0001f)
        assertEquals(4f, CropView(zoom = 9f).clamped(100, 100).zoom, 0f)
        assertEquals(3, CropView().turned(-1).turn)
        assertEquals(0, CropView(turn = 3, x = 0.2f).turned(1).let { it.turn + it.x.toInt() })
    }
}

class ProfileApiTest {
    private val server = MockWebServer()
    private lateinit var api: ProfileApi

    @Before
    fun setUp() {
        server.start()
        val http = OkHttpClient.Builder().readTimeout(500, TimeUnit.MILLISECONDS).build()
        api = ProfileApi(ApiClient(server.url("/").toString(), http, { "session_abc" }, { }))
    }

    @After
    fun tearDown() = server.close()

    private fun enqueue(body: String, code: Int = 200) {
        val response = MockResponse.Builder()
            .code(code)
            .addHeader("Content-Type", "application/json")
            .body(body)
            .build()
        server.enqueue(response)
    }

    // The local engine's PUT /api/v1/users/me/update with {} (no change), for demo.provider2.
    private val meJson =
        """{"id":3,"email":"demo.provider2@hafiyanjye.test","name":"Aline Uwase","picture":null,""" +
            """"phone":"+250788123402","activeRole":"provider","hasProviderProfile":true,"hasPassword":true,""" +
            """"createdAt":"2026-11-12T16:24:15.054Z","staff":null,"badges":[],"unreadWarnings":[]}"""

    // Trimmed from the local engine's GET /api/v1/notifications (demo.provider1 and demo.seeker).
    private val notificationsJson =
        """{"items":[{"id":797,"type":"org_interest","title":"Inzu Facilities Ltd is interested in you",""" +
            """"body":"Inzu Facilities Ltd would like to talk to you about \"Site electrician\". """ +
            """Accept to share your contact details.","url":"/orgs/interest","readAt":null,""" +
            """"createdAt":"2026-11-17T22:40:36.417Z"},""" +
            """{"id":767,"type":"match.none","title":"Nobody available yet","body":"Nobody offering this """ +
            """service is online near you right now. Try again to search a wider area.","url":"/jobs/4524",""" +
            """"readAt":"2026-11-17T12:11:50.366Z","createdAt":"2026-11-17T12:11:05.398Z"}],"unread":14}"""

    @Test
    fun `notifications decode from the engine's json`() {
        val inbox = HafiJson.decodeFromString(Notifications.serializer(), notificationsJson)
        assertEquals(14, inbox.unread)
        assertEquals(2, inbox.items.size)
        val (unread, read) = inbox.items
        assertEquals(797, unread.id)
        assertEquals("org_interest", unread.type)
        assertEquals("/orgs/interest", unread.url)
        assertTrue(unread.unread)
        assertTrue(unread.body.contains("\"Site electrician\""))
        assertEquals("/jobs/4524", read.url)
        assertFalse(read.unread)
        assertEquals(Instant.parse("2026-11-17T12:11:05.398Z"), read.createdAt)
    }

    @Test
    fun `the account decodes from the engine's json`() {
        val me = HafiJson.decodeFromString(Me.serializer(), meJson)
        assertEquals("Aline Uwase", me.name)
        assertEquals("+250788123402", me.phone)
        assertTrue(me.hasWorkProfile)
        assertTrue(me.hasPassword)
        assertEquals(Countries.byIso("RW") to "788123402", Countries.split(me.phone))
    }

    @Test
    fun `inbox is fetched and marked read with the right calls`() = runTest {
        enqueue("""{"ok":true,"data":$notificationsJson,"meta":{}}""")
        assertEquals(14, api.notifications().unread)
        var sent = server.takeRequest()
        assertEquals("GET", sent.method)
        assertEquals("/api/v1/notifications", sent.url.encodedPath)
        assertEquals("Bearer session_abc", sent.headers["Authorization"])

        enqueue("""{"ok":true,"data":{"items":[],"unread":0},"meta":{}}""")
        api.markRead(listOf(797))
        sent = server.takeRequest()
        assertEquals("PUT", sent.method)
        assertEquals("/api/v1/notifications/read", sent.url.encodedPath)
        assertEquals("""{"ids":[797]}""", sent.body?.utf8())

        enqueue("""{"ok":true,"data":{"items":[],"unread":0},"meta":{}}""")
        assertEquals(0, api.markRead().unread)
        assertEquals("{}", server.takeRequest().body?.utf8())
    }

    @Test
    fun `update, picture removal, avatar upload and deletion send what the engine expects`() = runTest {
        val me = HafiJson.decodeFromString(Me.serializer(), meJson)

        enqueue("""{"ok":true,"data":$meJson,"meta":{}}""")
        val body = ProfileRules.updateBody(me, "Aline U.", "")
        assertNotNull(body)
        assertEquals("Aline Uwase", api.update(body ?: return@runTest).name)
        var sent = server.takeRequest()
        assertEquals("PUT", sent.method)
        assertEquals("/api/v1/users/me/update", sent.url.encodedPath)
        assertEquals("""{"name":"Aline U.","phone":null}""", sent.body?.utf8())

        enqueue("""{"ok":true,"data":$meJson,"meta":{}}""")
        api.removePicture()
        assertEquals("""{"picture":null}""", server.takeRequest().body?.utf8())

        enqueue("""{"ok":true,"data":{"id":"7f3c","url":"/api/media/7f3c"},"meta":{}}""", code = 201)
        assertEquals("/api/media/7f3c", api.uploadAvatar(byteArrayOf(1, 2, 3), 512).url)
        sent = server.takeRequest()
        assertEquals("POST", sent.method)
        assertEquals("/api/media", sent.url.encodedPath)
        assertEquals("avatar", sent.url.queryParameter("kind"))
        assertEquals("512", sent.url.queryParameter("w"))
        assertEquals("512", sent.url.queryParameter("h"))
        assertTrue(sent.headers["Content-Type"].orEmpty().startsWith("image/jpeg"))
        assertEquals(3L, sent.body?.size?.toLong())

        enqueue("""{"ok":true,"data":{"deleted":true},"meta":{}}""")
        assertTrue(api.deleteAccount(ProfileRules.deleteInput(me, "delete", "demo-pass-123")).deleted)
        sent = server.takeRequest()
        assertEquals("POST", sent.method)
        assertEquals("/api/v1/users/me/delete", sent.url.encodedPath)
        assertEquals("""{"confirm":"delete","password":"demo-pass-123"}""", sent.body?.utf8())
    }
}
