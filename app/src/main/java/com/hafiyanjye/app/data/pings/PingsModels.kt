package com.hafiyanjye.app.data.pings

import com.hafiyanjye.app.data.api.Timestamp
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/*
 * The job seeker's side of the engine (contracts.ts: ProviderProfileDto,
 * ProviderStatsDto, RankingBreakdownDto, ReviewDto). The engine says
 * "provider" for the person who wants work; here that is the work profile.
 * The ping itself is `data.api.Ping`.
 */

/** Online gets pings from people nearby; offline gets none (there is no "busy"). */
@Serializable
enum class Availability {
    @SerialName("online") Online,
    @SerialName("offline") Offline,
}

/** How complete a work profile is; "ready" switches starter seats on. */
@Serializable
enum class ProfileStage {
    @SerialName("getting_started") GettingStarted,
    @SerialName("ready") Ready,
    @SerialName("strong") Strong,
}

/** Level earned by answering well; higher levels are pinged first. */
@Serializable
enum class WorkLevel(val word: String) {
    @SerialName("new") New("New"),
    @SerialName("bronze") Bronze("Bronze"),
    @SerialName("silver") Silver("Silver"),
    @SerialName("gold") Gold("Gold"),
}

/** What the starting price is per. */
@Serializable
enum class PriceUnit(val short: String) {
    @SerialName("hour") Hour("/ hour"),
    @SerialName("job") Job("/ job"),
    @SerialName("month") Month("/ month"),
}

/** A service someone offers (engine: CategoryDto). */
@Serializable
data class WorkCategory(
    val id: Int,
    val slug: String = "",
    val nameEn: String,
    val nameRw: String = "",
    val nameFr: String = "",
    /** Lucide icon name, for `categoryIcon(name)`. */
    val icon: String = "",
    val listed: Boolean = true,
)

/** The signed-in job seeker's work profile (engine: ProviderProfileDto). */
@Serializable
data class WorkProfile(
    val id: Int,
    val userId: Int,
    val displayName: String,
    val picture: String? = null,
    val bio: String = "",
    val yearsExperience: Int = 0,
    val priceMinRwf: Int? = null,
    val priceUnit: PriceUnit = PriceUnit.Job,
    val priceMaxRwf: Int? = null,
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val serviceRadiusKm: Double = 0.0,
    val addressLabel: String? = null,
    val photos: List<String> = emptyList(),
    val languages: List<String> = emptyList(),
    /** 0 to 100. */
    val completenessScore: Double = 0.0,
    val profileStage: ProfileStage = ProfileStage.GettingStarted,
    val completenessTips: List<String> = emptyList(),
    val availability: Availability = Availability.Offline,
    val maxConcurrentJobs: Int = 1,
    val ratingAvg: Double = 0.0,
    val ratingCount: Int = 0,
    val completedJobs: Int = 0,
    val categories: List<WorkCategory> = emptyList(),
    val level: WorkLevel = WorkLevel.New,
    val replyMedianSec: Double? = null,
)

/** One requirement on the way to the next level. */
@Serializable
data class LevelRequirement(
    val label: String,
    val met: Boolean = false,
    /** Written by the engine, e.g. "82% of 90%". */
    val progress: String = "",
)

/** This week's numbers on the dashboard. */
@Serializable
data class WeekStats(
    val pingsReceived: Int = 0,
    val answered: Int = 0,
    val chosen: Int = 0,
    val jobsDone: Int = 0,
    /** Sum of this person's own quotes on jobs finished this week (not a payout). */
    val quotedEarningsRwf: Int = 0,
)

/** The habit dashboard (engine: ProviderStatsDto). */
@Serializable
data class WorkStats(
    val level: WorkLevel = WorkLevel.New,
    val nextLevel: WorkLevel? = null,
    val nextLevelChecklist: List<LevelRequirement> = emptyList(),
    /** Median reply seconds over the last 30 counted pings; null when none. */
    val replyMedianSec: Double? = null,
    /** 0 to 1 over the last 30 counted pings; null when none. */
    val answerRate: Double? = null,
    val pingsCounted: Int = 0,
    val fastStreak: Int = 0,
    val bestStreak: Int = 0,
    val missedStreak: Int = 0,
    /** Jobs nearby that went out while offline, since last online. */
    val missedWhileOffline: Int = 0,
    /** Set when the app paused this person after 3 missed pings in a row. */
    val pausedAt: Timestamp? = null,
    val week: WeekStats = WeekStats(),
)

/** A sub-part of a ranking part (e.g. rating inside trust). */
@Serializable
data class RankingItem(val label: String, val points: Double = 0.0, val max: Double = 0.0)

/** One part of the Fair Rank score. */
@Serializable
data class RankingPart(
    val key: String = "",
    val label: String,
    /** Null for job fit, which depends on each job. */
    val points: Double? = null,
    val max: Double = 0.0,
    val detail: String = "",
    val items: List<RankingItem> = emptyList(),
)

/** One action that raises the score, with the points it gains. */
@Serializable
data class RankingStep(val label: String, val gain: Double = 0.0)

/** One step of the starter path and what reaching it unlocks. */
@Serializable
data class StarterStep(val label: String, val unlocks: String = "", val done: Boolean = false)

/** The path for people with fewer than 10 jobs done. */
@Serializable
data class StarterPath(
    val boostPoints: Double = 0.0,
    val jobsLeft: Int = 0,
    val seatEligible: Boolean = false,
    val steps: List<StarterStep> = emptyList(),
)

/** The Fair Rank breakdown (engine: RankingBreakdownDto). */
@Serializable
data class RankingBreakdown(
    /** Points earned outside job fit, out of 50. */
    val standingTotal: Double = 0.0,
    val parts: List<RankingPart> = emptyList(),
    val nextSteps: List<RankingStep> = emptyList(),
    val profileStage: ProfileStage = ProfileStage.GettingStarted,
    /** Null once past the first 10 jobs. */
    val starter: StarterPath? = null,
)

/** A review left by a job provider (engine: ReviewDto). */
@Serializable
data class Review(
    val id: Int,
    val rating: Double,
    val text: String = "",
    val reviewerName: String? = null,
    val createdAt: Timestamp,
)

/** PUT /pings/{id}/answer. Null fields are left out of the JSON. */
@Serializable
data class PingAnswerInput(val available: Boolean, val quoteRwf: Int? = null, val note: String? = null)

/** PUT /providers/me/availability. */
@Serializable
data class AvailabilityInput(val availability: Availability)
