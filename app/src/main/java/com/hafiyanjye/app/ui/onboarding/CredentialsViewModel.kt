package com.hafiyanjye.app.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hafiyanjye.app.AppContainer
import com.hafiyanjye.app.core.Tone
import com.hafiyanjye.app.data.api.userMessage
import com.hafiyanjye.app.data.onboarding.DocumentDetailsInput
import com.hafiyanjye.app.data.onboarding.DocumentKind
import com.hafiyanjye.app.data.onboarding.DocumentStatus
import com.hafiyanjye.app.data.onboarding.WorkDocument
import com.hafiyanjye.app.data.onboarding.onboardingApi
import com.hafiyanjye.app.data.pings.WorkProfile
import com.hafiyanjye.app.data.talent.TalentRules
import com.hafiyanjye.app.data.talent.talentApi
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

/** What the documents screen loads: the files, the profile (for its services) and whether a studio CV exists. */
data class CredentialsData(
    val documents: List<WorkDocument>,
    val profile: WorkProfile?,
    /** Null when the CV studio couldn't be asked; then neither studio button shows. */
    val studioReady: Boolean?,
) {
    val certificates: Int get() = documents.count { it.kind == DocumentKind.Certificate }
    val hasCv: Boolean get() = documents.any { it.kind == DocumentKind.Cv }
    val canAddCertificate: Boolean get() = certificates < MAX_CERTIFICATES

    companion object {
        /** Engine MAX_CERTIFICATES. */
        const val MAX_CERTIFICATES = 5
    }
}

/** An upload on its way: sending bytes, then waiting while the AI reads the file. */
data class DocumentUpload(val kind: DocumentKind, val progress: Float = 0f) {
    val reading: Boolean get() = progress >= 1f
}

/** A failed upload kept so "Try again" sends the same file without picking it again. */
class FailedUpload(val kind: DocumentKind, val file: DocumentFile?, val message: String)

/** A document file downloaded to show in the app or hand to another app. */
class OpenedDocument(val document: WorkDocument, val bytes: ByteArray, val external: Boolean)

data class CredentialsUi(
    val upload: DocumentUpload? = null,
    val failed: FailedUpload? = null,
    val savingId: Int? = null,
    val detailsError: String? = null,
    val removingId: Int? = null,
    val openingId: Int? = null,
    val opened: OpenedDocument? = null,
) {
    val uploading: Boolean get() = upload != null
}

/** A short message for the toaster. */
data class Notice(val text: String, val tone: Tone = Tone.Neutral)

/**
 * The job seeker's CV and certificates (web: CredentialsPanel): upload (the AI
 * reads each one), status in words, details when it couldn't be checked,
 * view, remove.
 */
class CredentialsViewModel(private val container: AppContainer) : ViewModel() {
    private val api = container.onboardingApi()
    private val talent = container.talentApi()

    private val _ui = MutableStateFlow(CredentialsUi())
    val ui: StateFlow<CredentialsUi> = _ui.asStateFlow()

    private val notices = Channel<Notice>(Channel.BUFFERED)
    val notice: Flow<Notice> = notices.receiveAsFlow()

    val loader: Loader<CredentialsData> = Loader(viewModelScope) {
        val studio = runCatching { TalentRules.hasStudioCv(talent.me()) }.getOrNull()
        CredentialsData(documents = api.documents(), profile = api.profile(), studioReady = studio)
    }.refreshOn(container.hub.resyncs)

    /** Reads the picked file, sends it, and says what the AI check made of it. */
    fun upload(kind: DocumentKind, read: suspend () -> DocumentFile) {
        if (_ui.value.uploading) return
        _ui.update { it.copy(upload = DocumentUpload(kind), failed = null) }
        viewModelScope.launch {
            val file = try {
                read()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _ui.update { it.copy(upload = null, failed = FailedUpload(kind, null, error.userMessage())) }
                return@launch
            }
            send(kind, file)
        }
    }

    /** Sends the last failed file again. */
    fun retry() {
        val failed = _ui.value.failed ?: return
        val file = failed.file ?: return
        if (_ui.value.uploading) return
        _ui.update { it.copy(upload = DocumentUpload(failed.kind), failed = null) }
        viewModelScope.launch { send(failed.kind, file) }
    }

    fun dismissFailure() = _ui.update { it.copy(failed = null) }

