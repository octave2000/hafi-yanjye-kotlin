package com.hafiyanjye.app.ui.find

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hafiyanjye.app.data.api.HafiJson
import com.hafiyanjye.app.data.find.FindApi
import com.hafiyanjye.app.data.find.FindCategory
import com.hafiyanjye.app.data.find.ListingAsk
import com.hafiyanjye.app.data.realtime.RealtimeEvent
import com.hafiyanjye.app.data.realtime.RealtimeHub
import com.hafiyanjye.app.ui.Load
import com.hafiyanjye.app.ui.Loader
import com.hafiyanjye.app.ui.location.PickedLocation
import com.hafiyanjye.app.ui.nav.Routes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Which part of the search box a problem belongs to. */
enum class SearchField { Need, Where }

/** A problem with the search, shown under the box and tied to its field. */
data class SearchError(val field: SearchField, val message: String)

/** The one search box on Find: what was typed, the picked service, Where, and what is happening. */
data class SearchState(
    val text: String = "",
    val categorySlug: String? = null,
    val location: PickedLocation? = null,
    val whereOpen: Boolean = false,
    val asking: Boolean = false,
    val error: SearchError? = null,
    /** Bumped when the box should take focus (a service picked, "Ask again"). */
    val focusRequests: Int = 0,
)

/**
 * Find (web: /find with useRequestSearch): the search box, the running and recent
 * requests, and the popular services. Submitting sends rental searches to the
 * listings and everything else to the request form.
 */
