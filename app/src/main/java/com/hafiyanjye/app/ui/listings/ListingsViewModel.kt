package com.hafiyanjye.app.ui.listings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hafiyanjye.app.data.api.userMessage
import com.hafiyanjye.app.data.listings.ListingStatusChange
import com.hafiyanjye.app.data.listings.ListingsApi
import com.hafiyanjye.app.data.realtime.RealtimeHub
import com.hafiyanjye.app.data.rentals.ListingDto
import com.hafiyanjye.app.data.rentals.ListingStatus
import com.hafiyanjye.app.ui.Loader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A toast after a status change; [failed] ones read as errors. */
data class ListingsNotice(val message: String, val failed: Boolean = false)

/** The owner's listings with quick pause, go live and delete. */
class ListingsViewModel(private val api: ListingsApi, hub: RealtimeHub) : ViewModel() {

    val listings: Loader<List<ListingDto>> = Loader(viewModelScope) { api.mine() }.refreshOn(hub.resyncs)

    private val _busy = MutableStateFlow<Set<Int>>(emptySet())

    /** Listings with a status change on its way; their buttons wait. */
    val busy: StateFlow<Set<Int>> = _busy.asStateFlow()

    private val notices = Channel<ListingsNotice>(Channel.BUFFERED)
    val oneOffs: Flow<ListingsNotice> = notices.receiveAsFlow()

    fun refresh() = listings.refresh()

    fun change(listing: ListingDto, status: ListingStatusChange) {
        if (listing.id in _busy.value) return
        _busy.update { it + listing.id }
        viewModelScope.launch {
            try {
                api.setStatus(listing.id, status)
                listings.mutate { rows -> rows.applying(listing.id, status) }
                notices.trySend(ListingsNotice(noticeFor(status)))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                notices.trySend(ListingsNotice(failure.userMessage(), failed = true))
            } finally {
                _busy.update { it - listing.id }
            }
        }
    }
}

/** The list as it is after a status change the engine accepted. */
internal fun List<ListingDto>.applying(id: Int, status: ListingStatusChange): List<ListingDto> = when (status) {
    ListingStatusChange.Deleted -> filterNot { it.id == id }
    ListingStatusChange.Paused -> map { if (it.id == id) it.copy(status = ListingStatus.Paused) else it }
    ListingStatusChange.Active -> map { if (it.id == id) it.copy(status = ListingStatus.Active) else it }
}

/** The web's toasts after a status change. */
internal fun noticeFor(status: ListingStatusChange): String = when (status) {
    ListingStatusChange.Deleted -> "Listing deleted"
    ListingStatusChange.Paused -> "Paused: people can't find it now"
    ListingStatusChange.Active -> "Live again"
}

/** "2 live · 1 paused", or the invitation when there are none (web: the page's sub-heading). */
internal fun listingsSummary(listings: List<ListingDto>): String {
    if (listings.isEmpty()) return "Rent out a car or a home to people nearby."
    val live = listings.count { it.status == ListingStatus.Active }
    return "$live live · ${listings.size - live} paused"
}
