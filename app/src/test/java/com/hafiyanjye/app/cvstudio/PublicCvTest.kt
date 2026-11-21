package com.hafiyanjye.app.cvstudio

import com.hafiyanjye.app.data.api.ApiClient
import com.hafiyanjye.app.data.api.ApiException
import com.hafiyanjye.app.data.cvstudio.AttachedCvStatus
import com.hafiyanjye.app.data.cvstudio.CvStudioApi
import com.hafiyanjye.app.data.cvstudio.PublicCvRules
import com.hafiyanjye.app.data.talent.CvTemplate
import com.hafiyanjye.app.data.talent.TalentExperience
import com.hafiyanjye.app.data.talent.TalentLanguageLevel
import com.hafiyanjye.app.ui.cvstudio.cvContent
import com.hafiyanjye.app.ui.cvstudio.detailLines
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.YearMonth

/** Answers recorded from the local engine (GET /api/v1/talent/3 and /4, 2026-11-17). */
private val PUBLIC_3 = """
    {
        "ok": true,
        "data": {
            "fullName": "Jean Bosco Habimana",
            "headline": "Electrician · 6 years on homes and shops",
            "summary": "",
            "email": "demo.provider1@hafiyanjye.test",
            "phone": "+250788123401",
            "location": "Kicukiro, Kigali",
            "photo": null,
            "skills": [
                "House wiring",
                "Solar installation",
                "Fault finding"
            ],
            "languages": [
                {
                    "name": "Kinyarwanda",
                    "level": "native"
                },
                {
                    "name": "English",
                    "level": "good"
                }
            ],
            "experience": [
                {
                    "end": null,
                    "role": "Electrician",
                    "start": "2021-03",
                    "company": "Volta Works",
                    "details": "Wired 40 new homes in Kicukiro\nTrained 2 apprentices"
                }
            ],
            "education": [
                {
                    "end": "2018",
                    "start": "2016",
                    "degree": "A1 Electrical Technology",
                    "school": "IPRC Kigali"
                }
            ],
            "links": [
                {
                    "url": "https://linkedin.com/in/demo",
                    "label": ""
                }
            ],
            "desiredRoles": [
                "Site electrician"
            ],
            "openToWork": true,
            "id": 3,
            "style": {
                "template": "bold",
                "accent": "#0b7a40",
                "font": "poppins",
                "showPhoto": true
            },
            "updatedAt": "2026-11-17T17:37:47.813Z"
        },
        "meta": {
            "requestId": "17a019b6-f22c-4fb1-af88-c7c28c8d74c4",
            "timestamp": "2026-11-17T23:43:15.882Z"
        }
    }
""".trimIndent()
private val PUBLIC_4 = """
    {
        "ok": true,
        "data": {
            "fullName": "Ana Analyst",
            "headline": "Facilities coordinator",
            "summary": "Five years running cleaning and maintenance crews in Kigali.",
            "email": "analyst.demo@example.com",
            "phone": "0781234567",
            "location": "",
            "photo": null,
            "skills": [
                "cleaning",
                "facility management",
                "plumbing"
            ],
            "languages": [],
            "experience": [],
            "education": [],
            "links": [],
            "desiredRoles": [
                "Facilities coordinator"
            ],
            "openToWork": true,
            "id": 4,
            "style": {
                "template": "bold",
                "accent": "#0b7a40",
                "font": "poppins",
                "showPhoto": true
            },
            "updatedAt": "2026-11-17T23:29:25.680Z"
        },
        "meta": {
            "requestId": "08d68e7b-5359-476a-a234-08334d7cc60f",
            "timestamp": "2026-11-17T23:43:19.685Z"
        }
    }
""".trimIndent()
private val NOT_FOUND = """
    {
        "ok": false,
        "error": {
            "code": "NOT_FOUND",
            "message": "Profile not found"
        },
        "meta": {
            "requestId": "920c8204-d8a6-4641-9268-a53bd3cee009",
            "timestamp": "2026-11-17T23:43:15.893Z"
        }
    }
""".trimIndent()

class PublicCvTest {
    private val server = MockWebServer()
    private lateinit var api: CvStudioApi

    @Before
    fun setUp() {
        server.start()
        api = CvStudioApi(ApiClient(server.url("/").toString(), OkHttpClient(), { "session_abc" }, {}))
    }

    @After
    fun tearDown() = server.close()

    private fun enqueue(code: Int, body: String, type: String = "application/json") {
        server.enqueue(MockResponse.Builder().code(code).addHeader("Content-Type", type).body(body).build())
    }

