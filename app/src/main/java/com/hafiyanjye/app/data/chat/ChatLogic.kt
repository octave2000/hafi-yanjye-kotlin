package com.hafiyanjye.app.data.chat

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/** The engine's limits (core/chat/chat.ts, core/chat/files.ts) and the web's (components/chat). */
object ChatLimits {
    const val MAX_MESSAGE_CHARS = 2000

    /** The composer shows how many characters are left from here on. */
    const val COUNTER_FROM = 1800
    const val MAX_FILE_BYTES = 10L * 1024 * 1024

    /** Photos are shrunk before upload, so only absurd originals are refused. */
    const val MAX_RAW_PHOTO_BYTES = 30L * 1024 * 1024
    const val MAX_FILES_PER_PICK = 10

    /** Longest side and largest size of a photo after resizing (web: resizeToJpeg(file, 2000, 4 MB)). */
    const val PHOTO_MAX_SIDE = 2000
    const val PHOTO_MAX_BYTES = 4 * 1024 * 1024
    const val TYPING_SEND_EVERY_MS = 3_000L
    const val TYPING_SHOW_MS = 4_000L
    const val PRESENCE_EVERY_MS = 20_000L

    /** Document extensions the engine accepts besides photos. */
    val DOCUMENT_EXTENSIONS = setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx")

    /** What the system picker offers (web: ACCEPTED_CHAT_FILES). */
    val PICKER_TYPES = arrayOf(
        "image/jpeg",
        "image/png",
        "image/webp",
        "application/pdf",
        "application/msword",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "application/vnd.ms-excel",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "application/vnd.ms-powerpoint",
        "application/vnd.openxmlformats-officedocument.presentationml.presentation",
    )
}

/**
 * App-wide chat state the web keeps at module level (lib/chat/events.ts): which
 * thread is on screen, so the badge does not count it, and requests to reload
 * the unread count after a thread marks messages read.
 */
object ChatSignals {
    private val _openThread = MutableStateFlow<Int?>(null)

    /** The conversation shown on screen right now, or null. */
    val openThread: StateFlow<Int?> = _openThread.asStateFlow()

    private val _unreadChanged = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** Fires when something on this phone changed what is unread (a read receipt sent). */
    val unreadChanged: SharedFlow<Unit> = _unreadChanged.asSharedFlow()

    fun threadShown(conversationId: Int) {
        _openThread.value = conversationId
    }

    /** Only clears the thread it was asked about, so leaving one thread cannot clear the next one. */
    fun threadHidden(conversationId: Int) {
        _openThread.compareAndSet(conversationId, null)
    }

    fun requestUnreadRefresh() {
        _unreadChanged.tryEmit(Unit)
    }
}

/**
 * Remembers the last [limit] message ids so a message delivered twice (the
 * WebSocket and the event stream both carry chat) is handled once.
 */
class FirstTimeFilter(private val limit: Int = 500) {
    private val seen = LinkedHashSet<Int>()

    /** True the first time [id] is offered. */
    @Synchronized
    fun firstTime(id: Int): Boolean {
        if (!seen.add(id)) return false
        if (seen.size > limit) seen.remove(seen.first())
        return true
    }
}

/**
 * Lets "typing…" go out at most once every [everyMs] while text is being
 * written, and again at once after a message is sent (web composer).
 */
class TypingThrottle(private val everyMs: Long = ChatLimits.TYPING_SEND_EVERY_MS) {
    private var lastSentAt: Long? = null

    /** True when a typing signal should be sent now for [text]. */
    fun shouldSend(text: String, nowMs: Long): Boolean {
        if (text.isBlank()) return false
        val last = lastSentAt
        if (last != null && nowMs - last <= everyMs) return false
        lastSentAt = nowMs
        return true
    }

    fun reset() {
        lastSentAt = null
    }
}

