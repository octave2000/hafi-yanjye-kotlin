package com.hafiyanjye.app.chat

import com.hafiyanjye.app.data.api.ApiClient
import com.hafiyanjye.app.data.api.ApiException
import com.hafiyanjye.app.data.chat.ChatApi
import com.hafiyanjye.app.data.chat.ChatAttachment
import com.hafiyanjye.app.data.chat.ChatContextType
import com.hafiyanjye.app.data.chat.ChatOpenInput
import com.hafiyanjye.app.data.chat.ChatUpload
import com.hafiyanjye.app.data.chat.MessageKind
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/** Paths, bodies and decoding of the chat endpoints, against the engine's real answers. */
class ChatApiTest {
    private val server = MockWebServer()
    private lateinit var api: ChatApi

    @Before
    fun setUp() {
        server.start()
        api = ChatApi(ApiClient(server.url("/").toString(), OkHttpClient(), { "session_test" }))
    }

    @After
    fun tearDown() = server.close()

    private fun answer(body: String, code: Int = 200) {
        server.enqueue(
            MockResponse.Builder()
                .code(code)
                .addHeader("Content-Type", "application/json")
                .body(body)
                .build(),
        )
    }

    @Test
    fun `conversation list decodes context, hire and attachments`() = runTest {
        answer(ChatFixtures.envelope(ChatFixtures.CONVERSATIONS))
        val list = api.conversations()
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/api/v1/chat", request.url.encodedPath)
        assertEquals("Bearer session_test", request.headers["Authorization"])

        assertEquals(3, list.unread)
        val kevin = list.items[0]
        assertNull(kevin.context)
        assertEquals(18, kevin.hire?.workerId)
        assertEquals(listOf("tutor", "it_repair"), kevin.hire?.categories?.map { it.slug })
        val rental = list.items[1]
        assertEquals(ChatContextType.Listing, rental.context?.type)
        assertEquals("car", rental.context?.listingKind)
        assertEquals("+250788000101", rental.other.phone)
        assertEquals(MessageKind.File, rental.lastMessage?.kind)
        assertEquals("quote.pdf", rental.lastMessage?.attachment?.name)
        val job = list.items[2]
        assertEquals(ChatContextType.Job, job.context?.type)
        assertTrue(job.context?.mine == true)
        assertNull(job.otherReadId)
    }

    @Test
    fun `thread page reads oldest first and pages back with before`() = runTest {
        answer(ChatFixtures.envelope(ChatFixtures.THREAD))
        answer(ChatFixtures.envelope(ChatFixtures.THREAD_ATTACHMENTS))
        val page = api.messages(10)
        assertEquals("/api/v1/chat/10/messages", server.takeRequest().url.encodedPath)
        assertEquals(listOf(54, 56, 57), page.items.map { it.id })
        assertEquals(57, page.conversation.otherReadId)
        assertFalse(page.hasMore)

        val older = api.messages(9, before = 54)
        val request = server.takeRequest()
        assertEquals("/api/v1/chat/9/messages", request.url.encodedPath)
        assertEquals("54", request.url.queryParameter("before"))
        assertTrue(older.hasMore)
        assertTrue(older.items[0].attachment?.isImage == true)
    }

    @Test
    fun `open sends the work profile id as providerId`() = runTest {
        answer(ChatFixtures.envelope("""{"id":10}"""))
        assertEquals(10, api.open(ChatOpenInput(workerId = 18)))
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/chat/open", request.url.encodedPath)
        assertEquals("""{"providerId":18}""", request.body?.utf8())
    }

    @Test
    fun `open refuses zero or several targets before asking the engine`() = runTest {
        assertFalse(ChatOpenInput().isValid())
        assertFalse(ChatOpenInput(workerId = 0).isValid())
        assertFalse(ChatOpenInput(workerId = 4, jobId = 5).isValid())
        assertTrue(ChatOpenInput(listingId = 6).isValid())
        try {
            api.open(ChatOpenInput(jobId = 1, listingId = 2))
            fail("Expected a refusal")
        } catch (_: IllegalArgumentException) {
            assertEquals(0, server.requestCount)
        }
    }