    @Test
    fun `decodes a full public profile from the engine`() = runTest {
        enqueue(200, PUBLIC_3)
        val profile = api.publicProfile(3)
        assertEquals("/api/v1/talent/3", server.takeRequest().url.encodedPath)
        assertEquals(3, profile.id)
        assertEquals("Jean Bosco Habimana", profile.fullName)
        assertEquals(TalentLanguageLevel.Native, profile.languages[0].level)
        assertNull(profile.experience[0].end)
        assertEquals("2018", profile.education[0].end)
        assertEquals("", profile.links[0].label)
        assertEquals(CvTemplate.Bold, profile.style.template)
        assertEquals(Instant.parse("2026-11-17T17:37:47.813Z"), profile.updatedAt)
    }

    @Test
    fun `decodes a profile with empty sections`() = runTest {
        enqueue(200, PUBLIC_4)
        val profile = api.publicProfile(4)
        assertTrue(profile.experience.isEmpty())
        assertEquals("", profile.location)
        val content = cvContent(profile, profile.style)
        assertEquals(listOf("email", "phone"), content.contacts.map { it.key })
    }

    @Test
    fun `a private or unknown profile is a 404`() = runTest {
        enqueue(404, NOT_FOUND)
        try {
            api.publicProfile(99999)
            fail("expected a 404")
        } catch (error: ApiException) {
            assertEquals(404, error.status)
            assertEquals("Profile not found", error.message)
        }
    }

    @Test
    fun `the PDF is the raw route with the accent override`() = runTest {
        enqueue(200, "%PDF-1.4 test", type = "application/pdf")
        val bytes = api.pdf("#2563c9")
        val sent = server.takeRequest()
        assertEquals("/api/talent/cv/pdf", sent.url.encodedPath)
        assertEquals("#2563c9", sent.url.queryParameter("accent"))
        assertEquals("Bearer session_abc", sent.headers["Authorization"])
        assertEquals("%PDF-1.4 test", bytes.decodeToString())
    }

    @Test
    fun `an invalid accent is not sent to the PDF route`() = runTest {
        enqueue(200, "%PDF", type = "application/pdf")
        api.pdf("red")
        assertNull(server.takeRequest().url.queryParameter("accent"))
    }

    @Test
    fun `attaching posts to studio-cv and reads the status`() = runTest {
        enqueue(
            201,
            """
                {
                    "ok": true,
                    "data": {
                        "id": 12,
                        "kind": "cv",
                        "title": "CV",
                        "issuer": null,
                        "issuedYear": null,
                        "expiresOn": null,
                        "expired": false,
                        "categorySlugs": [],
                        "status": "self_declared",
                        "checkNote": "AI is off.",
                        "cvSummary": null,
                        "fileUrl": "/api/providers/me/documents/12/file",
                        "createdAt": "2026-11-17T23:50:00.000Z"
                    },
                    "meta": {}
                }
            """.trimIndent(),
        )
        val attached = api.attachToWorkProfile()
        val sent = server.takeRequest()
        assertEquals("POST", sent.method)
        assertEquals("/api/v1/providers/me/documents/studio-cv", sent.url.encodedPath)
        assertEquals(AttachedCvStatus.SelfDeclared, attached.status)
        assertEquals("AI is off.", attached.checkNote)
    }

    @Test
    fun `phone links use the Rwandan international form`() {
        assertEquals("tel:+250788123401", PublicCvRules.telUri("+250788123401"))
        assertEquals("tel:+250781234567", PublicCvRules.telUri("0781234567"))
        assertEquals("tel:+250781234567", PublicCvRules.telUri("781 234 567"))
        assertNull(PublicCvRules.telUri("  "))
        assertEquals("mailto:a@b.rw", PublicCvRules.mailUri(" a@b.rw "))
    }

    @Test
    fun `years of experience run from the first start to the last end or now`() {
        val jobs = listOf(
            TalentExperience(role = "Electrician", start = "2021-03", end = null),
            TalentExperience(role = "Apprentice", start = "2018-06", end = "2021-02"),
        )
        assertEquals(8, PublicCvRules.yearsOfExperience(jobs, YearMonth.of(2026, 11)))
        assertEquals(0, PublicCvRules.yearsOfExperience(emptyList(), YearMonth.of(2026, 11)))
    }

    @Test
    fun `detail lines drop bullets and blanks`() {
        assertEquals(listOf("Wired 40 homes", "Trained 2"), detailLines("- Wired 40 homes\n\n• Trained 2\n  "))
        assertEquals("linkedin.com/in/demo", PublicCvRules.shortUrl("https://www.linkedin.com/in/demo/"))
    }
}
