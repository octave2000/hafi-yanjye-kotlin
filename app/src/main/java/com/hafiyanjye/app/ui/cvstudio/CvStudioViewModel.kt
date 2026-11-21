package com.hafiyanjye.app.ui.cvstudio

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hafiyanjye.app.BuildConfig
import com.hafiyanjye.app.core.Tone
import com.hafiyanjye.app.data.api.ApiException
import com.hafiyanjye.app.data.api.userMessage
import com.hafiyanjye.app.data.cvstudio.AttachedCvStatus
import com.hafiyanjye.app.data.cvstudio.CvStudioApi
import com.hafiyanjye.app.data.cvstudio.CvStyles
import com.hafiyanjye.app.data.realtime.RealtimeHub
import com.hafiyanjye.app.data.talent.CvStyle
import com.hafiyanjye.app.data.talent.TalentApi
import com.hafiyanjye.app.data.talent.TalentChanges
import com.hafiyanjye.app.data.talent.TalentPatch
import com.hafiyanjye.app.data.talent.TalentPublicDto
import com.hafiyanjye.app.ui.Load
import com.hafiyanjye.app.ui.Loader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Autosave of the look: [Pending] while a change waits out the debounce. */
internal enum class SaveState { Idle, Pending, Saving, Saved, Failed }

/** Which export action is running; one at a time, each with its own spinner. */
internal enum class StudioBusy { Download, SharePdf, CopyLink, Attach }

internal data class StudioUi(
    /** The look shown and being saved; null until the profile has loaded. */
    val style: CvStyle? = null,
    val save: SaveState = SaveState.Idle,
    val busy: StudioBusy? = null,
)

/** One-off things the screen does for the ViewModel: it holds the Activity and the clipboard. */
internal sealed interface StudioEvent {
    data class Toast(val message: String, val tone: Tone) : StudioEvent
    data class Launch(val intent: Intent) : StudioEvent
    data class CopyLink(val url: String) : StudioEvent

    /** Android 8–9: ask where to save the PDF (no Downloads access without a permission). */
    data class PickSaveLocation(val fileName: String) : StudioEvent
}

private const val SAVE_DEBOUNCE_MS = 600L
private const val SAVED_MARK_MS = 1800L

