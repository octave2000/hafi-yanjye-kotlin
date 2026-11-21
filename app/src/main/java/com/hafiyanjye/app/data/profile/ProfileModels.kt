package com.hafiyanjye.app.data.profile

import com.hafiyanjye.app.data.api.Me
import com.hafiyanjye.app.data.api.Timestamp
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Engine: NotificationDto. [url] is a web path ("/jobs/12"); see Routes.fromWebPath. */
@Serializable
data class Notification(
    val id: Int,
    val type: String = "",
    val title: String = "",
    val body: String = "",
    val url: String? = null,
    val readAt: Timestamp? = null,
    val createdAt: Timestamp,
) {
    val unread: Boolean get() = readAt == null
}

/** Engine: NotificationsDto. The latest 50, newest first, and how many are unread in all. */
@Serializable
data class Notifications(val items: List<Notification> = emptyList(), val unread: Int = 0)

/** `ids` left out marks every unread notification as read. */
@Serializable
data class NotificationsReadInput(val ids: List<Int>? = null)

/** Engine: input of POST /users/me/delete; the password only for accounts that have one. */
@Serializable
data class DeleteAccountInput(val confirm: String, val password: String? = null)

/** Engine: answer of POST /users/me/delete. */
@Serializable
data class DeleteAccountResult(val deleted: Boolean = false)

/** What POST /api/media answers. */
@Serializable
data class MediaUpload(val id: String, val url: String)

/** The profile form's and account deletion's rules, kept apart from the UI so they are tested. */
object ProfileRules {
    const val MAX_NAME = 100

    /** What the person types to confirm deleting the account (engine: DELETE_CONFIRM_WORD). */
    const val DELETE_WORD = "DELETE"

    fun nameProblem(name: String): String? = when {
        name.isBlank() -> "Enter your name."
        name.trim().length > MAX_NAME -> "That name is too long (at most $MAX_NAME characters)."
        else -> null
    }

    /** The web's rule: the word, in any case, and the password when the account has one. */
    fun canDelete(me: Me, confirm: String, password: String): Boolean =
        confirm.trim().uppercase() == DELETE_WORD && (!me.hasPassword || password.isNotEmpty())

    fun deleteInput(me: Me, confirm: String, password: String) =
        DeleteAccountInput(confirm.trim(), password.takeIf { me.hasPassword })

    /**
     * The body of PUT /users/me/update for an edited form: only what changed.
     * An emptied phone is sent as an explicit null, which removes it. Null when
     * nothing changed.
     */
    fun updateBody(me: Me, name: String, phone: String): JsonObject? {
        val cleanName = name.trim()
        val cleanPhone = phone.trim()
        val body = buildJsonObject {
            if (cleanName.isNotEmpty() && cleanName != me.name.orEmpty()) put("name", cleanName)
            if (cleanPhone != me.phone.orEmpty()) {
                if (cleanPhone.isEmpty()) put("phone", JsonNull) else put("phone", cleanPhone)
            }
        }
        return body.takeIf { it.isNotEmpty() }
    }

    /** Removing the picture: the engine accepts only an explicit null. */
    val removePictureBody: JsonObject = buildJsonObject { put("picture", JsonNull) }
}
