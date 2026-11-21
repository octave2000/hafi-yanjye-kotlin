package com.hafiyanjye.app.ui.nav

import android.net.Uri
import androidx.compose.runtime.compositionLocalOf
import com.hafiyanjye.app.data.api.Me

/** The signed-in account, for any screen inside the shell. Changes when the role is switched. */
val LocalMe = compositionLocalOf<Me> { error("No signed-in account here") }

/**
 * Every screen's address. Tab roots are plain strings; pushed screens have a
 * `…Pattern` for `composable(route = …)` and a function that builds the
 * address to `navigate(…)` to. The feature that owns a screen registers it in
 * its `<feature>Graph` function (ui/<feature>/<Feature>Graph.kt).
 */
object Routes {
    // ── Tab roots: job provider (the one hiring) ────────────────────────────
    const val Find = "find"
    const val Jobs = "jobs"
    const val Rentals = "rentals"

    // ── Tab roots: job seeker (the one working) ─────────────────────────────
    const val Pings = "pings"
    const val Listings = "listings"
    const val Cv = "cv"

    // ── Tab roots: both ─────────────────────────────────────────────────────
    const val Chat = "chat"
    const val Profile = "profile"

    val Tabs = setOf(Find, Jobs, Rentals, Pings, Listings, Cv, Chat, Profile)

    // ── find: ask for help ──────────────────────────────────────────────────
    /** New request form. `text` prefills what they typed; `categoryId` a chosen service. */
    const val NewRequestPattern = "requests/new?text={text}&categoryId={categoryId}"
    fun newRequest(text: String? = null, categoryId: Int? = null) =
        "requests/new" + query("text" to text, "categoryId" to categoryId)

    // ── match: one request and its live matching ────────────────────────────
    const val RequestPattern = "requests/{jobId}"
    fun request(jobId: Int) = "requests/$jobId"

    // ── browse: services list and a person's public profile ─────────────────
    const val ServicesPattern = "services?categoryId={categoryId}&q={q}"
    fun services(categoryId: Int? = null, q: String? = null) =
        "services" + query("categoryId" to categoryId, "q" to q)

    /** Public profile of a job seeker (engine: GET /providers/{id}). */
    const val PersonPattern = "people/{providerId}"
    fun person(providerId: Int) = "people/$providerId"

    // ── trip: live map after a hire ─────────────────────────────────────────
    const val TripPattern = "trip/{sessionId}"
    fun trip(sessionId: Int) = "trip/$sessionId"

    // ── chat ────────────────────────────────────────────────────────────────
    const val ThreadPattern = "chat/{conversationId}"
    fun thread(conversationId: Int) = "chat/$conversationId"

    /**
     * Start or reopen a chat (engine: POST /chat/open with exactly one of the
     * ids), then show its thread. Any feature may navigate here; chat owns it.
     */
    const val ChatOpenPattern = "chat/open?providerId={providerId}&jobId={jobId}&listingId={listingId}"
    fun chatWith(providerId: Int? = null, jobId: Int? = null, listingId: Int? = null) =
        "chat/open" + query("providerId" to providerId, "jobId" to jobId, "listingId" to listingId)

    // ── rentals (browse) and listings (owner) ───────────────────────────────
    const val RentalSearchPattern = "rentals/search?q={q}&kind={kind}&lat={lat}&lng={lng}"
    fun rentalSearch(q: String? = null, kind: String? = null, lat: Double? = null, lng: Double? = null) =
        "rentals/search" + query("q" to q, "kind" to kind, "lat" to lat, "lng" to lng)

    const val RentalPattern = "rentals/{listingId}"
    fun rental(listingId: Int) = "rentals/$listingId"

    const val ListingNew = "listings/new"
    const val ListingEditPattern = "listings/{listingId}/edit"
    fun listingEdit(listingId: Int) = "listings/$listingId/edit"

    // ── onboarding: the job seeker's work profile and documents ─────────────
    const val WorkSetup = "work/setup"
    const val Credentials = "work/credentials"

    // ── talent / cvstudio ───────────────────────────────────────────────────
    const val CvStudio = "cv/studio"
    /** Someone's public CV (engine: GET /talent/{id}). */
    const val PublicCvPattern = "cv/public/{userId}"
    fun publicCv(userId: Int) = "cv/public/$userId"

    // ── profile ─────────────────────────────────────────────────────────────
    const val EditProfile = "profile/edit"
    const val Notifications = "notifications"

    /**
     * Where an engine notification's `url` (a web path such as "/jobs/12",
     * "/provider", "/chat/3") leads in the app. Null when there is no such screen.
     */
    fun fromWebPath(url: String?): String? {
        val path = url?.substringBefore('?')?.trim('/') ?: return null
        val parts = path.split('/').filter { it.isNotEmpty() }
        fun id(index: Int) = parts.getOrNull(index)?.toIntOrNull()
        return when (parts.firstOrNull()) {
            "jobs" -> id(1)?.let(::request) ?: Jobs
            "find" -> Find
            "provider" -> when (parts.getOrNull(1)) {
                "listings" -> Listings
                "onboarding" -> WorkSetup
                else -> Pings
            }
            "providers" -> id(1)?.let(::person)
            "chat" -> id(1)?.let(::thread) ?: Chat
            "rentals" -> id(1)?.let(::rental) ?: Rentals
            "services" -> services()
            "work" -> if (parts.getOrNull(1) == "cv") CvStudio else Cv
            "cv" -> id(1)?.let(::publicCv)
            "profile" -> Profile
            "trip" -> id(1)?.let(::trip)
            else -> null
        }
    }

    private fun query(vararg pairs: Pair<String, Any?>): String {
        val parts = pairs.filter { it.second != null }.map { "${it.first}=${Uri.encode(it.second.toString())}" }
        return if (parts.isEmpty()) "" else "?" + parts.joinToString("&")
    }
}