/** The CV studio: the profile, the look kept locally and autosaved, and the PDF exports. */
internal class CvStudioViewModel(
    private val talent: TalentApi,
    private val studio: CvStudioApi,
    private val app: Application,
    hub: RealtimeHub,
) : ViewModel() {
    val profile = Loader(viewModelScope) { talent.me() }
        .refreshOn(hub.resyncs)
        .refreshOn(TalentChanges.saved)

    private val _ui = MutableStateFlow(StudioUi())
    val ui: StateFlow<StudioUi> = _ui.asStateFlow()

    private val _events = Channel<StudioEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var savedStyle: CvStyle? = null
    private var saveJob: Job? = null
    private var pickedPdf: ByteArray? = null

    init {
        viewModelScope.launch {
            profile.state.collect { state ->
                val loaded = (state as? Load.Ready)?.value ?: return@collect
                if (_ui.value.style == null) {
                    val style = CvStyles.normalized(loaded.style)
                    savedStyle = style
                    _ui.update { it.copy(style = style) }
                }
            }
        }
    }

    fun change(transform: (CvStyle) -> CvStyle) {
        val current = _ui.value.style ?: return
        val next = transform(current)
        if (next == current) return
        _ui.update { it.copy(style = next, save = SaveState.Pending) }
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(SAVE_DEBOUNCE_MS)
            persist(next)
        }
    }

    fun retrySave() {
        val style = _ui.value.style ?: return
        saveJob?.cancel()
        saveJob = viewModelScope.launch { persist(style) }
    }

    private suspend fun persist(style: CvStyle) {
        if (style == savedStyle) {
            _ui.update { it.copy(save = SaveState.Idle) }
            return
        }
        val previous = savedStyle
        savedStyle = style
        _ui.update { it.copy(save = SaveState.Saving) }
        try {
            talent.update(CvStyles.saveBody(style))
        } catch (error: CancellationException) {
            // A newer change took over; it saves the whole look again.
            savedStyle = previous
            throw error
        } catch (error: Exception) {
            savedStyle = previous
            _ui.update { it.copy(save = SaveState.Failed) }
            _events.trySend(StudioEvent.Toast(error.userMessage(), Tone.Danger))
            return
        }
        TalentChanges.notifySaved()
        _ui.update { it.copy(save = SaveState.Saved) }
        delay(SAVED_MARK_MS)
        _ui.update { if (it.save == SaveState.Saved) it.copy(save = SaveState.Idle) else it }
    }

    /** Waits for (or forces) the look's save, so the engine renders what the person sees. */
    private suspend fun flushSave() {
        val style = _ui.value.style ?: return
        if (style == savedStyle) return
        saveJob?.cancel()
        persist(style)
    }

    private fun run(kind: StudioBusy, block: suspend () -> Unit) {
        if (_ui.value.busy != null) return
        _ui.update { it.copy(busy = kind) }
        viewModelScope.launch {
            try {
                block()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _events.trySend(StudioEvent.Toast(error.userMessage(), Tone.Danger))
            } finally {
                _ui.update { it.copy(busy = null) }
            }
        }
    }

    private fun fileName(): String {
        val name = (profile.state.value as? Load.Ready)?.value?.fullName.orEmpty()
        return CvStyles.pdfFileName(name)
    }

    fun downloadPdf() = run(StudioBusy.Download) {
        val bytes = studio.pdf(_ui.value.style?.accent)
        val name = fileName()
        if (CvFiles.canSaveToDownloads) {
            withContext(Dispatchers.IO) { CvFiles.saveToDownloads(app, bytes, name) }
            _events.trySend(StudioEvent.Toast("Saved to Downloads as $name", Tone.Accent))
        } else {
            pickedPdf = bytes
            _events.trySend(StudioEvent.PickSaveLocation(name))
        }
    }

    /** The place picked on Android 8–9, or null when the picker was closed. */
    fun savePickedPdf(uri: Uri?) {
        val bytes = pickedPdf ?: return
        pickedPdf = null
        if (uri == null) return
        run(StudioBusy.Download) {
            withContext(Dispatchers.IO) { CvFiles.writeTo(app, uri, bytes) }
            _events.trySend(StudioEvent.Toast("Your CV is saved", Tone.Accent))
        }
    }

    fun sharePdf() = run(StudioBusy.SharePdf) {
        val bytes = studio.pdf(_ui.value.style?.accent)
        val intent = withContext(Dispatchers.IO) { CvFiles.shareIntent(app, bytes, fileName()) }
        _events.trySend(StudioEvent.Launch(intent))
    }

    /** Turns the public link on when it's off (as the web's Share does), then copies it. */
    fun copyLink() {
        val current = (profile.state.value as? Load.Ready)?.value ?: return
        val url = CvStyles.publicUrl(BuildConfig.WEB_BASE_URL, current.id)
        if (url == null) {
            _events.trySend(StudioEvent.Toast("Sharing isn't set up in this version of the app.", Tone.Danger))
            return
        }
        run(StudioBusy.CopyLink) {
            if (!current.isPublic) {
                val updated = talent.update(TalentPatch(isPublic = true))
                profile.mutate { updated }
            }
            _events.trySend(StudioEvent.CopyLink(url))
        }
    }

    fun attachToWorkProfile() = run(StudioBusy.Attach) {
        flushSave()
        val attached = studio.attachToWorkProfile()
        val message = when (attached.status) {
            AttachedCvStatus.Checked -> StudioEvent.Toast(
                "Your CV is on your job seeker profile. Checked by AI.",
                Tone.Accent,
            )
            AttachedCvStatus.SelfDeclared -> StudioEvent.Toast(
                "Your CV is on your job seeker profile. ${attached.checkNote ?: "Saved as self-declared."}",
                Tone.Accent,
            )
            AttachedCvStatus.Unreadable -> StudioEvent.Toast(
                attached.checkNote ?: "We couldn't read your CV.",
                Tone.Danger,
            )
        }
        _events.trySend(message)
    }
}

/** A public CV by profile id; private and unknown ids both load as null ("not found"), as on the web. */
internal class PublicCvViewModel(private val api: CvStudioApi, private val id: Int, hub: RealtimeHub) : ViewModel() {
    val profile = Loader<TalentPublicDto?>(viewModelScope) {
        if (id <= 0) {
            null
        } else {
            try {
                api.publicProfile(id)
            } catch (error: ApiException) {
                if (error.status == 404) null else throw error
            }
        }
    }.refreshOn(hub.resyncs)
}
