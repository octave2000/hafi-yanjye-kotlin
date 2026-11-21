package com.hafiyanjye.app.data.match

import com.hafiyanjye.app.data.api.JobRequirement
import com.hafiyanjye.app.data.api.JobStatus
import com.hafiyanjye.app.data.api.SessionState
import com.hafiyanjye.app.data.api.Timestamp
import com.hafiyanjye.app.data.api.Urgency
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/*
 * One request and its live matching (engine/src/shared/contracts.ts).
 *
 * Role names: the engine says "provider" for the person who does the work.
 * Here that person is the job seeker, so Kotlin names say "worker" / "person"
 * and keep the engine's word only in @SerialName.
 */

/** What a starting price is counted per. */
@Serializable
enum class PriceUnit(val short: String) {
    @SerialName("hour") Hour("/ hour"),
    @SerialName("job") Job("/ job"),
    @SerialName("month") Month("/ month"),
}

/** Earned from answer rate, reply speed, rating and finished jobs. */
@Serializable
enum class WorkerLevel(val word: String) {
    @SerialName("new") New("New"),
    @SerialName("bronze") Bronze("Bronze"),
    @SerialName("silver") Silver("Silver"),
    @SerialName("gold") Gold("Gold"),
}

/** The service a request is for. */
@Serializable
data class JobCategory(
    val id: Int,
    val slug: String = "",
    val nameEn: String,
    val nameRw: String = "",
    val nameFr: String = "",
    val icon: String = "",
    val listed: Boolean = true,
)

/** The request as the job provider wrote it. */
@Serializable
data class Job(
    val id: Int,
    val rawText: String = "",
    val summary: String,
    val category: JobCategory,
    val requirements: List<JobRequirement> = emptyList(),
    val urgency: Urgency = Urgency.Flexible,
    val lat: Double,
    val lng: Double,
    val addressLabel: String? = null,
    /** Null when no budget was given. */
    val budgetRwf: Int? = null,
    val status: JobStatus,
    val notifyMe: Boolean = false,
    val sessionId: Int,
    val createdAt: Timestamp,
)

/** Live counts while people are being asked. */
@Serializable
data class PingStats(
    val pinged: Int = 0,
    val seen: Int = 0,
    val available: Int = 0,
    val declined: Int = 0,
    val wave: Int = 1,
    val radiusKm: Double = 0.0,
    /** Seconds from the first ping to the first yes; null until one arrives. */
    val firstReplySeconds: Double? = null,
    /** Median reply time in this service over 30 days; null when too few samples. */
    val typicalReplySeconds: Double? = null,
)

/** One anonymous "raised hand" on the live screen. */
@Serializable
data class Reply(
    val distanceKm: Double,
    val quoteRwf: Int? = null,
    val secondsAfterStart: Double = 0.0,
    val level: WorkerLevel = WorkerLevel.New,
)

/** An anonymous candidate during the elimination rounds (no name, no contact). */
@Serializable
data class Candidate(
    /** "c1", "c2", … */
    val shortId: String,
    val distanceKm: Double,
    val ratingAvg: Double = 0.0,
    val ratingCount: Int = 0,
    val yearsExperience: Int = 0,
    val completedJobs: Int = 0,
    val quoteRwf: Int? = null,
    val priceMinRwf: Int? = null,
    val priceUnit: PriceUnit = PriceUnit.Job,
    val priceMaxRwf: Int? = null,
    val responded: Boolean = false,
    val reasons: List<String> = emptyList(),
    val concerns: List<String> = emptyList(),
    val level: WorkerLevel = WorkerLevel.New,
    val replyMedianSec: Double? = null,
    val hasCertificate: Boolean = false,
    val hasCv: Boolean = false,
    /** The request asked for a certificate or CV and this person has none uploaded. */
    val missingCredential: Boolean = false,
)

/** One answer to a round's question; [keepCount] says how many people stay. */
@Serializable
data class RoundOption(
    val id: String,
    val label: String,
    val description: String = "",
    /** How many people stay if this is chosen. */
    val keepCount: Int = 0,
)

/** Who wrote a question or an answer: the AI, or the standard fallback. */
@Serializable
enum class RoundSource {
    @SerialName("claude") Ai,
    @SerialName("fallback") Standard,
}

/** "Which trait matters to you?": one open elimination round. */
@Serializable
data class Round(
    val roundNo: Int,
    val question: String,
    val options: List<RoundOption> = emptyList(),
    val source: RoundSource = RoundSource.Standard,
    /** Upper bound of rounds left, this one included. */
    val maxRounds: Int = 1,
)

/** Why the engine picked this person, in plain words. */
@Serializable
data class MatchExplanation(
    val headline: String = "",
    val whyThisPerson: String = "",
    val whatToSay: String = "",
    val questionsToAsk: List<String> = emptyList(),
    val backupsNote: String = "",
)

/** A CV or a certificate. */
@Serializable
enum class DocumentKind {
    @SerialName("cv") Cv,
    @SerialName("certificate") Certificate,
}

/** How far a document was checked. */
@Serializable
enum class CredentialStatus {
    /** Read by the AI and the name matched; not an official verification. */
    @SerialName("checked") Checked,
    @SerialName("self_declared") SelfDeclared,
}

