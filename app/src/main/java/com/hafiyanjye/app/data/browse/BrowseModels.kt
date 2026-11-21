package com.hafiyanjye.app.data.browse

import com.hafiyanjye.app.data.api.Timestamp
import com.hafiyanjye.app.data.match.Credential
import com.hafiyanjye.app.data.match.JobCategory
import com.hafiyanjye.app.data.match.PriceUnit
import com.hafiyanjye.app.data.match.WorkerLevel
import com.hafiyanjye.app.ui.location.LocationSource
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** The engine's `ProviderSearchSort`: how the services list is ordered. */
@Serializable
enum class SearchSort(val wire: String, val label: String) {
    @SerialName("distance") Distance("distance", "Closest first"),
    @SerialName("rating") Rating("rating", "Top rated"),
    @SerialName("price") Price("price", "Lowest price"),
    @SerialName("jobs") Jobs("jobs", "Most jobs done"),
}

/** Whether a job seeker takes requests right now. */
@Serializable
enum class Availability {
    @SerialName("online") Online,
    @SerialName("offline") Offline,
}

/** One job seeker in the services list (engine `ProviderSearchDto.items`): public view, no phone or coordinates. */
@Serializable
data class WorkerResult(
    val id: Int,
    val displayName: String,
    val picture: String? = null,
    val bioSnippet: String = "",
    val addressLabel: String? = null,
    val categories: List<JobCategory> = emptyList(),
    val ratingAvg: Double = 0.0,
    val ratingCount: Int = 0,
    val completedJobs: Int = 0,
    val yearsExperience: Int = 0,
    val priceMinRwf: Int? = null,
    val priceUnit: PriceUnit = PriceUnit.Job,
    val priceMaxRwf: Int? = null,
    val level: WorkerLevel = WorkerLevel.New,
    val availability: Availability = Availability.Offline,
    val replyMedianSec: Int? = null,
    val languages: List<String> = emptyList(),
    /** Rounded by the engine (0.5 for anything closer); null without a location. */
    val distanceKm: Double? = null,
    /** False when the reader is outside the area this person usually covers; null without a location. */
    val coversYou: Boolean? = null,
    val hasCheckedCredential: Boolean = false,
)

/** One page of `GET /providers/search`. */
@Serializable
data class WorkerSearch(
    val items: List<WorkerResult>,
    val total: Int,
    /** The sort the engine applied (distance falls back to rating without a location). */
    val sort: SearchSort = SearchSort.Rating,
    val located: Boolean = false,
    val category: JobCategory? = null,
    /** Matches per service slug for every filter except the service. */
    val categoryCounts: Map<String, Int> = emptyMap(),
    val allCount: Int = 0,
)

/** A job seeker's public profile (engine `PublicProviderDto`): no coordinates, no contact, no files. */
@Serializable
data class PublicWorker(
    val id: Int,
    val displayName: String,
    val picture: String? = null,
    val bio: String = "",
    val yearsExperience: Int = 0,
    val priceMinRwf: Int? = null,
    val priceUnit: PriceUnit = PriceUnit.Job,
    val priceMaxRwf: Int? = null,
    val addressLabel: String? = null,
    val photos: List<String> = emptyList(),
    val languages: List<String> = emptyList(),
    val ratingAvg: Double = 0.0,
    val ratingCount: Int = 0,
    val completedJobs: Int = 0,
    val categories: List<JobCategory> = emptyList(),
    val level: WorkerLevel = WorkerLevel.New,
    val replyMedianSec: Int? = null,
    val credentials: List<Credential> = emptyList(),
)

/** A review on a public profile; the reviewer is the person who hired them. */
@Serializable
data class PublicReview(
    val id: Int,
    val rating: Int,
    val text: String = "",
    val reviewerName: String? = null,
    val createdAt: Timestamp,
)

/** Body of `POST /jobs/direct`: a request sent to one person, who accepts or declines. */
@Serializable
data class DirectHireInput(
    @SerialName("providerId") val jobSeekerId: Int,
    val categorySlug: String,
    val text: String,
    val lat: Double,
    val lng: Double,
    val addressLabel: String? = null,
    val locationSource: LocationSource,
    val budgetRwf: Int? = null,
)

/** What `POST /jobs/direct` answers: the new request and its matching session. */
@Serializable
data class DirectHireResult(val jobId: Int, val sessionId: Int)
