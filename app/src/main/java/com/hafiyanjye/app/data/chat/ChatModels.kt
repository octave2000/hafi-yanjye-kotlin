package com.hafiyanjye.app.data.chat

import com.hafiyanjye.app.data.api.Timestamp
import com.hafiyanjye.app.ui.location.LocationSource
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** What a message carries (engine MessageKind). */
@Serializable
enum class MessageKind {
    @SerialName("text")
    Text,

    @SerialName("image")
    Image,

    @SerialName("file")
    File,
}

/** A photo or document in a message; [url] is private to the two people in the chat. */
@Serializable
data class ChatAttachment(
    val url: String,
    val name: String,
    val size: Long = 0,
    val mime: String = "application/octet-stream",
    val width: Int? = null,
    val height: Int? = null,
) {
    val isImage: Boolean get() = mime.startsWith("image/")
}

/** One chat message (engine MessageDto). */
@Serializable
data class ChatMessage(
    val id: Int,
    val conversationId: Int,
    val senderId: Int,
    val kind: MessageKind = MessageKind.Text,
    val body: String = "",
    val attachment: ChatAttachment? = null,
    val createdAt: Timestamp,
)

/** The other person in a conversation; [phone] is only set where it is already shared. */
@Serializable
data class ChatPerson(
    val userId: Int,
    val name: String,
    val picture: String? = null,
    val phone: String? = null,
)

/** Where a conversation was opened from. */
@Serializable
enum class ChatContextType {
    @SerialName("listing")
    Listing,

    @SerialName("job")
    Job,
}

/**
 * What the conversation is about: a rental listing (with [listingKind] "car" or
 * "home") or a job ([mine] is true when I posted it, so I am the job provider).
 */
@Serializable
data class ChatContext(
    val type: ChatContextType,
    val id: Int,
    val title: String,
    @SerialName("kind") val listingKind: String? = null,
    val photo: String? = null,
    val mine: Boolean = false,
)

/** A service the other person offers, for the Hire sheet. */
@Serializable
data class ChatCategory(val slug: String, val nameEn: String)

/**
 * Set when the chat is not about a job and the other person has a work
 * profile: they can be hired from the thread. [workerId] is their work profile id.
 */
@Serializable
data class ChatHire(
    @SerialName("providerId") val workerId: Int,
    val displayName: String,
    val categories: List<ChatCategory> = emptyList(),
)

/** One row of the conversation list, also the header of a thread. */
@Serializable
data class Conversation(
    val id: Int,
    val other: ChatPerson,
    val context: ChatContext? = null,
    val hire: ChatHire? = null,
    val lastMessage: ChatMessage? = null,
    val unread: Int = 0,
    /** Newest message of mine the other person has read. */
    val otherReadId: Int? = null,
    val updatedAt: Timestamp,
)

/** GET /api/v1/chat. */
@Serializable
data class ConversationList(val items: List<Conversation> = emptyList(), val unread: Int = 0)

/** GET /api/v1/chat/{id}/messages: one page, oldest first. */
@Serializable
data class MessagePage(
    val conversation: Conversation,
    val items: List<ChatMessage> = emptyList(),
    val hasMore: Boolean = false,
)

/** POST /api/v1/chat/open: exactly one of the ids. [workerId] is a work profile id. */
@Serializable
data class ChatOpenInput(
    @SerialName("providerId") val workerId: Int? = null,
    val jobId: Int? = null,
    val listingId: Int? = null,
) {
    /** The engine needs exactly one positive id. */
    fun isValid(): Boolean {
        val ids = listOfNotNull(workerId, jobId, listingId)
        return ids.size == 1 && ids.single() > 0
    }
}

/** POST /api/v1/jobs/direct, sent from the thread's Hire sheet. */
@Serializable
data class ChatHireInput(
    @SerialName("providerId") val workerId: Int,
    val categorySlug: String,
    val text: String,
    val lat: Double,
    val lng: Double,
    val addressLabel: String? = null,
    val locationSource: LocationSource,
    val budgetRwf: Int? = null,
)

/** The job a direct hire created. */
@Serializable
data class ChatHireResult(val jobId: Int, val sessionId: Int? = null)

@Serializable
internal data class OpenedChat(val id: Int)

@Serializable
internal data class SendInput(val body: String)

@Serializable
internal data class ReadInput(val messageId: Int)

@Serializable
internal data class ChatOk(val ok: Boolean = true)

@Serializable
internal data class ChatPresence(val online: List<Int> = emptyList())
