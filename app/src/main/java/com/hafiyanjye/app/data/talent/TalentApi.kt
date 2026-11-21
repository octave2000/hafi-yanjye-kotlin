package com.hafiyanjye.app.data.talent

import com.hafiyanjye.app.AppContainer
import com.hafiyanjye.app.BuildConfig
import com.hafiyanjye.app.data.api.ApiClient
import kotlinx.serialization.Serializable
import java.util.concurrent.TimeUnit

/**
 * The job seeker's talent profile: the data behind their CV.
 *
 * [slow] is the same engine with a longer read timeout, for the calls where
 * the AI reads or writes (interview, polish, reading an uploaded CV).
 */
class TalentApi(private val client: ApiClient, private val slow: ApiClient = client) {

    /** The signed-in person's profile, or null before they start one. Reading never creates it. */
    suspend fun me(): TalentProfileDto? = client.get("/api/v1/talent/me")

    /** Creates the profile if needed and changes only the fields set in [patch]. */
    suspend fun update(patch: TalentPatch): TalentProfileDto = client.put("/api/v1/talent/me/update", patch)

    /** A crisper headline and summary to accept or dismiss (a plain template when the AI is unavailable). */
    suspend fun polish(): TalentPolish = slow.post("/api/v1/talent/me/polish")

    /**
     * "Build with AI": answers in, a draft out; nothing is saved. The first
     * round may ask follow-ups; send [final] with their answers for the finished draft.
     */
    suspend fun interview(answers: List<TalentInterviewAnswer>, final: Boolean = false): TalentInterview =
        slow.post("/api/v1/talent/me/interview", TalentInterviewRequest(answers, final.takeIf { it }))

    /**
     * Uploads an existing CV (a PDF, or a photo as JPEG) to the raw route
     * POST /api/talent/cv; the engine keeps it and the AI fills the empty parts.
     */
    suspend fun uploadCv(bytes: ByteArray, contentType: String): TalentCvUploadDto =
        slow.upload("/api/talent/cv", bytes, contentType)

    /** Stores a square JPEG as a public profile photo and returns its path for [TalentPatch.photo]. */
    suspend fun uploadPhoto(jpeg: ByteArray, side: Int): String {
        val query = mapOf("kind" to "avatar", "w" to side, "h" to side)
        return client.upload<UploadedMedia>("/api/media", jpeg, "image/jpeg", query).url
    }

    @Serializable
    private data class UploadedMedia(val url: String)
}

/** The talent API for a ViewModel: `containerViewModel { MyViewModel(it.talentApi()) }`. */
fun AppContainer.talentApi(): TalentApi = TalentApi(
    client,
    ApiClient(
        baseUrl = BuildConfig.API_BASE_URL,
        // The AI can take longer than the app's usual 20 seconds.
        http = http.newBuilder()
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build(),
        token = session::token,
        onSessionRejected = session::onRejected,
    ),
)
