package com.hafiyanjye.app.onboarding

import com.hafiyanjye.app.data.api.ApiClient
import com.hafiyanjye.app.data.api.HafiJson
import com.hafiyanjye.app.data.onboarding.DocumentKind
import com.hafiyanjye.app.data.onboarding.DocumentStatus
import com.hafiyanjye.app.data.onboarding.OnboardingApi
import com.hafiyanjye.app.data.onboarding.RegisteredService
import com.hafiyanjye.app.data.onboarding.WorkDocument
import com.hafiyanjye.app.ui.onboarding.DocumentDetails
import com.hafiyanjye.app.ui.onboarding.WorkForm
import com.hafiyanjye.app.ui.onboarding.WorkSetupBodies
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

class OnboardingApiTest {
    private val server = MockWebServer()
    private lateinit var api: OnboardingApi

    @Before
    fun start() {
        server.start()
        api = OnboardingApi(ApiClient(server.url("/").toString(), OkHttpClient(), { "session-token" }))
    }

    @After
    fun stop() = server.close()

    private fun answer(body: String, status: Int = 200) {
        server.enqueue(
            MockResponse.Builder()
                .code(status)
                .addHeader("Content-Type", "application/json")
                .body(body.trimIndent())
                .build(),
        )
    }

    @Test
    fun `reads the real profile, an absent one, and services`() = runTest {
        answer(OnboardingFixtures.PROFILE)
        val profile = api.profile()
        assertEquals("Kimironko, Kigali", profile?.addressLabel)
        assertEquals("electrician", profile?.categories?.single()?.slug)
        assertEquals("/api/v1/providers/me", server.takeRequest().url.encodedPath)

        answer(OnboardingFixtures.NO_PROFILE)
        assertNull(api.profile())
        server.takeRequest()

        answer(OnboardingFixtures.CATEGORIES)
        val categories = api.categories()
        assertFalse(categories.last().listed)
        assertEquals("Zap", categories.first().icon)
    }

    @Test
    fun `documents decode with status, dates and private file paths`() = runTest {
        answer(OnboardingFixtures.DOCUMENTS)
        val documents = api.documents()
        assertEquals(listOf(DocumentKind.Cv, DocumentKind.Certificate), documents.map { it.kind })
        assertEquals(DocumentStatus.Checked, documents[1].status)
        assertEquals("IPRC Kigali · 2013", documents[1].meta)
        assertEquals("/api/documents/1/file", documents[1].fileUrl)
        assertFalse(documents[0].editable)
        assertEquals("/api/v1/providers/me/documents", server.takeRequest().url.encodedPath)

        val selfDeclared = HafiJson.decodeFromJsonElement(
            WorkDocument.serializer(),
            HafiJson.parseToJsonElement(OnboardingFixtures.SELF_DECLARED).jsonObject.getValue("data"),
        )
        assertTrue(selfDeclared.editable)
        assertEquals(Instant.parse("2027-06-30T23:59:59Z"), selfDeclared.expiresOn)
        assertEquals("2027-06-30", DocumentDetails.initialExpiry(selfDeclared))
    }

    @Test
    fun `document upload sends raw bytes with the kind and reports progress`() = runTest {
        answer(OnboardingFixtures.SELF_DECLARED, status = 201)
        val progress = mutableListOf<Float>()
        val bytes = ByteArray(40_000) { 1 }
        val document = api.uploadDocument(bytes, "application/pdf", DocumentKind.Certificate) { progress += it }
        assertEquals(7, document.id)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/documents", request.url.encodedPath)
        assertEquals("certificate", request.url.queryParameter("kind"))
        assertEquals("application/pdf", request.headers["Content-Type"])
        assertEquals("Bearer session-token", request.headers["Authorization"])
        assertEquals(40_000L, request.body?.size?.toLong())
        assertEquals(1f, progress.last())
        assertTrue(progress.size > 1)
    }

