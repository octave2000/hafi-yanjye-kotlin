package com.hafiyanjye.app.ui.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hafiyanjye.app.data.api.ApiException
import com.hafiyanjye.app.data.api.userMessage
import com.hafiyanjye.app.data.browse.BrowseApi
import com.hafiyanjye.app.data.browse.BrowseFilters
import com.hafiyanjye.app.data.browse.PublicWorker
import com.hafiyanjye.app.data.browse.SearchSort
import com.hafiyanjye.app.data.browse.WorkerSearch
import com.hafiyanjye.app.data.match.JobCategory
import com.hafiyanjye.app.data.realtime.RealtimeHub
import com.hafiyanjye.app.ui.Loader
import com.hafiyanjye.app.ui.location.PickedLocation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The services list. [results] holds the first page plus every "Show more"
 * page; it stays on screen while a new filter loads, so the list never blanks.
 */
internal data class ServicesState(
    val categories: List<JobCategory> = emptyList(),
    val results: WorkerSearch? = null,
    val loading: Boolean = true,
    /** The last search failed: a panel without results, a banner over older ones. */
    val error: String? = null,
    val moreLoading: Boolean = false,
    val moreError: String? = null,
) {
    /** Listed services, plus the chosen one when someone registered it and it is not listed yet. */
    fun services(selected: String?): List<JobCategory> = categories.filter { it.listed || it.slug == selected }
}

/** Services list (web: app/services/page.tsx): filters, sort, location, paging and direct hiring. */
internal class ServicesViewModel(
    private val api: BrowseApi,
    hub: RealtimeHub,
    categoryId: Int?,
    q: String?,
) : ViewModel() {
    private val _filters = MutableStateFlow(BrowseFilters(q = q.orEmpty().take(BrowseFilters.MaxQueryLength)))
    val filters: StateFlow<BrowseFilters> = _filters.asStateFlow()

    /** What is in the search box; applied after a pause in typing or on Search. */
    private val _typed = MutableStateFlow(_filters.value.q)
    val typed: StateFlow<String> = _typed.asStateFlow()

    private val _location = MutableStateFlow<PickedLocation?>(null)
    val location: StateFlow<PickedLocation?> = _location.asStateFlow()

    private val _state = MutableStateFlow(ServicesState())
    val state: StateFlow<ServicesState> = _state.asStateFlow()

    val hire = HireController(api, viewModelScope)

    /** The route names a service by id; it becomes a slug once the services have loaded. */
    private var pendingCategoryId = categoryId

    /** After "Stop using my location" the phone's position is not put back on its own. */
    private var locationDismissed = false
    private var search: Job? = null
    private var nextPage: Job? = null
    private var typing: Job? = null

    init {
        load()
        viewModelScope.launch { hub.resyncs.collect { load() } }
    }

    /** Loads the first page for the current filters, cancelling any search or page still running. */
    fun load() {
        search?.cancel()
        nextPage?.cancel()
        _state.update { it.copy(loading = true, error = null, moreLoading = false, moreError = null) }
        search = viewModelScope.launch {
            try {
                val categories = _state.value.categories.ifEmpty { api.categories() }
                resolvePendingCategory(categories)
                val page = api.search(_filters.value.toQuery(_location.value))
                _state.update { it.copy(categories = categories, results = page, loading = false) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                _state.update { it.copy(loading = false, error = failure.userMessage()) }
            }
        }
    }

    private fun resolvePendingCategory(categories: List<JobCategory>) {
        val id = pendingCategoryId ?: return
        pendingCategoryId = null
        val slug = categories.firstOrNull { it.id == id }?.slug ?: return
        _filters.update { it.copy(service = slug) }
    }

    fun update(change: (BrowseFilters) -> BrowseFilters) {
        val next = change(_filters.value)
        if (next == _filters.value) return
        _filters.value = next
        load()
    }

    fun type(text: String) {
        _typed.value = text.take(BrowseFilters.MaxQueryLength)
        typing?.cancel()
        typing = viewModelScope.launch {
            delay(TypingPauseMs)
            applySearch()
        }
    }

    /** Search now (the Search button or the keyboard's search key). */
    fun applySearch() {
        typing?.cancel()
        val text = _typed.value.trim()
        update { if (it.q.trim() == text) it else it.copy(q = text) }
    }

    fun clearSearch() {
        _typed.value = ""
        applySearch()
    }

    fun setLocation(place: PickedLocation) {
        locationDismissed = false
        if (place == _location.value) return
        _location.value = place
        load()
    }

    /** The phone's position found on its own: used only when nothing was chosen or stopped. */
    fun foundLocation(place: PickedLocation) {
        if (_location.value == null && !locationDismissed) setLocation(place)
    }

    /** Distance filters and "closest first" need a location, so they go with it. */
    fun stopUsingLocation() {
        locationDismissed = true
        _location.value = null
        _filters.update { it.copy(km = null, sort = it.sort.takeUnless { sort -> sort == SearchSort.Distance }) }
        load()
    }

    fun more() {
        val current = _state.value
        val page = current.results ?: return
        if (current.loading || current.moreLoading || page.items.size >= page.total) return
        _state.update { it.copy(moreLoading = true, moreError = null) }
        nextPage = viewModelScope.launch {
            try {
                val next = api.search(_filters.value.toQuery(_location.value, offset = page.items.size))
                _state.update { state ->
                    val shown = state.results ?: return@update state.copy(moreLoading = false)
                    val items = (shown.items + next.items).distinctBy { it.id }
                    state.copy(results = shown.copy(items = items, total = next.total), moreLoading = false)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                _state.update { it.copy(moreLoading = false, moreError = failure.userMessage()) }
            }
        }
    }

    private companion object {
        const val TypingPauseMs = 300L
    }
}

/** Reviews are shown a few at a time; the engine sends up to 50 at once. */
internal const val ReviewsPageSize = 5

/** A public profile (web: app/providers/[id]/page.tsx). A null profile means the person is gone. */
internal class PersonViewModel(private val api: BrowseApi, hub: RealtimeHub, private val id: Int) : ViewModel() {
    val profile = Loader(viewModelScope) { loadProfile() }.refreshOn(hub.resyncs)
    val reviews = Loader(viewModelScope) { if (id > 0) api.reviews(id) else emptyList() }.refreshOn(hub.resyncs)
    val hire = HireController(api, viewModelScope)

    private val _reviewsShown = MutableStateFlow(ReviewsPageSize)
    val reviewsShown: StateFlow<Int> = _reviewsShown.asStateFlow()

    /** The photo open full screen (its index), kept here so a rotation keeps it open. */
    private val _viewing = MutableStateFlow<Int?>(null)
    val viewing: StateFlow<Int?> = _viewing.asStateFlow()

    private suspend fun loadProfile(): PublicWorker? {
        if (id <= 0) return null
        return try {
            api.person(id)
        } catch (missing: ApiException) {
            if (missing.status == NotFound) null else throw missing
        }
    }

    fun refresh() {
        profile.refresh()
        reviews.refresh()
    }

    fun showMoreReviews() = _reviewsShown.update { it + ReviewsPageSize }

    fun view(photo: Int?) {
        _viewing.value = photo
    }

    private companion object {
        const val NotFound = 404
    }
}