/** A CV or certificate as others see it. */
@Serializable
data class Credential(
    val id: Int,
    val kind: DocumentKind,
    val title: String = "",
    val issuer: String? = null,
    val issuedYear: Int? = null,
    val status: CredentialStatus = CredentialStatus.SelfDeclared,
    val expired: Boolean = false,
    val categorySlugs: List<String> = emptyList(),
    /** CVs only: one line without personal details. */
    val cvSummary: String? = null,
    /** Engine path of the file, for the matched pick only. */
    val fileUrl: String? = null,
)

/** A job seeker revealed after a match. [phone] is only set for the current pick. */
@Serializable
data class MatchedPerson(
    /** Their work profile id (engine: providerId); what `Routes.person` takes. */
    @SerialName("providerId") val workerId: Int,
    val displayName: String,
    val picture: String? = null,
    val bio: String = "",
    val phone: String? = null,
    val distanceKm: Double = 0.0,
    val ratingAvg: Double = 0.0,
    val ratingCount: Int = 0,
    val yearsExperience: Int = 0,
    val completedJobs: Int = 0,
    val quoteRwf: Int? = null,
    val priceMinRwf: Int? = null,
    val priceUnit: PriceUnit = PriceUnit.Job,
    val priceMaxRwf: Int? = null,
    val categories: List<String> = emptyList(),
    val level: WorkerLevel = WorkerLevel.New,
    val replyMedianSec: Double? = null,
    val credentials: List<Credential> = emptyList(),
) {
    val firstName: String get() = firstNameOf(displayName)
}

/** "Jean Bosco Habimana" → "Jean"; the whole name when it has no spaces. */
fun firstNameOf(displayName: String): String =
    displayName.trim().substringBefore(' ').ifEmpty { displayName }

/** While matched: the last "ask more people" round. */
@Serializable
data class AskingMore(
    val askedAt: Timestamp,
    val asked: Int = 0,
    /** Still able to answer. */
    val waiting: Int = 0,
    val saidYes: Int = 0,
    val radiusKm: Double = 0.0,
)

/** What the one person a direct request went to has answered. */
@Serializable
enum class DirectAnswer {
    @SerialName("waiting") Waiting,
    @SerialName("accepted") Accepted,
    @SerialName("declined") Declined,
    @SerialName("no_answer") NoAnswer,
}

/** A hire request sent to one person from Find help. */
@Serializable
data class DirectRequest(
    @SerialName("providerId") val workerId: Int,
    val displayName: String,
    val picture: String? = null,
    val answer: DirectAnswer = DirectAnswer.Waiting,
) {
    val firstName: String get() = firstNameOf(displayName)
}

/** The live session. The screen switches on [state]. */
@Serializable
data class MatchSession(
    val id: Int,
    val jobId: Int,
    val state: SessionState,
    val stats: PingStats = PingStats(),
    /** Yeses so far, oldest first, anonymous. */
    val replies: List<Reply> = emptyList(),
    /** When the shortlist is built at the latest; the countdown runs to it. */
    val shortlistDueAt: Timestamp? = null,
    val candidates: List<Candidate> = emptyList(),
    /** The open round while eliminating. */
    val round: Round? = null,
    val roundsPlayed: Int = 0,
    /** How many people were turned down; above 0 while pinging means "searching again". */
    val turnedDown: Int = 0,
    val askingMore: AskingMore? = null,
    /** Present when matched or completed. */
    val pick: MatchedPerson? = null,
    val hiredAt: Timestamp? = null,
    val direct: DirectRequest? = null,
    val backups: List<MatchedPerson> = emptyList(),
    /** While matched: everyone else who said yes and was not turned down. */
    val alternatives: List<MatchedPerson> = emptyList(),
    val explanation: MatchExplanation? = null,
    val canReview: Boolean = false,
    val reviewed: Boolean = false,
    val updatedAt: Timestamp? = null,
) {
    val hired: Boolean get() = hiredAt != null
}

/** A request and its session: what GET /jobs/{id} and every request action return. */
@Serializable
data class JobWithSession(val job: Job, val session: MatchSession)

@Serializable
enum class RefineKind {
    /** Another person who said yes fits the extra wish better. */
    @SerialName("switch") Switch,
    /** The current pick already fits. */
    @SerialName("keep") Keep,
    /** Nobody's profile shows it. */
    @SerialName("none") None,
}

/** Answer to "Need something more?" on the match screen. */
@Serializable
data class RefineResult(
    val kind: RefineKind,
    /** The suggested person when [kind] is [RefineKind.Switch]. */
    @SerialName("providerId") val workerId: Int? = null,
    val reason: String = "",
    val source: RoundSource = RoundSource.Standard,
)

/** How many more people were asked, and the session with them. */
@Serializable
data class PingMoreResult(val pinged: Int = 0, val session: MatchSession)

/** The review as the engine stored it (answer of POST /reviews/create). */
@Serializable
data class Review(
    val id: Int,
    val rating: Int,
    val text: String = "",
    val reviewerName: String? = null,
    val createdAt: Timestamp,
)

// ── Request bodies. No defaults on sent fields: defaults are not encoded. ──

@Serializable
internal data class BudgetBody(val budgetRwf: Int)

@Serializable
internal data class NotifyBody(val notifyMe: Boolean)

@Serializable
internal data class AnswerBody(val roundNo: Int, val optionId: String)

@Serializable
internal data class ChooseBody(val providerId: Int)

/** A null reason is left out of the JSON. */
@Serializable
internal data class RejectBody(val reason: String?)

@Serializable
internal data class RefineBody(val text: String)

@Serializable
internal data class ReviewBody(val sessionId: Int, val rating: Int, val text: String?)