    @Test
    fun `details put the id in the path and the body, removal is a DELETE`() = runTest {
        val document = HafiJson.decodeFromJsonElement(
            WorkDocument.serializer(),
            HafiJson.parseToJsonElement(OnboardingFixtures.SELF_DECLARED).jsonObject.getValue("data"),
        )
        val input = DocumentDetails.input(document, " First  aid ", "", "2024", "", listOf("electrician")).getOrThrow()
        answer(OnboardingFixtures.SELF_DECLARED)
        api.updateDocument(input)
        val put = server.takeRequest()
        assertEquals("PUT", put.method)
        assertEquals("/api/v1/providers/me/documents/7", put.url.encodedPath)
        val body = HafiJson.parseToJsonElement(checkNotNull(put.body).utf8()).jsonObject
        assertEquals(7, body.getValue("id").jsonPrimitive.int)
        assertEquals("First aid", body.getValue("title").jsonPrimitive.content)
        assertEquals(2024, body.getValue("issuedYear").jsonPrimitive.int)
        assertEquals("electrician", body.getValue("categorySlugs").jsonArray.single().jsonPrimitive.content)

        answer(OnboardingFixtures.REMOVED)
        assertTrue(api.removeDocument(7).deleted)
        val remove = server.takeRequest()
        assertEquals("DELETE", remove.method)
        assertEquals("/api/v1/providers/me/documents/7/remove", remove.url.encodedPath)
    }

    @Test
    fun `details rules match the engine`() {
        val cv = WorkDocument(id = 2, kind = DocumentKind.Cv, title = "CV", fileUrl = "/api/documents/2/file")
        assertEquals("", DocumentDetails.initialTitle(cv))
        assertEquals("CV", DocumentDetails.input(cv, "", "", "", "", emptyList()).getOrThrow().title)
        val certificate = cv.copy(kind = DocumentKind.Certificate, title = "Certificate")
        assertTrue(DocumentDetails.input(certificate, "", "", "", "", emptyList()).isFailure)
        assertTrue(DocumentDetails.input(certificate, "Welding", "", "1949", "", emptyList(), 2026).isFailure)
        assertTrue(DocumentDetails.input(certificate, "Welding", "", "", "2027-02-30", emptyList()).isFailure)
        val ok = DocumentDetails.input(certificate, "Welding", " TVET ", "2020", "2027-06-30", listOf("welder"))
        assertEquals("TVET", ok.getOrThrow().issuer)
        val cvInput = DocumentDetails.input(cv, "My CV", "", "", "", listOf("x")).getOrThrow()
        assertEquals(emptyList<String>(), cvInput.categorySlugs)
    }

    @Test
    fun `work photo answers a UUID path, profile update sends the step body`() = runTest {
        answer(OnboardingFixtures.MEDIA, status = 201)
        val stored = api.uploadWorkPhoto(byteArrayOf(1, 2, 3), 1600, 1200)
        assertEquals("/api/media/5b0f3d1e-8f0a-4c43-9a55-0c8b1d6e2f10", stored.url)
        val upload = server.takeRequest()
        assertEquals("work", upload.url.queryParameter("kind"))
        assertEquals("1600", upload.url.queryParameter("w"))
        assertEquals("image/jpeg", upload.headers["Content-Type"])

        answer(OnboardingFixtures.PROFILE)
        api.update(WorkSetupBodies.whatYouDo(WorkForm(name = "Jean", services = listOf("electrician"))))
        val put = server.takeRequest()
        assertEquals("PUT", put.method)
        assertEquals("/api/v1/providers/me/update", put.url.encodedPath)
        assertEquals("""{"displayName":"Jean","categorySlugs":["electrician"]}""", checkNotNull(put.body).utf8())
    }

    @Test
    fun `registering a service posts the name and reads which kind it was`() = runTest {
        answer(OnboardingFixtures.REGISTERED_EXISTING)
        val result = api.registerService("plumber")
        assertEquals(RegisteredService.Kind.Existing, result.kind)
        assertEquals("plumber", result.category.slug)
        val post = server.takeRequest()
        assertEquals("/api/v1/categories/register", post.url.encodedPath)
        assertEquals("""{"name":"plumber"}""", checkNotNull(post.body).utf8())
    }
}