    private suspend fun send(kind: DocumentKind, file: DocumentFile) {
        try {
            val document = api.uploadDocument(file.bytes, file.contentType, kind) { sent ->
                _ui.update { state -> state.copy(upload = state.upload?.copy(progress = sent)) }
            }
            loader.refresh()
            report(document, kind)?.let { problem ->
                _ui.update { it.copy(failed = FailedUpload(kind, null, problem)) }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            _ui.update { it.copy(failed = FailedUpload(kind, file, error.userMessage())) }
        } finally {
            _ui.update { it.copy(upload = null) }
        }
    }

    /** Toasts a good result; answers the problem to show when the file couldn't be read. */
    private fun report(document: WorkDocument, kind: DocumentKind): String? = when (document.status) {
        DocumentStatus.Checked -> {
            notices.trySend(Notice("${document.title}: checked.", Tone.Accent))
            null
        }
        DocumentStatus.SelfDeclared -> {
            notices.trySend(Notice("Saved as self-declared. Add the details to be clear.", Tone.Accent))
            null
        }
        DocumentStatus.Unreadable -> document.checkNote ?: "We couldn't read that ${kind.word}."
    }

    /** "Use studio CV": the engine renders the studio CV as a PDF and puts it on the work profile. */
    fun attachStudioCv() {
        if (_ui.value.uploading) return
        _ui.update { it.copy(upload = DocumentUpload(DocumentKind.Cv, progress = 1f), failed = null) }
        viewModelScope.launch {
            try {
                val document = api.attachStudioCv()
                loader.refresh()
                when (document.status) {
                    DocumentStatus.Checked -> notices.trySend(
                        Notice("Your CV is on your job seeker profile.", Tone.Accent),
                    )
                    DocumentStatus.SelfDeclared -> notices.trySend(
                        Notice(document.checkNote ?: "Your CV is on your job seeker profile, saved as self-declared."),
                    )
                    DocumentStatus.Unreadable -> notices.trySend(
                        Notice(document.checkNote ?: "We couldn't read your CV.", Tone.Danger),
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _ui.update { it.copy(failed = FailedUpload(DocumentKind.Cv, null, error.userMessage())) }
            } finally {
                _ui.update { it.copy(upload = null) }
            }
        }
    }

    /** Saves typed details; the document becomes self-declared. [onSaved] closes the form. */
    fun saveDetails(input: DocumentDetailsInput, onSaved: () -> Unit) {
        if (_ui.value.savingId != null) return
        _ui.update { it.copy(savingId = input.id, detailsError = null) }
        viewModelScope.launch {
            try {
                val saved = api.updateDocument(input)
                loader.mutate { data ->
                    data.copy(documents = data.documents.map { if (it.id == saved.id) saved else it })
                }
                notices.trySend(Notice("Details saved.", Tone.Accent))
                onSaved()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _ui.update { it.copy(detailsError = error.userMessage()) }
            } finally {
                _ui.update { it.copy(savingId = null) }
            }
        }
    }

    fun clearDetailsError() = _ui.update { it.copy(detailsError = null) }

    /** Removes the document and its file, after the screen asked to be sure. */
    fun remove(document: WorkDocument, onDone: () -> Unit) {
        if (_ui.value.removingId != null) return
        _ui.update { it.copy(removingId = document.id) }
        viewModelScope.launch {
            try {
                api.removeDocument(document.id)
                loader.mutate { data -> data.copy(documents = data.documents.filter { it.id != document.id }) }
                notices.trySend(Notice("Removed."))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                notices.trySend(Notice(error.userMessage(), Tone.Danger))
            } finally {
                _ui.update { it.copy(removingId = null) }
                onDone()
            }
        }
    }

    /** Downloads the private file with the session, to view here or ([external]) open in another app. */
    fun open(document: WorkDocument, external: Boolean) {
        if (_ui.value.openingId != null) return
        _ui.update { it.copy(openingId = document.id) }
        viewModelScope.launch {
            try {
                val bytes = api.documentFile(document)
                _ui.update { it.copy(opened = OpenedDocument(document, bytes, external)) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                notices.trySend(Notice(error.userMessage(), Tone.Danger))
            } finally {
                _ui.update { it.copy(openingId = null) }
            }
        }
    }

    fun closeOpened() = _ui.update { it.copy(opened = null) }

    fun say(text: String, tone: Tone = Tone.Neutral) {
        notices.trySend(Notice(text, tone))
    }
}
