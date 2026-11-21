package com.hafiyanjye.app.data.cvstudio

import com.hafiyanjye.app.data.api.ApiClient
import com.hafiyanjye.app.data.talent.TalentPublicDto

/** The CV studio's own calls; reading and saving the profile itself goes through `TalentApi`. */
class CvStudioApi(private val client: ApiClient, private val slow: ApiClient = client) {

    /** Anyone's public CV (GET /talent/{id}, by talent profile id). Private or unknown ids answer 404. */
    suspend fun publicProfile(id: Int): TalentPublicDto = client.get("/api/v1/talent/$id")

    /**
     * The signed-in person's CV as the engine's PDF (raw route GET /api/talent/cv/pdf).
     * [accent] overrides the saved accent, so an unsaved colour still shows in the file.
     */
    suspend fun pdf(accent: String?): ByteArray =
        client.download("/api/talent/cv/pdf", mapOf("accent" to accent?.takeIf(CvStyles::isHex)))

    /** Writes the studio CV out as a PDF and puts it on the work profile, replacing any earlier CV. */
    suspend fun attachToWorkProfile(): AttachedCv = slow.post("/api/v1/providers/me/documents/studio-cv")
}
