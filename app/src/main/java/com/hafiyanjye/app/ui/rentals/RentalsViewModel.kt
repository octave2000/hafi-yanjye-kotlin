package com.hafiyanjye.app.ui.rentals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hafiyanjye.app.data.api.ApiException
import com.hafiyanjye.app.data.api.userMessage
import com.hafiyanjye.app.data.location.Geocoding
import com.hafiyanjye.app.data.realtime.RealtimeHub
import com.hafiyanjye.app.data.rentals.ListingAskKind
import com.hafiyanjye.app.data.rentals.ListingCardDto
import com.hafiyanjye.app.data.rentals.ListingFilters
import com.hafiyanjye.app.data.rentals.ListingKind
import com.hafiyanjye.app.data.rentals.ListingPoint
import com.hafiyanjye.app.data.rentals.RentalsApi
import com.hafiyanjye.app.ui.Load
import com.hafiyanjye.app.ui.Loader
import com.hafiyanjye.app.ui.location.PickedLocation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Where distances are measured from. `label` is null for a point handed over without a name. */
data class RentalPlace(val lat: Double, val lng: Double, val label: String?)

/** How the rentals screen was opened: the tab, or a hand-off from the find feature. */
data class RentalStart(
    val q: String = "",
    val kind: ListingKind = ListingKind.Car,
    val place: RentalPlace? = null,
)

/**
 * Everything one search is made of; the results follow it. `near` is a place
 * named in the text that is still being looked up.
 */
data class RentalQuery(
    val filters: ListingFilters,
    val place: RentalPlace? = null,
    val q: String = "",
    val near: String? = null,
)

/** The pages loaded so far for one query. */
data class RentalResults(
    val items: List<ListingCardDto>,
    val center: ListingPoint,
    val total: Int,
    val hasMore: Boolean,
    val page: Int,
)

/** What the rentals screen shows. */
data class RentalsUiState(
    val query: RentalQuery,
    val text: String,
    val asking: Boolean = false,
    val askError: String? = null,
    /** The typed text sounds like a job for someone nearby: offer to ask for help instead. */
    val serviceHint: String? = null,
    /** The engine's one line on what it understood. */
    val summary: String? = null,
    val placeNote: String? = null,
    val results: Load<RentalResults> = Load.Loading,
    val loadingMore: Boolean = false,
    val moreError: String? = null,
)

