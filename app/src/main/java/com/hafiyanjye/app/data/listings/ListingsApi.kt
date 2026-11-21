package com.hafiyanjye.app.data.listings

import com.hafiyanjye.app.data.api.ApiClient
import com.hafiyanjye.app.data.api.Me
import com.hafiyanjye.app.data.rentals.ListingDto
import kotlinx.serialization.serializer
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okio.Buffer
import okio.BufferedSink
import okio.ForwardingSink
import okio.Sink
import okio.buffer

/** The owner's side of rentals: their listings, saving one, pausing it, and its photos. */
class ListingsApi(private val client: ApiClient) {

    /** Every listing of the signed-in owner, newest change first, paused ones included. */
    suspend fun mine(): List<ListingDto> = client.get("/api/v1/listings/mine")

    /** One listing; paused ones only for their owner. `mine` and `exact` tell the owner apart. */
    suspend fun listing(id: Int): ListingDto = client.get("/api/v1/listings/$id")

    /** Creates (no id) or updates a listing and answers it as saved. New listings go live at once. */
    suspend fun save(input: SaveListingInput): ListingDto = client.post("/api/v1/listings/save", input)

    /** Pauses, re-activates or deletes one of the owner's listings. */
    suspend fun setStatus(id: Int, status: ListingStatusChange) {
        client.post<ListingStatusInput, ListingStatusResult>(
            "/api/v1/listings/status",
            ListingStatusInput(id, status),
        )
    }

    /** Saves the owner's phone (the engine needs one before a first listing) and answers the updated account. */
    suspend fun savePhone(phone: String): Me = client.put("/api/v1/users/me/update", PhoneInput(phone))

    /**
     * Uploads one resized JPEG as a listing photo. [onProgress] gets the share
     * sent so far (0 to 1), so each photo can show its own progress.
     */
    suspend fun uploadPhoto(
        jpeg: ByteArray,
        width: Int,
        height: Int,
        onProgress: (Float) -> Unit = {},
    ): UploadedPhoto {
        val query = mapOf("kind" to "listing", "w" to width, "h" to height)
        val body = ProgressBody(jpeg, "image/jpeg".toMediaType(), onProgress)
        val request = client.newRequest("/api/media", query).post(body).build()
        return client.execute(request, serializer<UploadedPhoto>())
    }
}

/** A byte body that reports how much of it OkHttp has written to the socket. */
private class ProgressBody(
    private val bytes: ByteArray,
    private val type: MediaType,
    private val onProgress: (Float) -> Unit,
) : RequestBody() {

    override fun contentType(): MediaType = type

    override fun contentLength(): Long = bytes.size.toLong()

    override fun writeTo(sink: BufferedSink) {
        val counting = CountingSink(sink, bytes.size.toLong(), onProgress).buffer()
        counting.write(bytes)
        counting.flush()
    }
}

/** Counts bytes on their way to the socket for [ProgressBody]. */
private class CountingSink(
    delegate: Sink,
    private val total: Long,
    private val onProgress: (Float) -> Unit,
) : ForwardingSink(delegate) {
    private var written = 0L

    override fun write(source: Buffer, byteCount: Long) {
        super.write(source, byteCount)
        written += byteCount
        onProgress(if (total == 0L) 1f else written.toFloat() / total)
    }
}
