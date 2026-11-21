package com.hafiyanjye.app.chat

import com.hafiyanjye.app.data.api.HafiJson
import com.hafiyanjye.app.data.chat.ChatMessage
import com.hafiyanjye.app.data.chat.ConversationList
import com.hafiyanjye.app.data.chat.FirstTimeFilter
import com.hafiyanjye.app.data.chat.MessageKind
import com.hafiyanjye.app.data.chat.TypingThrottle
import com.hafiyanjye.app.data.chat.arrivalText
import com.hafiyanjye.app.data.chat.dayLabel
import com.hafiyanjye.app.data.chat.formatBytes
import com.hafiyanjye.app.data.chat.isAcceptedDocument
import com.hafiyanjye.app.data.chat.isChatFilePath
import com.hafiyanjye.app.data.chat.linkRanges
import com.hafiyanjye.app.data.chat.listTime
import com.hafiyanjye.app.data.chat.mergeMessages
import com.hafiyanjye.app.data.chat.myNewestId
import com.hafiyanjye.app.data.chat.newestFromOther
import com.hafiyanjye.app.data.chat.previewText
import com.hafiyanjye.app.data.chat.unreadOutside
import com.hafiyanjye.app.ui.chat.PickedFile
import com.hafiyanjye.app.ui.chat.checkPicked
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The chat rules that do not need a screen: merging, receipts, the badge, limits and labels. */
class ChatLogicTest {
    private val utc = ZoneOffset.UTC
    private val list = HafiJson.decodeFromString(ConversationList.serializer(), ChatFixtures.CONVERSATIONS)

    private fun message(id: Int, sender: Int, body: String = "Message $id") =
        ChatMessage(id, 10, sender, MessageKind.Text, body, createdAt = Instant.parse("2026-11-17T08:00:00Z"))

    @Test
    fun `a message delivered live and in a reload shows once, in id order`() {
        val older = listOf(message(1, 19), message(2, 1))
        val newest = listOf(message(2, 1), message(3, 19))
        val live = listOf(message(3, 19, "edited copy"), message(4, 1))
        val merged = mergeMessages(older, newest, live)
        assertEquals(listOf(1, 2, 3, 4), merged.map { it.id })
        assertEquals("edited copy", merged[2].body)
    }

    @Test
    fun `read receipts point at the newest message of each side`() {
        val messages = listOf(message(54, 1), message(56, 1), message(57, 19))
        assertEquals(57, newestFromOther(messages, meId = 1))
        assertEquals(56, myNewestId(messages, meId = 1))
        assertEquals(0, newestFromOther(messages.take(2), meId = 1))
    }

    @Test
    fun `the badge leaves out the thread on screen`() {
        assertEquals(3, unreadOutside(list, openThread = null))
        assertEquals(2, unreadOutside(list, openThread = 10))
        assertEquals(1, unreadOutside(list, openThread = 9))
    }

    @Test
    fun `each message id is handled once, and the memory stays bounded`() {
        val filter = FirstTimeFilter(limit = 2)
        assertTrue(filter.firstTime(1))
        assertFalse(filter.firstTime(1))
        assertTrue(filter.firstTime(2))
        assertTrue(filter.firstTime(3))
        assertTrue(filter.firstTime(1))
    }

    @Test
    fun `typing goes out at most every three seconds and again after a send`() {
        val throttle = TypingThrottle(everyMs = 3_000)
        assertFalse(throttle.shouldSend("   ", 0))
        assertTrue(throttle.shouldSend("H", 1_000))
        assertFalse(throttle.shouldSend("He", 2_000))
        assertFalse(throttle.shouldSend("Hel", 4_000))
        assertTrue(throttle.shouldSend("Hell", 4_001))
        throttle.reset()
        assertTrue(throttle.shouldSend("N", 4_002))
    }