/** Rental search: the ask box, filters, where from, and paged results nearest first (web: /rentals). */
class RentalsViewModel(
    private val api: RentalsApi,
    private val geocoding: Geocoding,
    hub: RealtimeHub,
    start: RentalStart,
) : ViewModel() {

    private val _state = MutableStateFlow(
        RentalsUiState(
            query = RentalQuery(filters = ListingFilters(start.kind), place = start.place),
            text = start.q,
        ),
    )
    val state: StateFlow<RentalsUiState> = _state.asStateFlow()

    private var searchJob: Job? = null
    private var moreJob: Job? = null
    private var lookUpJob: Job? = null
    private var askJob: Job? = null

    init {
        load(refresh = false)
        if (start.q.isNotBlank()) submit()
        viewModelScope.launch { hub.resyncs.collect { refresh() } }
    }

    fun setText(text: String) {
        _state.update { it.copy(text = text, serviceHint = null, askError = null) }
    }

    /** Reads the typed text: a rental search sets the filters and place, anything else offers help nearby. */
    fun submit() {
        if (askJob?.isActive == true) return
        val text = _state.value.text.trim()
        _state.update { it.copy(serviceHint = null, askError = null) }
        if (text.length < 2) {
            _state.update { it.copy(query = it.query.copy(q = ""), summary = null) }
            return
        }
        _state.update { it.copy(asking = true) }
        askJob = viewModelScope.launch {
            try {
                val answer = api.ask(text)
                val filters = answer.filters
                if (answer.kind == ListingAskKind.Service || filters == null) {
                    _state.update { it.copy(serviceHint = text) }
                } else {
                    val near = answer.place?.trim()?.ifEmpty { null }
                    val current = _state.value.query
                    setQuery(
                        RentalQuery(
                            filters = filters.cleaned(),
                            place = if (near != null) null else current.place,
                            q = text,
                            near = near,
                        ),
                        summary = answer.summary,
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _state.update { it.copy(askError = error.userMessage()) }
            } finally {
                _state.update { it.copy(asking = false) }
            }
        }
    }

    /** Cars or homes. Prices and details don't carry over between the two. */
    fun setKind(kind: ListingKind) {
        val query = _state.value.query
        if (kind == query.filters.kind) return
        setQuery(query.copy(filters = ListingFilters(kind), q = ""))
    }

    fun removeFilter(key: FilterKey) {
        val query = _state.value.query
        setQuery(query.copy(filters = query.filters.without(key)), summary = _state.value.summary)
    }

    /** Back to every listing of this kind. */
    fun clearFilters() {
        val query = _state.value.query
        setQuery(query.copy(filters = ListingFilters(query.filters.kind), q = ""))
    }

    fun applyFilters(filters: ListingFilters) {
        val query = _state.value.query
        val cleaned = filters.copy(kind = query.filters.kind).cleaned()
        if (cleaned == query.filters) return
        setQuery(query.copy(filters = cleaned))
    }

    /** A place chosen in the picker. */
    fun setPlace(picked: PickedLocation) {
        val note = if (picked.approximate) {
            "We couldn't find \"${picked.label ?: "that place"}\" exactly; distances are approximate."
        } else {
            null
        }
        lookUpJob?.cancel()
        setQuery(
            _state.value.query.copy(place = RentalPlace(picked.lat, picked.lng, picked.label), near = null),
            summary = _state.value.summary,
            placeNote = note,
        )
    }

    /** The phone's position, used only while nothing else says where to measure from (as on the web). */
    fun useMyLocation(found: PickedLocation) {
        val query = _state.value.query
        if (query.place != null || query.near != null) return
        setQuery(
            query.copy(place = RentalPlace(found.lat, found.lng, "My location")),
            summary = _state.value.summary,
            placeNote = _state.value.placeNote,
        )
    }

    fun refresh() {
        if (_state.value.query.near != null) return
        load(refresh = true)
    }

    fun loadMore() {
        val shown = (_state.value.results as? Load.Ready)?.value ?: return
        if (!shown.hasMore || moreJob?.isActive == true) return
        val query = _state.value.query
        _state.update { it.copy(loadingMore = true, moreError = null) }
        moreJob = viewModelScope.launch {
            try {
                val next = api.search(query.filters, query.place?.lat, query.place?.lng, shown.page + 1)
                _state.update { state ->
                    val ready = state.results as? Load.Ready ?: return@update state
                    val merged = ready.value.copy(
                        items = (ready.value.items + next.items).distinctBy { it.id },
                        hasMore = next.hasMore,
                        total = next.total,
                        page = shown.page + 1,
                    )
                    state.copy(results = ready.copy(value = merged))
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _state.update { it.copy(moreError = error.userMessage()) }
            } finally {
                _state.update { it.copy(loadingMore = false) }
            }
        }
    }

    private fun setQuery(query: RentalQuery, summary: String? = null, placeNote: String? = null) {
        _state.update {
            it.copy(
                query = query,
                text = query.q,
                summary = summary,
                placeNote = placeNote,
            )
        }
        val near = query.near
        if (near != null) lookUp(near) else load(refresh = false)
    }

    /** Finds a place named in the text, then searches from there (web: `findTypedPlace`). */
    private fun lookUp(near: String) {
        searchJob?.cancel()
        moreJob?.cancel()
        lookUpJob?.cancel()
        _state.update { it.copy(results = Load.Loading, loadingMore = false, moreError = null) }
        lookUpJob = viewModelScope.launch {
            val found = try {
                geocoding.findTypedPlace(near)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
            val note = when {
                found == null -> "We couldn't look up \"$near\", so distances are from Kigali centre."
                found.approximate -> "We couldn't find \"$near\" on the map, so distances are from Kigali centre."
                else -> null
            }
            _state.update { state ->
                state.copy(
                    query = state.query.copy(
                        near = null,
                        place = found?.let { RentalPlace(it.lat, it.lng, near) },
                    ),
                    placeNote = note,
                )
            }
            load(refresh = false)
        }
    }

    private fun load(refresh: Boolean) {
        searchJob?.cancel()
        moreJob?.cancel()
        val query = _state.value.query
        _state.update { state ->
            val shown = state.results
            val next = if (refresh && shown is Load.Ready) {
                shown.copy(refreshing = true, staleError = null)
            } else {
                Load.Loading
            }
            state.copy(results = next, loadingMore = false, moreError = null)
        }
        searchJob = viewModelScope.launch {
            try {
                val found = api.search(query.filters, query.place?.lat, query.place?.lng)
                val results = RentalResults(found.items, found.center, found.total, found.hasMore, page = 0)
                _state.update { it.copy(results = Load.Ready(results)) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _state.update { state ->
                    val shown = state.results
                    val failed = if (shown is Load.Ready) {
                        shown.copy(refreshing = false, staleError = error.userMessage())
                    } else {
                        Load.Failed(error.userMessage())
                    }
                    state.copy(results = failed)
                }
            }
        }
    }
}

/** One listing in full. `null` means it is gone or paused, which the screen explains instead of an error. */
class RentalDetailViewModel(api: RentalsApi, hub: RealtimeHub, listingId: Int) : ViewModel() {
    val listing = Loader(viewModelScope) {
        try {
            api.listing(listingId)
        } catch (missing: ApiException) {
            if (missing.status == 404) null else throw missing
        }
    }.refreshOn(hub.resyncs)
}
