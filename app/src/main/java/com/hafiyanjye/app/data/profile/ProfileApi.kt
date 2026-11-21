package com.hafiyanjye.app.data.profile

import com.hafiyanjye.app.data.api.ApiClient
import com.hafiyanjye.app.data.api.Me
import kotlinx.serialization.json.JsonObject

/** The account's own endpoints: details, picture, deletion and the notifications inbox. */
class ProfileApi(private val client: ApiClient) {

    /** [body] comes from [ProfileRules.updateBody] or [ProfileRules.removePictureBody]. */
    suspend fun update(body: JsonObject): Me = client.put("/api/v1/users/me/update", body)

    suspend fun removePicture(): Me = update(ProfileRules.removePictureBody)

    /** Stores a square JPEG; the engine makes it the account's picture straight away. */
    suspend fun uploadAvatar(jpeg: ByteArray, side: Int): MediaUpload =
        client.upload("/api/media", jpeg, "image/jpeg", mapOf("kind" to "avatar", "w" to side, "h" to side))

    /** Deletes the account for good and ends every session. */
    suspend fun deleteAccount(input: DeleteAccountInput): DeleteAccountResult =
        client.post("/api/v1/users/me/delete", input)

    suspend fun notifications(): Notifications = client.get("/api/v1/notifications")

    /** Marks these as read, or every unread one when [ids] is null. Answers the fresh list. */
    suspend fun markRead(ids: List<Int>? = null): Notifications =
        client.put("/api/v1/notifications/read", NotificationsReadInput(ids))
}