    @Test
    fun `list previews say who wrote last and what kind it was`() {
        assertEquals("Yes, Saturday 9:00 works. Chemistry and maths, 2 hours.", previewText(list.items[0], meId = 1))
        assertEquals("You: quote.pdf", previewText(list.items[1], meId = 1))
        assertEquals("You: hello", previewText(list.items[2], meId = 1))
        val photo = list.items[1].copy(lastMessage = list.items[1].lastMessage?.copy(kind = MessageKind.Image))
        assertEquals("You: Photo", previewText(photo, meId = 1))
        assertEquals("No messages yet", previewText(list.items[0].copy(lastMessage = null), meId = 1))
    }

    @Test
    fun `arrival toasts are short and name files`() {
        assertEquals("Sent a photo", arrivalText(message(1, 19).copy(kind = MessageKind.Image)))
        assertEquals("a".repeat(89) + "…", arrivalText(message(1, 19, "a".repeat(120))))
        assertEquals("Sent a file: quote.pdf", arrivalText(list.items[1].lastMessage ?: return))
    }

    @Test
    fun `day and list labels read like the web`() {
        val now = Instant.parse("2026-11-17T23:40:00Z")
        assertEquals("Today", dayLabel(Instant.parse("2026-11-17T22:31:27Z"), now, utc))
        assertEquals("Yesterday", dayLabel(Instant.parse("2026-11-16T10:36:20Z"), now, utc))
        assertEquals("Sun 15 Nov", dayLabel(Instant.parse("2026-11-15T20:23:09Z"), now, utc))
        assertEquals("Thu 13 Nov 2025", dayLabel(Instant.parse("2025-11-13T10:00:00Z"), now, utc))
        assertEquals("22:31", listTime(Instant.parse("2026-11-17T22:31:27Z"), now, utc))
        assertEquals("Yesterday", listTime(Instant.parse("2026-11-16T10:36:20Z"), now, utc))
        assertEquals("14 Nov", listTime(Instant.parse("2026-11-14T13:28:06Z"), now, utc))
    }

    @Test
    fun `sizes and links are found the way the web finds them`() {
        assertEquals("1 KB", formatBytes(44))
        assertEquals("5 KB", formatBytes(4816))
        assertEquals("2.4 MB", formatBytes(2_516_582))
        val text = "Photos here: https://hafiyanjye.rw/rentals/75. Call me."
        val ranges = linkRanges(text)
        assertEquals(listOf("https://hafiyanjye.rw/rentals/75"), ranges.map { text.substring(it) })
        assertTrue(linkRanges("no links").isEmpty())
    }

    @Test
    fun `only chat file paths and accepted documents pass`() {
        assertTrue(isChatFilePath("/api/chat/files/747f6d23-b24c-4d5b-8079-2ed689c0b058"))
        assertFalse(isChatFilePath("/api/chat/files/../v1/auth/me"))
        assertFalse(isChatFilePath("https://example.com/api/chat/files/747f6d23-b24c-4d5b-8079-2ed689c0b058"))
        assertTrue(isAcceptedDocument("Lease.DOCX"))
        assertFalse(isAcceptedDocument("setup.exe"))
        assertFalse(isAcceptedDocument("pdf"))
    }

    @Test
    fun `picks over the size limits or past ten are left out with a reason`() {
        val photo = PickedFile("content://p/1", "kitchen.jpg", "image/jpeg", 12L * 1024 * 1024)
        val bigPdf = PickedFile("content://p/2", "plans.pdf", "application/pdf", 11L * 1024 * 1024)
        val first = checkPicked(listOf(photo, bigPdf))
        assertEquals(listOf(photo), first.sending)
        assertEquals("\"plans.pdf\" is too large (documents up to 10 MB), so it wasn't sent.", first.problem)

        val many = (1..12).map { PickedFile("content://p/$it", "doc$it.pdf", "application/pdf", 1_000) }
        val second = checkPicked(many)
        assertEquals(10, second.sending.size)
        assertEquals("You can send up to 10 files at once. The first 10 are on their way.", second.problem)

        assertNull(checkPicked(listOf(photo)).problem)
    }
}
