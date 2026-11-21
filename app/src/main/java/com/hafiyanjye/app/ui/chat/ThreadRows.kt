package com.hafiyanjye.app.ui.chat

import com.hafiyanjye.app.data.chat.ChatMessage
import com.hafiyanjye.app.data.chat.dayLabel
import com.hafiyanjye.app.data.chat.myNewestId
import java.time.Instant
import java.time.ZoneId

/** Where a message I wrote stands before the engine has stored it. */
enum class PendingStatus { Sending, Failed }

/**
 * A message shown at once while it is on its way (web: Pending). Text has a
 * [body]; a photo or document has a [file]. [tempId] is negative and unique.
 */
data class PendingMessage(
    val tempId: Long,
    val body: String,
    val file: PickedFile?,
    val status: PendingStatus,
    val error: String?,
    val createdAt: Instant,
)

/** "Sent" until the other person has read up to the message, then "Seen". */
enum class Receipt { Sent, Seen }

/** One line of the thread, oldest first. */
sealed interface ThreadRow {
    /** Stable LazyColumn key. */
    val key: String

    data class Day(val label: String, override val key: String) : ThreadRow

    data class Message(val message: ChatMessage, val mine: Boolean, val receipt: Receipt?) : ThreadRow {
        override val key: String get() = "m${message.id}"
    }

    data class Pending(val pending: PendingMessage) : ThreadRow {
        override val key: String get() = "p${pending.tempId}"
    }
}

/**
 * The thread as the web draws it: a day separator whenever the day changes,
 * the receipt only under my newest message and only when nothing of mine is
 * still on its way, then the messages still being sent.
 */
fun buildThreadRows(
    messages: List<ChatMessage>,
    pending: List<PendingMessage>,
    meId: Int,
    otherReadId: Int,
    now: Instant = Instant.now(),
    zone: ZoneId = ZoneId.systemDefault(),
): List<ThreadRow> {
    val rows = ArrayList<ThreadRow>(messages.size + pending.size + 4)
    val myNewest = myNewestId(messages, meId)
    var lastDay: String? = null
    for (message in messages) {
        val day = dayLabel(message.createdAt, now, zone)
        if (day != lastDay) {
            rows += ThreadRow.Day(day, "d${message.id}")
            lastDay = day
        }
        val mine = message.senderId == meId
        val receipt = when {
            !mine || message.id != myNewest || pending.isNotEmpty() -> null
            otherReadId >= message.id -> Receipt.Seen
            else -> Receipt.Sent
        }
        rows += ThreadRow.Message(message, mine, receipt)
    }
    pending.forEach { rows += ThreadRow.Pending(it) }
    return rows
}
