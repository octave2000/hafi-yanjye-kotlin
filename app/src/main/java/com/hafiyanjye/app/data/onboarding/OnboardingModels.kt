package com.hafiyanjye.app.data.onboarding

import com.hafiyanjye.app.data.api.Timestamp
import com.hafiyanjye.app.data.pings.WorkCategory
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/*
 * The job seeker's work profile setup (engine: api/v1/providers/me/…,
 * api/v1/categories/register, core/providers/documents.ts). The profile
 * itself and its services are the pings feature's `WorkProfile` and
 * `WorkCategory`; this file holds what only setup needs.
 */

/** What a CV or certificate is (engine: DocumentKind). */
@Serializable
enum class DocumentKind(val wire: String, val word: String) {
    @SerialName("cv") Cv("cv", "CV"),
    @SerialName("certificate") Certificate("certificate", "certificate"),
}

/** What the AI check made of a document (engine: DocumentStatus). */
@Serializable
enum class DocumentStatus {
    @SerialName("checked") Checked,
    @SerialName("self_declared") SelfDeclared,
    @SerialName("unreadable") Unreadable,
}

/** One of the job seeker's own CVs or certificates (engine: ProviderDocumentDto). */
@Serializable
data class WorkDocument(
    val id: Int,
    val kind: DocumentKind = DocumentKind.Certificate,
    val title: String,
    val issuer: String? = null,
    val issuedYear: Int? = null,
    val expiresOn: Timestamp? = null,
    val expired: Boolean = false,
    val categorySlugs: List<String> = emptyList(),
    val status: DocumentStatus = DocumentStatus.Unreadable,
    /** Why it couldn't be checked; null when checked. */
    val checkNote: String? = null,
    val cvSummary: String? = null,
    /** "/api/documents/:id/file": private, opened with the session. */
    val fileUrl: String,
    val createdAt: Timestamp? = null,
) {
    /** Checked documents can't be edited: the engine wants them removed and uploaded again. */
    val editable: Boolean get() = status != DocumentStatus.Checked

    /** "IPRC Kigali · 2013", or null with neither. */
    val meta: String?
        get() = listOfNotNull(issuer?.takeIf { it.isNotBlank() }, issuedYear?.toString())
            .joinToString(" · ")
            .takeIf { it.isNotEmpty() }
}

/** Body of PUT /providers/me/documents/:id. The engine reads `id` from the body like the web sends it. */
@Serializable
data class DocumentDetailsInput(
    val id: Int,
    val title: String,
    val issuer: String?,
    val issuedYear: Int?,
    /** "2027-06-30" or null. */
    val expiresOn: String?,
    val categorySlugs: List<String>,
)

@Serializable
data class DocumentRemoved(val deleted: Boolean = true)

@Serializable
data class RegisterServiceInput(val name: String)

/** The engine either found the same work under another name or created it. */
@Serializable
data class RegisteredService(val kind: Kind, val category: WorkCategory) {
    @Serializable
    enum class Kind {
        @SerialName("existing") Existing,
        @SerialName("created") Created,
    }
}

@Serializable
data class BioSuggestInput(
    val displayName: String,
    val categorySlugs: List<String>,
    val yearsExperience: Int,
    val notes: String? = null,
)

@Serializable
data class BioSuggestion(val bio: String, val source: Source = Source.Fallback) {
    @Serializable
    enum class Source {
        @SerialName("claude") Claude,
        @SerialName("fallback") Fallback,
    }
}

/** POST /api/media answer: the stored path to save with the profile. */
@Serializable
data class StoredMedia(val id: String, val url: String)
