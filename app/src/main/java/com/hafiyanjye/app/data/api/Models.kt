package com.hafiyanjye.app.data.api

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.Instant

/*
 * The engine's DTOs (engine/src/shared/contracts.ts).
 *
 * Role names: the engine keeps the old words. In everything people read,
 * "seeker" mode is the JOB PROVIDER (has the money, hires) and "provider"
 * mode is the JOB SEEKER (wants work, gets pings). See [Role].
 */

/** The engine writes every Date as an ISO-8601 string. */
object InstantSerializer : KSerializer<Instant> {
    override val descriptor = PrimitiveSerialDescriptor("Instant", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: Instant) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): Instant = Instant.parse(decoder.decodeString())
}

typealias Timestamp = @Serializable(with = InstantSerializer::class) Instant

/** Which side of the app the account is using. */
@Serializable
enum class Role(val label: String) {
    /** Engine "seeker": the job provider, who hires and pays. */
    @SerialName("seeker")
    Hirer("Provider"),

    /** Engine "provider": the job seeker, who gets pinged with work. */
    @SerialName("provider")
    Worker("Seeker"),
}

@Serializable
data class Me(
    val id: Int,
    val email: String,
    val name: String? = null,
    val picture: String? = null,
    val phone: String? = null,
    @SerialName("activeRole") val role: Role = Role.Hirer,
    /** The job seeker's work profile exists (engine: provider profile). */
    @SerialName("hasProviderProfile") val hasWorkProfile: Boolean = false,
    val hasPassword: Boolean = false,
) {
    val firstName: String
        get() = name?.trim()?.substringBefore(' ')?.takeIf { it.isNotEmpty() } ?: email.substringBefore('@')

    val displayName: String
        get() = name?.trim()?.takeIf { it.isNotEmpty() } ?: email.substringBefore('@')
}

/** What token sign-in and register return. */
@Serializable
data class AuthSession(val user: Me, val token: String)

@Serializable
data class LoginInput(val email: String, val password: String)

@Serializable
data class RegisterInput(
    val name: String,
    val email: String,
    val password: String,
    val phone: String? = null,
)

@Serializable
data class RoleInput(val role: Role)

@Serializable
enum class JobStatus {
    @SerialName("open") Open,
    @SerialName("matched") Matched,
    @SerialName("completed") Completed,
    @SerialName("cancelled") Cancelled,
    @SerialName("no_providers") NoProviders,
}

@Serializable
enum class SessionState {
    @SerialName("pinging") Pinging,
    @SerialName("shortlisting") Shortlisting,
    @SerialName("eliminating") Eliminating,
    @SerialName("matched") Matched,
    @SerialName("completed") Completed,
    @SerialName("cancelled") Cancelled,
    @SerialName("no_providers") NoProviders,
}

/** One row of "My requests". */
@Serializable
data class JobListItem(
    val id: Int,
    val summary: String,
    val categoryName: String,
    val categoryIcon: String = "",
    val status: JobStatus,
    val state: SessionState,
    /** Null when no budget was given. */
    val budgetRwf: Int? = null,
    val addressLabel: String? = null,
    val createdAt: Timestamp,
)

@Serializable
enum class Importance {
    @SerialName("must") Must,
    @SerialName("nice") Nice,
}

@Serializable
data class JobRequirement(val label: String, val importance: Importance = Importance.Nice)

@Serializable
enum class Urgency {
    @SerialName("now") Now,
    @SerialName("today") Today,
    @SerialName("this_week") ThisWeek,
    @SerialName("flexible") Flexible,
}

@Serializable
enum class PingStatus {
    @SerialName("sent") Sent,
    @SerialName("seen") Seen,
    @SerialName("available") Available,
    @SerialName("not_now") NotNow,
    @SerialName("expired") Expired,
    @SerialName("withdrawn") Withdrawn,
}

/** Set on a ping once this job seeker was matched: the job provider's contact. */
@Serializable
data class PingMatch(
    val sessionId: Int,
    @SerialName("seekerName") val hirerName: String? = null,
    @SerialName("seekerPhone") val hirerPhone: String? = null,
    val addressLabel: String? = null,
    val lat: Double,
    val lng: Double,
    val state: SessionState,
    /** The job provider pressed Hire. */
    val hired: Boolean = false,
)

/** A job offered to a job seeker. */
@Serializable
data class Ping(
    val id: Int,
    val jobId: Int,
    val status: PingStatus,
    val wave: Int = 1,
    val distanceKm: Double,
    val summary: String,
    val categoryName: String,
    val requirements: List<JobRequirement> = emptyList(),
    val urgency: Urgency = Urgency.Flexible,
    /** Null when no budget was given. */
    val budgetRwf: Int? = null,
    /** Approximate area only (no exact pin before a match). */
    val areaLabel: String? = null,
    val quoteRwf: Int? = null,
    val note: String? = null,
    val sentAt: Timestamp,
    val expiresAt: Timestamp,
    /** The job provider picked this person directly from Find help. */
    val direct: Boolean = false,
    val match: PingMatch? = null,
)
