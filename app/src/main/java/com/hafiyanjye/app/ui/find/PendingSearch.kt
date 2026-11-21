package com.hafiyanjye.app.ui.find

import com.hafiyanjye.app.ui.location.PickedLocation

/**
 * A search started on Find, handed to the new request form (web: pending-search.ts).
 * The route only carries text and a service id, so the place and the "send it
 * straight away" flag travel here. Once the form has read it, it keeps its own
 * copy in saved state, so losing this on process death loses nothing.
 */
data class PendingSearch(
    val text: String,
    val location: PickedLocation?,
    val budgetRwf: Int?,
    val categorySlug: String?,
    /** Text, place and budget were all fine on Find: send without asking again. */
    val autoSubmit: Boolean,
)

/** The one hand-off slot between Find and the form. */
object PendingSearches {
    private var pending: PendingSearch? = null

    /** Keeps [search] for the next form that opens. */
    fun save(search: PendingSearch) {
        pending = search
    }

    /** Reads and removes the draft, so opening the form again later starts clean. */
    fun take(): PendingSearch? = pending.also { pending = null }
}