class FindViewModel(
    private val api: FindApi,
    hub: RealtimeHub,
    private val saved: SavedStateHandle,
) : ViewModel() {
    val categories = Loader(viewModelScope) { api.categories() }.refreshOn(hub.resyncs)

    private val requestEvents = setOf(RealtimeEvent.SessionUpdated, RealtimeEvent.MatchFound, RealtimeEvent.MatchHired)
    val requests = Loader(viewModelScope) { api.requests() }
        .refreshOn(hub.resyncs)
        .refreshOn(hub.events.filter { it.type in requestEvents })

    private val mutable = MutableStateFlow(
        SearchState(
            text = saved.get<String>(KeyText).orEmpty(),
            categorySlug = saved.get<String>(KeyCategory),
            location = saved.get<String>(KeyLocation)?.let(::decodeLocation),
        ),
    )
    val state: StateFlow<SearchState> = mutable.asStateFlow()

    val nearby = NearbyTeaser(api, viewModelScope)

    private val routes = Channel<String>(Channel.BUFFERED)

    /** Screens to open, once each (the form, or the rentals search). */
    val navigation: Flow<String> = routes.receiveAsFlow()

    init {
        refreshNearby()
    }

    /** The service picked, from the loaded list or the fallback tiles. */
    fun category(slug: String? = mutable.value.categorySlug): FindCategory? {
        val list = (categories.state.value as? Load.Ready)?.value ?: FallbackCategories
        return slug?.let { wanted -> list.find { it.slug == wanted } }
    }

    fun text(value: String) {
        val picked = category()
        // Typing over the service name means they moved on from that service.
        val keepsService = picked != null && value.startsWith(picked.nameEn, ignoreCase = true)
        update { current ->
            current.copy(
                text = value.take(MaxRequestLength),
                categorySlug = if (keepsService) current.categorySlug else null,
                error = current.error?.takeIf { it.field != SearchField.Need },
            )
        }
    }

    /** A service tile: picks it (keeping any details typed) or, tapped again, drops it. */
    fun pickCategory(picked: FindCategory) {
        val current = mutable.value
        if (current.categorySlug == picked.slug) {
            val emptied = detailsOf(current.text, picked.nameEn).isEmpty()
            update { it.copy(categorySlug = null, text = if (emptied) "" else it.text) }
            return
        }
        val details = detailsOf(current.text, category()?.nameEn)
        update {
            it.copy(
                categorySlug = picked.slug,
                text = if (details.isNotEmpty()) "${picked.nameEn}: $details" else "${picked.nameEn}: ",
                error = it.error?.takeIf { error -> error.field != SearchField.Need },
                focusRequests = it.focusRequests + 1,
            )
        }
    }

    /**
     * "Ask again" on a past request: the same service with the box ready for new
     * details. The old summary is written about the request, so it is not copied.
     */
    fun askAgain(categoryName: String) {
        val list = (categories.state.value as? Load.Ready)?.value.orEmpty()
        val picked = list.find { it.nameEn == categoryName }
        update {
            it.copy(
                categorySlug = picked?.slug,
                text = picked?.let { service -> "${service.nameEn}: " }.orEmpty(),
                error = null,
                focusRequests = it.focusRequests + 1,
            )
        }
    }

    fun toggleWhere() = update { it.copy(whereOpen = !it.whereOpen) }

    /** A place chosen in the Where panel, or found quietly because location was already allowed. */
    fun location(value: PickedLocation, fromPanel: Boolean = true) {
        if (!fromPanel && mutable.value.location != null) return
        update {
            it.copy(
                location = value,
                whereOpen = if (fromPanel) false else it.whereOpen,
                error = it.error?.takeIf { error -> error.field != SearchField.Where },
            )
        }
    }

    /** "Find help": checks the box, asks whether it is a rental, then hands over to the form. */
    fun submit() {
        val current = mutable.value
        if (current.asking) return
        val picked = category()
        val details = detailsOf(current.text, picked?.nameEn)
        if (picked == null && details.length < 3) {
            update {
                it.copy(
                    error = SearchError(
                        SearchField.Need,
                        "Say what you need in a few words, or tap one of the services below.",
                    ),
                    focusRequests = it.focusRequests + 1,
                )
            }
            return
        }
        viewModelScope.launch {
            if (picked == null && openedRentals(details, current.location)) return@launch
            val location = mutable.value.location
            if (location == null) {
                update {
                    it.copy(
                        whereOpen = true,
                        error = SearchError(
                            SearchField.Where,
                            "Where should they come? Use your location or type your area.",
                        ),
                    )
                }
                return@launch
            }
            update { it.copy(error = null) }
            val need = requestText(current.text, picked)
            PendingSearches.save(
                PendingSearch(
                    text = need,
                    location = location,
                    // Only an amount written in the request ("I have 18000 budget"); otherwise no budget.
                    budgetRwf = budgetFromText(need),
                    categorySlug = picked?.slug,
                    autoSubmit = true,
                ),
            )
            routes.send(Routes.newRequest())
        }
    }

    /**
     * Renting is a search, not a job: rental text goes straight to the listings.
     * If the engine can't answer, the search carries on as a service.
     */
    private suspend fun openedRentals(details: String, location: PickedLocation?): Boolean {
        update { it.copy(asking = true) }
        val answer = try {
            api.ask(details)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        } finally {
            update { it.copy(asking = false) }
        }
        if (answer !is ListingAsk.Rental) return false
        update { it.copy(error = null) }
        routes.send(
            Routes.rentalSearch(q = details, kind = answer.filters.kind, lat = location?.lat, lng = location?.lng),
        )
        return true
    }

    fun refresh() {
        requests.refresh()
        categories.refresh()
    }

    private fun update(change: (SearchState) -> SearchState) {
        mutable.update(change)
        val now = mutable.value
        saved[KeyText] = now.text
        saved[KeyCategory] = now.categorySlug
        saved[KeyLocation] = now.location?.let { HafiJson.encodeToString(PickedLocation.serializer(), it) }
        refreshNearby()
    }

    private fun refreshNearby() {
        val now = mutable.value
        nearby.update(now.text, now.location, now.categorySlug)
    }

    private companion object {
        const val KeyText = "find.text"
        const val KeyCategory = "find.category"
        const val KeyLocation = "find.location"
    }
}

/** A saved place, or null when the saved copy no longer reads (an older app version). */
internal fun decodeLocation(json: String): PickedLocation? =
    runCatching { HafiJson.decodeFromString(PickedLocation.serializer(), json) }.getOrNull()
