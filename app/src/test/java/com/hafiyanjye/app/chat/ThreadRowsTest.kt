package com.hafiyanjye.app.chat

import com.hafiyanjye.app.data.api.HafiJson
import com.hafiyanjye.app.data.chat.MessagePage
import com.hafiyanjye.app.ui.chat.PendingMessage
import com.hafiyanjye.app.ui.chat.PendingStatus
import com.hafiyanjye.app.ui.chat.Receipt
import com.hafiyanjye.app.ui.chat.ThreadRow
import com.hafiyanjye.app.ui.chat.buildThreadRows
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** How a thread is laid out: day separators, the one receipt, messages on their way at the end. */
class ThreadRowsTest {
    private val utc = ZoneOffset.UTC
    private val now = Instant.parse("2026-11-17T23:40:00Z")
    private val page = HafiJson.decodeFromString(MessagePage.serializer(), ChatFixtures.THREAD)

    private fun pending(id: Long) = PendingMessage(id, "Thank you", null, PendingStatus.Sending, null, now)

    @Test
    fun `a separator starts each day`() {
        val rows = buildThreadRows(page.items, emptyList(), meId = 1, otherReadId = 0, now = now, zone = utc)
        val labels = rows.filterIsInstance<ThreadRow.Day>().map { it.label }
        assertEquals(listOf("Yesterday", "Today"), labels)
        assertEquals(listOf("d54", "m54", "d56", "m56", "m57"), rows.map { it.key })
    }

    @Test
    fun `only my newest message carries Sent, then Seen once they read it`() {
        val asJobSeeker = buildThreadRows(page.items, emptyList(), 19, otherReadId = 56, now = now, zone = utc)
        val mine = asJobSeeker.filterIsInstance<ThreadRow.Message>()
        assertEquals(listOf(null, null, Receipt.Sent), mine.map { it.receipt })

        val seen = buildThreadRows(page.items, emptyList(), meId = 19, otherReadId = 57, now = now, zone = utc)
        assertEquals(Receipt.Seen, seen.filterIsInstance<ThreadRow.Message>().last().receipt)

        val asHirer = buildThreadRows(page.items, emptyList(), meId = 1, otherReadId = 56, now = now, zone = utc)
        val receipts = asHirer.filterIsInstance<ThreadRow.Message>().map { it.receipt }
        assertEquals(listOf(null, Receipt.Seen, null), receipts)
    }

    @Test
    fun `messages on their way come last and hide the receipt`() {
        val rows = buildThreadRows(page.items, listOf(pending(-1), pending(-2)), 1, 57, now, utc)
        assertEquals(listOf("p-1", "p-2"), rows.takeLast(2).map { it.key })
        rows.filterIsInstance<ThreadRow.Message>().forEach { assertNull(it.receipt) }
    }
}
