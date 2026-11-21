package com.hafiyanjye.app.data.talent

import com.hafiyanjye.app.data.api.Timestamp
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Mirrors engine/src/shared/contracts.ts "Talent (job seekers)": the data
// behind a job seeker's CV. Names follow the engine's.

/** CV look: the layout the CV studio renders. */
@Serializable
enum class CvTemplate(val label: String) {
    @SerialName("bold") Bold("Bold"),
    @SerialName("classic") Classic("Classic"),
    @SerialName("sidebar") Sidebar("Sidebar"),
    @SerialName("minimal") Minimal("Minimal"),
}

/** CV typeface. */
@Serializable
enum class CvFont(val label: String) {
    @SerialName("poppins") Poppins("Poppins"),
    @SerialName("geist") Geist("Geist"),
    @SerialName("serif") Serif("Serif"),
}

/** How well someone speaks a language, weakest first (the order the level bars fill). */
@Serializable
enum class TalentLanguageLevel(val label: String) {
    @SerialName("basic") Basic("Basic"),
    @SerialName("good") Good("Good"),
    @SerialName("fluent") Fluent("Fluent"),
    @SerialName("native") Native("Native"),
}

/**
 * One job on the CV. [end] defaults to null so that, with HafiJson leaving
 * defaults and nulls out, "current" is sent as no `end` (the engine reads it
 * as null) while a finished job's "" or date is always sent. New rows must
 * pass `end = ""` (see [blank]).
 */
@Serializable
data class TalentExperience(
    val role: String = "",
    val company: String = "",
    /** "2021" or "2021-03"; empty when unknown. */
    val start: String = "",
    /** Same format; null while it is the current job. */
    val end: String? = null,
    /** Short free text; one achievement per line. */
    val details: String = "",
) {
    companion object {
        /** An empty row for the editor: not the current job, like the web's. */
        fun blank() = TalentExperience(end = "")
    }
}

/** One school, course or certificate on the CV; [end] works like [TalentExperience.end]. */
@Serializable
data class TalentEducation(
    val school: String = "",
    val degree: String = "",
    val start: String = "",
    /** Null while still studying there. */
    val end: String? = null,
) {
    companion object {
        /** An empty row for the editor: finished, not "studying here". */
        fun blank() = TalentEducation(end = "")
    }
}

/** A language and how well the person speaks it; an unknown level reads as "good", like the engine. */
@Serializable
data class TalentLanguage(val name: String, val level: TalentLanguageLevel = TalentLanguageLevel.Good)

/** A web link on the CV; an empty [label] means "show the domain". */
@Serializable
data class TalentLink(val label: String = "", val url: String)

/** How the CV looks in the studio and the PDF. */
@Serializable
data class CvStyle(
    val template: CvTemplate = CvTemplate.Bold,
    /** Solid accent colour, "#rrggbb". */
    val accent: String = "#0b7a40",
    val font: CvFont = CvFont.Poppins,
    val showPhoto: Boolean = true,
) {
    companion object {
        val Default = CvStyle()

        /** Solid accent swatches the CV studio offers (engine CV_ACCENTS). */
        val Accents = listOf(
            "#0b7a40",
            "#0a1a11",
            "#2563c9",
            "#d13438",
            "#a15f00",
            "#0f766e",
            "#c2410c",
            "#be185d",
        )
    }
}

/** What a job seeker fills in (or what their uploaded CV fills in for them). */
interface TalentProfileFields {
    val fullName: String
    val headline: String
    val summary: String
    val email: String
    val phone: String
    val location: String

    /** Public photo path (/api/media/:id) or null. */
    val photo: String?
    val skills: List<String>
    val languages: List<TalentLanguage>
    val experience: List<TalentExperience>
    val education: List<TalentEducation>
    val links: List<TalentLink>
    val desiredRoles: List<String>
    val openToWork: Boolean
}

/** The uploaded CV file (owner only). [url] is an engine path: fetch it with `client.download`. */
@Serializable
data class TalentCvFile(val url: String, val uploadedAt: Timestamp)

