package com.hafiyanjye.app.data.chat

import com.hafiyanjye.app.data.api.ApiClient

/** The engine's chat endpoints (engine/src/api/v1/chat and the raw file routes in core/chat/files.ts). */
class ChatApi(private val client: ApiClient) {

    /** My conversations with at least one message, latest first, with unread counts. */
    suspend fun conversations(): ConversationList = client.get("/api/v1/chat")

    /** Starts or reopens the chat behind a listing, a match or a work profile; returns its id. */
    suspend fun open(input: ChatOpenInput): Int {
        require(input.isValid()) { "Choose one person, job or listing to chat about." }
        return client.post<ChatOpenInput, OpenedChat>("/api/v1/chat/open", input).id
    }

    /** The newest page, or the page of messages older than [before] (a message id). */
    suspend fun messages(conversationId: Int, before: Int? = null): MessagePage =
        client.get("/api/v1/chat/$conversationId/messages", mapOf("before" to before))

    suspend fun send(conversationId: Int, body: String): ChatMessage =
        client.post("/api/v1/chat/$conversationId/send", SendInput(body))

    /** Marks everything up to [messageId] as read; the sender then sees "Seen". */
    suspend fun markRead(conversationId: Int, messageId: Int) {
        client.post<ReadInput, ChatOk>("/api/v1/chat/$conversationId/read", ReadInput(messageId))
    }

    /** Tells the other person I am typing (the engine drops more than one every 2 s). */
    suspend fun typing(conversationId: Int) {
        client.post<ChatOk>("/api/v1/chat/$conversationId/typing")
    }

    /** Who of [userIds] is online now; the engine only answers for people I chat with. */
    suspend fun presence(userIds: Collection<Int>): Set<Int> {
        val ids = userIds.distinct().sorted()
        if (ids.isEmpty()) return emptySet()
        val presence = client.get<ChatPresence>("/api/v1/chat/presence", mapOf("ids" to ids.joinToString(",")))
        return presence.online.toSet()
    }

    /** Sends a photo or document as a message; the engine checks the bytes and creates the message. */
    suspend fun sendFile(conversationId: Int, upload: ChatUpload, caption: String = ""): ChatMessage =
        client.upload(
            "/api/chat/files",
            upload.bytes,
            upload.mime,
            mapOf(
                "conversation" to conversationId,
                "name" to upload.name,
                "caption" to caption.trim().ifEmpty { null },
                "w" to upload.width,
                "h" to upload.height,
            ),
        )

    /** The bytes of an attachment; only chat file paths are accepted so a message cannot point elsewhere. */
    suspend fun download(attachment: ChatAttachment): ByteArray {
        require(isChatFilePath(attachment.url)) { "This file can't be opened." }
        return client.download(attachment.url, mapOf("download" to "1"))
    }

    /** Sends a direct hire request to the person in the chat (the Hire button in the thread header). */
    suspend fun hire(input: ChatHireInput): ChatHireResult = client.post("/api/v1/jobs/direct", input)
}

/** A file ready to upload: photos already resized to JPEG. */
class ChatUpload(
    val bytes: ByteArray,
    val mime: String,
    val name: String,
    val width: Int? = null,
    val height: Int? = null,
)

/** True for "/api/chat/files/<uuid>", the only place attachments live. */
fun isChatFilePath(url: String): Boolean = Regex("^/api/chat/files/[0-9a-f-]{36}$").matches(url)