    @Test
    fun `send, read and typing post to the conversation`() = runTest {
        answer(ChatFixtures.envelope(ChatFixtures.SENT))
        answer(ChatFixtures.envelope("""{"ok":true}"""))
        answer(ChatFixtures.envelope("""{"ok":true}"""))
        val sent = api.send(10, "Yes, Saturday 9:00 works.")
        val send = server.takeRequest()
        assertEquals("/api/v1/chat/10/send", send.url.encodedPath)
        assertEquals("""{"body":"Yes, Saturday 9:00 works."}""", send.body?.utf8())
        assertEquals(57, sent.id)

        api.markRead(10, 57)
        val read = server.takeRequest()
        assertEquals("/api/v1/chat/10/read", read.url.encodedPath)
        assertEquals("""{"messageId":57}""", read.body?.utf8())

        api.typing(10)
        assertEquals("/api/v1/chat/10/typing", server.takeRequest().url.encodedPath)
    }

    @Test
    fun `presence asks once for sorted distinct ids and skips an empty list`() = runTest {
        assertEquals(emptySet<Int>(), api.presence(emptyList()))
        assertEquals(0, server.requestCount)
        answer(ChatFixtures.envelope("""{"online":[427]}"""))
        assertEquals(setOf(427), api.presence(listOf(430, 427, 19, 427)))
        val request = server.takeRequest()
        assertEquals("/api/v1/chat/presence", request.url.encodedPath)
        assertEquals("19,427,430", request.url.queryParameter("ids"))
    }

    @Test
    fun `a file is posted raw with its name, caption and size`() = runTest {
        answer(ChatFixtures.envelope(ChatFixtures.SENT), code = 201)
        val upload = ChatUpload(byteArrayOf(1, 2, 3), "image/jpeg", "kitchen.jpg", 1600, 1200)
        api.sendFile(9, upload, caption = "  The sink  ")
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/chat/files", request.url.encodedPath)
        assertEquals("9", request.url.queryParameter("conversation"))
        assertEquals("kitchen.jpg", request.url.queryParameter("name"))
        assertEquals("The sink", request.url.queryParameter("caption"))
        assertEquals("1600", request.url.queryParameter("w"))
        assertEquals("1200", request.url.queryParameter("h"))
        assertEquals("image/jpeg", request.headers["Content-Type"])
        assertEquals(3L, request.body?.size?.toLong())
    }

    @Test
    fun `a document without a caption sends no caption or size`() = runTest {
        answer(ChatFixtures.envelope(ChatFixtures.SENT), code = 201)
        api.sendFile(9, ChatUpload(byteArrayOf(0x25), "application/pdf", "quote.pdf"))
        val request = server.takeRequest()
        assertNull(request.url.queryParameter("caption"))
        assertNull(request.url.queryParameter("w"))
    }

    @Test
    fun `download only follows chat file paths`() = runTest {
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .addHeader("Content-Type", "application/pdf")
                .body("%PDF-")
                .build(),
        )
        val pdf = ChatAttachment("/api/chat/files/747f6d23-b24c-4d5b-8079-2ed689c0b058", "quote.pdf", 5)
        assertEquals("%PDF-", api.download(pdf).toString(Charsets.UTF_8))
        val request = server.takeRequest()
        assertEquals("/api/chat/files/747f6d23-b24c-4d5b-8079-2ed689c0b058", request.url.encodedPath)
        assertEquals("1", request.url.queryParameter("download"))

        try {
            api.download(pdf.copy(url = "/api/v1/auth/me"))
            fail("Expected a refusal")
        } catch (_: IllegalArgumentException) {
            assertEquals(1, server.requestCount)
        }
    }

    @Test
    fun `a chat that is not mine fails with the engine's words`() = runTest {
        answer(ChatFixtures.NOT_FOUND, code = 404)
        try {
            api.messages(999999)
            fail("Expected an ApiException")
        } catch (error: ApiException) {
            assertEquals(404, error.status)
            assertEquals("Conversation not found", error.message)
        }
    }
}