/** The owner's view of their talent profile (GET /talent/me, PUT /talent/me/update). */
@Serializable
data class TalentProfileDto(
    val id: Int,
    override val fullName: String = "",
    override val headline: String = "",
    override val summary: String = "",
    override val email: String = "",
    override val phone: String = "",
    override val location: String = "",
    override val photo: String? = null,
    override val skills: List<String> = emptyList(),
    override val languages: List<TalentLanguage> = emptyList(),
    override val experience: List<TalentExperience> = emptyList(),
    override val education: List<TalentEducation> = emptyList(),
    override val links: List<TalentLink> = emptyList(),
    override val desiredRoles: List<String> = emptyList(),
    override val openToWork: Boolean = true,
    val style: CvStyle = CvStyle.Default,
    /** Anyone with the link can open the public CV when true. */
    val isPublic: Boolean = false,
    val cvFile: TalentCvFile? = null,
    /** 0–100 checklist score. */
    val completeness: Int = 0,
    /** Field keys still empty, heaviest first. */
    val missing: List<String> = emptyList(),
    val updatedAt: Timestamp,
) : TalentProfileFields

/** What anyone with the link sees (GET /talent/{id}). */
@Serializable
data class TalentPublicDto(
    val id: Int,
    override val fullName: String = "",
    override val headline: String = "",
    override val summary: String = "",
    override val email: String = "",
    override val phone: String = "",
    override val location: String = "",
    override val photo: String? = null,
    override val skills: List<String> = emptyList(),
    override val languages: List<TalentLanguage> = emptyList(),
    override val experience: List<TalentExperience> = emptyList(),
    override val education: List<TalentEducation> = emptyList(),
    override val links: List<TalentLink> = emptyList(),
    override val desiredRoles: List<String> = emptyList(),
    override val openToWork: Boolean = true,
    val style: CvStyle = CvStyle.Default,
    val updatedAt: Timestamp,
) : TalentProfileFields

/** A partial [CvStyle]: values that aren't valid keep the saved ones. */
@Serializable
data class CvStylePatch(
    val template: CvTemplate? = null,
    val accent: String? = null,
    val font: CvFont? = null,
    val showPhoto: Boolean? = null,
)

/**
 * Body of PUT /talent/me/update. Only the fields that are set are sent, and
 * only those change; the first save creates the profile.
 */
@Serializable
data class TalentPatch(
    val fullName: String? = null,
    val headline: String? = null,
    val summary: String? = null,
    val email: String? = null,
    val phone: String? = null,
    val location: String? = null,
    val photo: String? = null,
    val skills: List<String>? = null,
    val languages: List<TalentLanguage>? = null,
    val experience: List<TalentExperience>? = null,
    val education: List<TalentEducation>? = null,
    val links: List<TalentLink>? = null,
    val desiredRoles: List<String>? = null,
    val openToWork: Boolean? = null,
    val style: CvStylePatch? = null,
    val isPublic: Boolean? = null,
)

/** POST /api/talent/cv answer: the profile after the uploaded CV was read. */
@Serializable
data class TalentCvUploadDto(
    val profile: TalentProfileDto,
    /** False when the AI couldn't read it (not available, unreadable file). */
    val parsed: Boolean = false,
    /** [TalentProfileFields] keys the CV filled in. */
    val filled: List<String> = emptyList(),
)

/** One question of the "Build with AI" interview and what the person said. */
@Serializable
data class TalentInterviewAnswer(
    /** Fixed question id ("role", "skills"…) or "followup-N". */
    val id: String,
    val question: String,
    val answer: String,
)

/** Body of POST /talent/me/interview. */
@Serializable
data class TalentInterviewRequest(val answers: List<TalentInterviewAnswer>, val final: Boolean? = null)

/**
 * The parts of a profile the interview writes (contacts and photo stay). The
 * engine already kept what the profile had wherever the answers said nothing.
 */
@Serializable
data class TalentInterviewDraft(
    val headline: String = "",
    val summary: String = "",
    val location: String = "",
    val skills: List<String> = emptyList(),
    val languages: List<TalentLanguage> = emptyList(),
    val experience: List<TalentExperience> = emptyList(),
    val education: List<TalentEducation> = emptyList(),
    val desiredRoles: List<String> = emptyList(),
) {
    /** The draft as an update body: saving it writes all eight parts. */
    fun toPatch() = TalentPatch(
        headline = headline,
        summary = summary,
        location = location,
        skills = skills,
        languages = languages,
        experience = experience,
        education = education,
        desiredRoles = desiredRoles,
    )
}

/** A built profile to review before saving; nothing is saved yet. */
@Serializable
data class TalentInterview(
    val draft: TalentInterviewDraft,
    /** Up to 3 short questions about real gaps; empty on the final round. */
    val followUps: List<String> = emptyList(),
    /** False when the AI was unavailable and only the simple parts were filled. */
    val ai: Boolean = false,
)

/** POST /talent/me/polish answer: a suggestion, nothing is saved. */
@Serializable
data class TalentPolish(val headline: String = "", val summary: String = "")
