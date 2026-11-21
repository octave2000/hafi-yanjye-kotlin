package com.hafiyanjye.app.talent

import com.hafiyanjye.app.data.api.ApiClient
import com.hafiyanjye.app.data.api.ApiException
import com.hafiyanjye.app.data.api.HafiJson
import com.hafiyanjye.app.data.talent.TalentApi
import com.hafiyanjye.app.data.talent.TalentExperience
import com.hafiyanjye.app.data.talent.TalentInterviewAnswer
import com.hafiyanjye.app.data.talent.TalentInterviewDraft
import com.hafiyanjye.app.data.talent.TalentInterviewRequest
import com.hafiyanjye.app.data.talent.TalentLanguageLevel
import com.hafiyanjye.app.data.talent.TalentPatch
import com.hafiyanjye.app.data.talent.TalentProfileDto
import com.hafiyanjye.app.data.talent.TalentRules
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/** Paths, bodies and decoding of the talent endpoints, against real engine answers. */
class TalentApiTest {
    private lateinit var server: MockWebServer
    private lateinit var api: TalentApi

    @Before
    fun start() {
        server = MockWebServer()
        server.start()
        api = TalentApi(ApiClient(server.url("/").toString(), OkHttpClient(), { "session" }))
    }

    @After
    fun stop() {
        server.close()
    }

    private fun answer(json: String, status: Int = 200) {
        server.enqueue(
            MockResponse.Builder()
                .code(status)
                .addHeader("Content-Type", "application/json")
                .body(json)
                .build(),
        )
    }

    @Test
    fun `reads the real profile`() = runTest {
        answer(TalentFixtures.profile)
        val profile = api.me()
        assertEquals(3, profile?.id)
        assertEquals("Jean Bosco Habimana", profile?.fullName)
        assertEquals(75, profile?.completeness)
        assertEquals(listOf("summary", "photo"), profile?.missing)
        assertEquals(TalentLanguageLevel.Native, profile?.languages?.first()?.level)
        // A null end is the current job; a year is a finished one.
        assertNull(profile?.experience?.single()?.end)
        assertEquals("2018", profile?.education?.single()?.end)
        assertEquals("", profile?.links?.single()?.label)
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/api/v1/talent/me", request.url.encodedPath)
        assertEquals("Bearer session", request.headers["Authorization"])
    }

    @Test
    fun `no profile yet reads as null`() = runTest {
        answer(TalentFixtures.noProfile)
        assertNull(api.me())
    }

    @Test
    fun `polish posts to the polish route`() = runTest {
        answer(TalentFixtures.polish)
        val polish = api.polish()
        assertEquals("Electrician for Homes and Shops", polish.headline)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/talent/me/polish", request.url.encodedPath)
    }

    @Test
    fun `first interview round leaves final out and reads follow-ups`() = runTest {
        answer(TalentFixtures.interview)
        val result = api.interview(listOf(TalentInterviewAnswer("role", "What work do you do?", "Electrician")))
        assertEquals(3, result.followUps.size)
        assertTrue(result.ai)
        assertNull(result.draft.experience.single().end)
        val request = server.takeRequest()
        assertEquals("/api/v1/talent/me/interview", request.url.encodedPath)
        val body = request.body?.utf8().orEmpty()
        assertFalse(body.contains("final"))
    }

    @Test
    fun `final interview round says so`() = runTest {
        answer(TalentFixtures.interview)
        api.interview(listOf(TalentInterviewAnswer("followup-1", "Which tools?", "Multimeter")), final = true)
        val sent = HafiJson.decodeFromString<TalentInterviewRequest>(server.takeRequest().body?.utf8().orEmpty())
        assertEquals(true, sent.final)
        assertEquals("followup-1", sent.answers.single().id)
    }

    @Test
    fun `engine refusals arrive as its message`() = runTest {
        answer(TalentFixtures.interviewNoAnswers, status = 400)
        try {
            api.interview(emptyList())
            fail("expected an ApiException")
        } catch (error: ApiException) {
            assertEquals(400, error.status)
            assertEquals("Answer at least one question", error.message)
        }
    }

    @Test
    fun `a CV goes raw to the upload route with its type`() = runTest {
        answer(TalentFixtures.cvUpload, status = 201)
        val result = api.uploadCv(byteArrayOf(0x25, 0x50, 0x44, 0x46), "application/pdf")
        assertTrue(result.parsed)
        assertEquals(listOf("summary"), result.filled)
        assertEquals(3, result.profile.id)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/talent/cv", request.url.encodedPath)
        assertEquals("application/pdf", request.headers["Content-Type"])
        assertEquals("%PDF", request.body?.utf8())
    }

    @Test
    fun `update sends only the fields set`() = runTest {
        answer(TalentFixtures.profile)
        api.update(TalentPatch(isPublic = true))
        val request = server.takeRequest()
        assertEquals("PUT", request.method)
        assertEquals("/api/v1/talent/me/update", request.url.encodedPath)
        assertEquals("""{"isPublic":true}""", request.body?.utf8())
    }

    @Test
    fun `a finished job sends its end and a current job leaves it out`() {
        val finished = HafiJson.encodeToString(TalentExperience.serializer(), TalentExperience.blank())
        assertTrue(finished.contains("\"end\":\"\""))
        val currentJob = TalentExperience(role = "Cook", end = null)
        val current = HafiJson.encodeToString(TalentExperience.serializer(), currentJob)
        assertFalse(current.contains("end"))
    }

    @Test
    fun `photo removal is sent as an empty path`() {
        val json = HafiJson.encodeToString(TalentPatch.serializer(), TalentPatch(photo = ""))
        assertEquals("""{"photo":""}""", json)
    }

    @Test
    fun `saving an interview draft writes its eight parts and keeps contacts`() {
        val patch = TalentInterviewDraft(headline = "Electrician", skills = listOf("Wiring")).toPatch()
        val json = HafiJson.parseToJsonElement(HafiJson.encodeToString(TalentPatch.serializer(), patch)).jsonObject
        assertEquals(
            setOf("headline", "summary", "location", "skills", "languages", "experience", "education", "desiredRoles"),
            json.keys,
        )
        assertEquals(1, json["skills"]?.jsonArray?.size)
        assertFalse(json.containsKey("email"))
    }

    @Test
    fun `local completeness matches the engine's on the real profile`() {
        val profile = HafiJson.decodeFromString<JsonObject>(TalentFixtures.profile)["data"]
            ?.let { HafiJson.decodeFromJsonElement(TalentProfileDto.serializer(), it) }
            ?: error("no profile")
        val local = TalentRules.completeness(profile)
        assertEquals(profile.completeness, local.score)
        assertEquals(profile.missing, local.missing)
    }
}