/** Every message once, by id, oldest first; a later copy of the same id wins. */
fun mergeMessages(vararg pages: List<ChatMessage>): List<ChatMessage> {
    val byId = LinkedHashMap<Int, ChatMessage>()
    for (page in pages) {
        for (message in page) byId[message.id] = message
    }
    return byId.values.sortedBy { it.id }
}

/** Id of the newest message the other person sent, the one a read receipt points at; 0 when none. */
fun newestFromOther(messages: List<ChatMessage>, meId: Int): Int =
    messages.lastOrNull { it.senderId != meId }?.id ?: 0

/** Id of my newest message, the only one that shows "Sent" or "Seen"; 0 when none. */
fun myNewestId(messages: List<ChatMessage>, meId: Int): Int =
    messages.lastOrNull { it.senderId == meId }?.id ?: 0

/** Unread messages outside the thread on screen: the Chat tab's badge. */
fun unreadOutside(list: ConversationList, openThread: Int?): Int =
    list.items.filter { it.id != openThread }.sumOf { it.unread }

/** "2.4 MB", "830 KB" (web: formatBytes). */
fun formatBytes(bytes: Long): String =
    if (bytes >= 1024 * 1024) {
        String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
    } else {
        "${maxOf(1L, Math.round(bytes / 1024.0))} KB"
    }

private val clockFormat = DateTimeFormatter.ofPattern("HH:mm", Locale.UK)
private val dayThisYear = DateTimeFormatter.ofPattern("EEE d MMM", Locale.UK)
private val dayOtherYear = DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.UK)
private val shortDay = DateTimeFormatter.ofPattern("d MMM", Locale.UK)

/** Time under a message: "14:05". */
fun messageTime(at: Instant, zone: ZoneId = ZoneId.systemDefault()): String = clockFormat.format(at.atZone(zone))

/** Day separator in a thread: "Today", "Yesterday", "Mon 3 Nov", "Tue 4 Nov 2025". */
fun dayLabel(at: Instant, now: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()): String {
    val day = at.atZone(zone).toLocalDate()
    val today = now.atZone(zone).toLocalDate()
    return when {
        day == today -> "Today"
        day == today.minusDays(1) -> "Yesterday"
        day.year == today.year -> dayThisYear.format(day)
        else -> dayOtherYear.format(day)
    }
}

/** When a conversation last moved, in the list: "14:05" today, "Yesterday", else "3 Nov". */
fun listTime(at: Instant, now: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()): String {
    val day = at.atZone(zone).toLocalDate()
    val today = now.atZone(zone).toLocalDate()
    return when {
        day == today -> messageTime(at, zone)
        day == today.minusDays(1) -> "Yesterday"
        else -> shortDay.format(day)
    }
}

private val urlPattern = Regex("""https?://[^\s<]+[^\s<.,;:!?)\]'"]""")

/** Web links inside a message body, as ranges into it (web: RichText). */
fun linkRanges(text: String): List<IntRange> = urlPattern.findAll(text).map { it.range }.toList()

/** What a conversation row says about the newest message (web: Preview). */
fun previewText(conversation: Conversation, meId: Int): String {
    val message = conversation.lastMessage ?: return "No messages yet"
    val prefix = if (message.senderId == meId) "You: " else ""
    val text = when (message.kind) {
        MessageKind.Image -> "Photo"
        MessageKind.File -> message.attachment?.name ?: "File"
        MessageKind.Text -> message.body
    }
    return prefix + text
}

/** A short line for a new message that arrives while another screen is open (web toast). */
fun arrivalText(message: ChatMessage): String {
    val text = when (message.kind) {
        MessageKind.Image -> "Sent a photo"
        MessageKind.File -> "Sent a file: ${message.attachment?.name ?: "document"}"
        MessageKind.Text -> message.body
    }
    return if (text.length > 90) text.take(89) + "…" else text
}

/** True for a document name the engine accepts (photos are checked by type instead). */
fun isAcceptedDocument(name: String): Boolean =
    name.substringAfterLast('.', "").lowercase() in ChatLimits.DOCUMENT_EXTENSIONS
