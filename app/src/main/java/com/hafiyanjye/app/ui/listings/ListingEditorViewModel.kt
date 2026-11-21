package com.hafiyanjye.app.ui.listings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hafiyanjye.app.data.api.ApiException
import com.hafiyanjye.app.data.api.userMessage
import com.hafiyanjye.app.data.listings.ListingsApi
import com.hafiyanjye.app.data.session.Session
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicLong

/** Whether the listing asked for can be edited here: deleted ones and other people's cannot. */
enum class EditorStart { Ready, NotFound }

/** One picked photo on its way to the engine. A failed one keeps its place until retried or removed. */
data class PhotoUpload(
    val key: Long,
    val uri: Uri,
    val progress: Float = 0f,
    val error: String? = null,
) {
    val failed: Boolean get() = error != null
}

/** What the listing form shows. */
data class ListingEditorState(
    val draft: ListingDraft = ListingDraft(),
    val problems: Map<DraftField, String> = emptyMap(),
    val uploads: List<PhotoUpload> = emptyList(),
    val saving: Boolean = false,
    /** The engine's refusal, shown above the save button. */
    val saveError: String? = null,
    /** Something differs from what was loaded (or from an empty form). */
    val dirty: Boolean = false,
) {
    val uploading: Int get() = uploads.count { !it.failed }

    /** Photos kept plus those still in flight or failed: what counts towards the maximum. */
    val photoSlotsUsed: Int get() = draft.photos.size + uploads.size
}

/** Something the screen does once: a toast, leaving after a save, scrolling to the first problem. */
sealed interface EditorEvent {
    data class Saved(val listingId: Int, val created: Boolean) : EditorEvent

    data class Toast(val message: String) : EditorEvent

    data class ShowProblem(val field: DraftField) : EditorEvent
}

