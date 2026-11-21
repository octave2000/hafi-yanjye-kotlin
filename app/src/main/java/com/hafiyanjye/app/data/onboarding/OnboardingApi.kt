package com.hafiyanjye.app.data.onboarding

import com.hafiyanjye.app.AppContainer
import com.hafiyanjye.app.BuildConfig
import com.hafiyanjye.app.data.api.ApiClient
import com.hafiyanjye.app.data.pings.AvailabilityInput
import com.hafiyanjye.app.data.pings.WorkCategory
import com.hafiyanjye.app.data.pings.WorkProfile
import kotlinx.serialization.json.JsonObject
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okio.BufferedSink
import java.util.concurrent.TimeUnit

/** The endpoints behind the job seeker's work profile setup and their CV and certificates. */
class OnboardingApi(private val client: ApiClient, private val slow: ApiClient = client) {

    /** The signed-in job seeker's work profile, or null before it is created. */
    suspend fun profile(): WorkProfile? = client.get("/api/v1/providers/me")

    suspend fun categories(): List<WorkCategory> = client.get("/api/v1/categories")

    /** Adds a typed service: the engine finds the same work under another name, else creates it. */
    suspend fun registerService(name: String): RegisteredService =
        client.post("/api/v1/categories/register", RegisterServiceInput(name))

    /** Creates or updates the profile; [patch] holds only the fields of one step (see `WorkSetupBodies`). */
    suspend fun update(patch: JsonObject): WorkProfile = client.put("/api/v1/providers/me/update", patch)

    suspend fun setAvailability(input: AvailabilityInput): WorkProfile =
        client.put("/api/v1/providers/me/availability", input)

    /** "Write it for me": a bio draft from the AI, or a plain template when it is unavailable. */
    suspend fun suggestBio(input: BioSuggestInput): BioSuggestion =
        slow.post("/api/v1/providers/me/bio-suggest", input)

    /** A work photo, already resized to JPEG; answers the path to keep in the profile's photos. */
    suspend fun uploadWorkPhoto(jpeg: ByteArray, width: Int, height: Int): StoredMedia =
        client.upload("/api/media", jpeg, "image/jpeg", mapOf("kind" to "work", "w" to width, "h" to height))

    /** CV first, then certificates, as the engine sorts them. */
    suspend fun documents(): List<WorkDocument> = client.get("/api/v1/providers/me/documents")

    /**
     * Sends a CV or certificate as the raw body (POST /api/documents?kind=…).
     * The engine lets the AI read it before answering, so this uses the slow
     * client. [onProgress] gets the share of bytes sent, 0 to 1.
     */
    suspend fun uploadDocument(
        bytes: ByteArray,
        contentType: String,
        kind: DocumentKind,
        onProgress: (Float) -> Unit = {},
    ): WorkDocument {
        val body = ProgressBody(bytes, contentType.toMediaType(), onProgress)
        val request = slow.newRequest("/api/documents", mapOf("kind" to kind.wire)).post(body).build()
        return slow.execute(request, WorkDocument.serializer())
    }

    /** Puts the CV written in the CV studio (as a PDF the engine renders) on the work profile. */
    suspend fun attachStudioCv(): WorkDocument = slow.post("/api/v1/providers/me/documents/studio-cv")

    /** Details typed by the job seeker; the document becomes self-declared. */
    suspend fun updateDocument(input: DocumentDetailsInput): WorkDocument =
        client.put("/api/v1/providers/me/documents/${input.id}", input)

    suspend fun removeDocument(id: Int): DocumentRemoved =
        client.delete("/api/v1/providers/me/documents/$id/remove", mapOf("id" to id))

    /** The file itself, with the session (documents are private). */
    suspend fun documentFile(document: WorkDocument): ByteArray = client.download(document.fileUrl)
}

/** The setup API for a ViewModel; AI reading (documents, bio) gets a longer timeout than the app's 20 seconds. */
fun AppContainer.onboardingApi(): OnboardingApi = OnboardingApi(
    client,
    ApiClient(
        baseUrl = BuildConfig.API_BASE_URL,
        http = http.newBuilder()
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build(),
        token = session::token,
        onSessionRejected = session::onRejected,
    ),
)

/** A byte body that reports how much of it has been written to the network. */
private class ProgressBody(
    private val bytes: ByteArray,
    private val type: MediaType,
    private val onProgress: (Float) -> Unit,
) : RequestBody() {
    override fun contentType(): MediaType = type

    override fun contentLength(): Long = bytes.size.toLong()

    override fun writeTo(sink: BufferedSink) {
        var sent = 0
        while (sent < bytes.size) {
            val count = minOf(CHUNK, bytes.size - sent)
            sink.write(bytes, sent, count)
            sink.flush()
            sent += count
            onProgress(sent.toFloat() / bytes.size)
        }
    }

    private companion object {
        const val CHUNK = 16 * 1024
    }
}