/** The listing form for a new listing ([listingId] null) or one of the owner's own. */
class ListingEditorViewModel(
    private val listingId: Int?,
    private val api: ListingsApi,
    private val session: Session,
    private val context: Context,
) : ViewModel() {

    private val _state = MutableStateFlow(ListingEditorState())
    val state: StateFlow<ListingEditorState> = _state.asStateFlow()

    private val events = Channel<EditorEvent>(Channel.BUFFERED)
    val oneOffs: Flow<EditorEvent> = events.receiveAsFlow()

    /** The draft as loaded; the form is "changed" when it differs. */
    private var baseline = ListingDraft()
    private val uploadKeys = AtomicLong()

    // Photos go up one at a time in the order picked, as on the web, so a slow
    // network is not split between several of them.
    private val uploadQueue = Mutex()

    val start: Loader<EditorStart> = Loader(viewModelScope) { load() }

    private suspend fun load(): EditorStart {
        if (listingId == null) return EditorStart.Ready
        // A retry after a refresh must not overwrite what the owner typed.
        if (_state.value.dirty) return EditorStart.Ready
        val listing = try {
            api.listing(listingId)
        } catch (missing: ApiException) {
            if (missing.status == 404 || missing.status == 403) return EditorStart.NotFound
            throw missing
        }
        if (!listing.mine) return EditorStart.NotFound
        baseline = ListingDraft.from(listing)
        _state.update { it.copy(draft = baseline) }
        return EditorStart.Ready
    }

    /** Applies a change to the form; messages of the fields it changed go away, as on the web. */
    fun edit(transform: (ListingDraft) -> ListingDraft) {
        if (_state.value.saving) return
        _state.update { current ->
            val draft = transform(current.draft)
            val kept = current.problems.filterKeys { field -> field.valueIn(draft) == field.valueIn(current.draft) }
            current.copy(draft = draft, problems = kept, saveError = null, dirty = isChanged(draft, current.uploads))
        }
    }

    fun save(needsPhone: Boolean) {
        val current = _state.value
        if (current.saving || current.uploading > 0) return
        val problems = current.draft.problems(needsPhone).toMutableMap()
        if (current.uploads.any { it.failed }) {
            problems[DraftField.Photos] = "Retry or remove the photos that didn't upload."
        }
        if (problems.isNotEmpty()) {
            _state.update { it.copy(problems = problems, saveError = null) }
            events.trySend(EditorEvent.ShowProblem(DraftField.entries.first { it in problems }))
            return
        }
        val input = current.draft.toInput(listingId) ?: return
        _state.update { it.copy(saving = true, saveError = null, problems = emptyMap()) }
        viewModelScope.launch {
            try {
                if (needsPhone) session.updateMe(api.savePhone(cleanPhone(current.draft.phone)))
                val saved = api.save(input)
                baseline = ListingDraft.from(saved)
                _state.update { it.copy(saving = false, dirty = false) }
                events.trySend(EditorEvent.Saved(saved.id, created = listingId == null))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                _state.update { it.copy(saving = false, saveError = failure.userMessage()) }
            }
        }
    }

    /** Starts uploading what the photo picker returned, up to the maximum. */
    fun addPhotos(uris: List<Uri>) {
        val current = _state.value
        if (current.saving || uris.isEmpty()) return
        val room = MAX_LISTING_PHOTOS - current.photoSlotsUsed
        if (uris.size > room) events.trySend(EditorEvent.Toast("At most $MAX_LISTING_PHOTOS photos."))
        val added = uris.take(room.coerceAtLeast(0)).map { PhotoUpload(uploadKeys.incrementAndGet(), it) }
        if (added.isEmpty()) return
        _state.update { it.copy(uploads = it.uploads + added, problems = it.problems - DraftField.Photos) }
        refreshDirty()
        added.forEach(::send)
    }

    fun retryUpload(key: Long) {
        val upload = _state.value.uploads.firstOrNull { it.key == key && it.failed } ?: return
        updateUpload(key) { it.copy(progress = 0f, error = null) }
        send(upload)
    }

    fun dropUpload(key: Long) {
        _state.update { current ->
            val uploads = current.uploads.filterNot { it.key == key }
            current.copy(uploads = uploads, problems = current.problems - DraftField.Photos)
        }
        refreshDirty()
    }

    fun movePhoto(index: Int, by: Int) = edit { it.copy(photos = it.photos.moved(index, by)) }

    fun makeCover(photo: String) = edit { it.copy(photos = it.photos.withCover(photo)) }

    fun removePhoto(photo: String) = edit { it.copy(photos = it.photos - photo) }

    private fun send(upload: PhotoUpload) {
        viewModelScope.launch {
            uploadQueue.withLock {
                // Removed while waiting its turn.
                if (_state.value.uploads.none { it.key == upload.key }) return@withLock
                try {
                    val photo = prepareListingPhoto(context, upload.uri)
                    val stored = api.uploadPhoto(photo.jpeg, photo.width, photo.height) { sent ->
                        updateUpload(upload.key) { it.copy(progress = sent) }
                    }
                    finishUpload(upload.key, stored.url)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    val message = if (failure is PhotoProblem) failure.message else failure.userMessage()
                    updateUpload(upload.key) { it.copy(error = message ?: "Couldn't upload that photo.") }
                }
            }
        }
    }

    private fun finishUpload(key: Long, url: String) {
        _state.update { current ->
            if (current.uploads.none { it.key == key }) return@update current
            val photos = (current.draft.photos + url).distinct().take(MAX_LISTING_PHOTOS)
            current.copy(
                draft = current.draft.copy(photos = photos),
                uploads = current.uploads.filterNot { it.key == key },
            )
        }
        refreshDirty()
    }

    private fun updateUpload(key: Long, transform: (PhotoUpload) -> PhotoUpload) {
        _state.update { current ->
            current.copy(uploads = current.uploads.map { if (it.key == key) transform(it) else it })
        }
    }

    private fun refreshDirty() {
        _state.update { it.copy(dirty = isChanged(it.draft, it.uploads)) }
    }

    // Choosing car or home alone is not worth a "discard?" question.
    private fun isChanged(draft: ListingDraft, uploads: List<PhotoUpload>): Boolean {
        if (uploads.isNotEmpty()) return true
        return if (listingId == null) draft.copy(kind = null) != baseline else draft != baseline
    }
}

/** The part of the draft a message is about, to tell whether an edit touched it. */
private fun DraftField.valueIn(draft: ListingDraft): Any? = when (this) {
    DraftField.Kind -> draft.kind
    DraftField.Make -> draft.car.make
    DraftField.Model -> draft.car.model
    DraftField.Year -> draft.car.year
    DraftField.Seats -> draft.car.seats
    DraftField.Title -> draft.title
    DraftField.Description -> draft.description
    DraftField.Price -> draft.price
    DraftField.Photos -> draft.photos
    DraftField.Location -> draft.location
    DraftField.Phone -> draft.phone
}
