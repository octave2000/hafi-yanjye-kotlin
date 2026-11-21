package com.hafiyanjye.app.ui.onboarding

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hafiyanjye.app.AppContainer
import com.hafiyanjye.app.data.api.HafiJson
import com.hafiyanjye.app.data.api.Me
import com.hafiyanjye.app.data.api.userMessage
import com.hafiyanjye.app.data.onboarding.BioSuggestInput
import com.hafiyanjye.app.data.onboarding.BioSuggestion
import com.hafiyanjye.app.data.onboarding.RegisteredService
import com.hafiyanjye.app.data.onboarding.onboardingApi
import com.hafiyanjye.app.data.pings.Availability
import com.hafiyanjye.app.data.pings.AvailabilityInput
import com.hafiyanjye.app.data.pings.WorkCategory
import com.hafiyanjye.app.data.pings.WorkProfile
import com.hafiyanjye.app.data.profile.ProfileApi
import com.hafiyanjye.app.data.session.SessionState
import com.hafiyanjye.app.ui.Loader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Which profile photo action is running. */
enum class AvatarAction { Upload, Remove }

/** Everything the setup screen draws besides the loaded profile and services. */
data class WorkSetupUi(
    val form: WorkForm = WorkForm(),
    val step: SetupStep = SetupStep.WhatYouDo,
    /** The form was filled from the saved profile or the draft; until then the screen shows skeletons. */
    val hydrated: Boolean = false,
    /** The profile exists, so this is editing (and every step can be jumped to). */
    val editing: Boolean = false,
    val saving: Boolean = false,
    val errors: Map<FormField, String> = emptyMap(),
    val goOnline: Boolean = true,
    val writingBio: Boolean = false,
    val addingService: Boolean = false,
    val serviceNotice: String? = null,
    val serviceError: String? = null,
    val avatarAction: AvatarAction? = null,
    val avatarError: String? = null,
    /** Work photos still uploading, shown as placeholders after the saved ones. */
    val photosUploading: Int = 0,
    /** Set once finishing worked: the toast to show on the way to the pings. */
    val finishedMessage: String? = null,
)

/**
 * The work profile wizard (web: provider/onboarding). Each step saves on its
 * own; the profile is created on step 2 with a location. Before it exists the
 * form is kept as a draft on the phone so nothing typed is lost.
 */
class WorkSetupViewModel(private val container: AppContainer, private val me: Me) : ViewModel() {
    private val api = container.onboardingApi()
    private val profileApi = ProfileApi(container.client)
    private val drafts = container.app.getSharedPreferences(DRAFTS, Context.MODE_PRIVATE)
    private val draftKey = "draft-${me.id}"

    private val _ui = MutableStateFlow(WorkSetupUi())
    val ui: StateFlow<WorkSetupUi> = _ui.asStateFlow()

    val profile: Loader<WorkProfile?> = Loader(viewModelScope) {
        api.profile().also(::hydrate)
    }.refreshOn(container.hub.resyncs)

    /** Loaded on its own so a failure only blocks the service picker, as on the web. */
    val categories: Loader<List<WorkCategory>> = Loader(viewModelScope) { api.categories() }

    /** Prefill once: the saved profile first, else the draft, else the account's name and phone. */
    private fun hydrate(saved: WorkProfile?) {
        if (_ui.value.hydrated) {
            if (saved != null) _ui.update { it.copy(editing = true) }
            return
        }
        val form = if (saved != null) WorkForm.fromProfile(saved, me.phone) else draftOrNew()
        _ui.update { it.copy(form = form, hydrated = true, editing = saved != null) }
    }

    private fun draftOrNew(): WorkForm {
        val phone = WorkForm.localPhone(me.phone)
        val draft = drafts.getString(draftKey, null)?.let { raw ->
            runCatching { HafiJson.decodeFromString(WorkForm.serializer(), raw) }.getOrNull()
        }
        return draft?.copy(phone = phone.ifBlank { draft.phone })
            ?: WorkForm(name = me.name.orEmpty(), phone = phone)
    }

    /** Changes the form; clears only the errors of the fields that changed. */
    fun edit(vararg changed: FormField, change: (WorkForm) -> WorkForm) {
        _ui.update { state ->
            state.copy(form = change(state.form), errors = state.errors - changed.toSet() - FormField.Form)
        }
        keepDraft()
    }

    private fun keepDraft() {
        val state = _ui.value
        if (!state.hydrated || state.editing) return
        drafts.edit().putString(draftKey, HafiJson.encodeToString(WorkForm.serializer(), state.form)).apply()
    }

    /** Jumps to a step (the indicator, summary edit buttons, Back); typed input stays in the form. */
    fun goTo(step: SetupStep) {
        if (_ui.value.saving) return
        _ui.update { it.copy(step = step, errors = emptyMap()) }
    }

    fun back(): Boolean {
        val previous = _ui.value.step.previous ?: return false
        goTo(previous)
        return true
    }

    fun setGoOnline(online: Boolean) = _ui.update { it.copy(goOnline = online) }

    /** "Save & continue": validates the step, saves what it holds, then moves on. */
    fun saveAndContinue() {
        val state = _ui.value
        if (state.saving) return
        val problems = state.form.validate(state.step)
        if (problems.isNotEmpty()) {
            _ui.update { it.copy(errors = problems) }
            return
        }
        _ui.update { it.copy(saving = true, errors = emptyMap()) }
        viewModelScope.launch {
            try {
                save(state.step, state.form, state.editing)
                _ui.update { current ->
                    current.copy(step = current.step.next ?: current.step)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _ui.update { it.copy(errors = mapOf(FormField.Form to error.userMessage())) }
            } finally {
                _ui.update { it.copy(saving = false) }
            }
        }
    }

    private suspend fun save(step: SetupStep, form: WorkForm, editing: Boolean) {
        when (step) {
            SetupStep.WhatYouDo -> {
                if (!editing) return
                api.update(WorkSetupBodies.whatYouDo(form))
            }
            SetupStep.WhereYouWork -> {
                val location = form.location ?: return
                api.update(WorkSetupBodies.whereYouWork(form, location))
                if (!editing) {
                    drafts.edit().remove(draftKey).apply()
                    _ui.update { it.copy(editing = true) }
                    // The account now has a work profile and the job seeker role.
                    container.session.refreshMe()
                }
            }
            SetupStep.AboutYou -> {
                api.update(WorkSetupBodies.aboutYou(form))
                if (!WorkForm.samePhone(form.phone, container.currentMe()?.phone ?: me.phone)) {
                    val account = profileApi.update(buildJsonObject { put("phone", WorkForm.cleanPhone(form.phone)) })
                    container.session.updateMe(account)
                }
            }
            SetupStep.Done -> return
        }
        profile.refresh()
    }

    /** "Open my dashboard": sets online or offline, reloads the account, then the screen goes to the pings. */
    fun finish() {
        val state = _ui.value
        if (state.saving) return
        _ui.update { it.copy(saving = true, errors = emptyMap()) }
        viewModelScope.launch {
            try {
                val availability = if (state.goOnline) Availability.Online else Availability.Offline
                api.setAvailability(AvailabilityInput(availability))
                container.session.refreshMe()
                val message = if (state.goOnline) "You're online. Jobs nearby will ping you." else "Profile saved."
                _ui.update { it.copy(finishedMessage = message) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _ui.update { it.copy(errors = mapOf(FormField.Form to error.userMessage())) }
            } finally {
                _ui.update { it.copy(saving = false) }
            }
        }
    }

    /** Adds the typed service and picks it; [onAdded] clears the search box. */
    fun addService(query: String, onAdded: () -> Unit) {
        val state = _ui.value
        val typed = ServiceSearch.typed(query)
        if (state.addingService || typed.length < 2 || state.form.services.size >= WorkForm.MAX_SERVICES) return
        _ui.update { it.copy(addingService = true, serviceError = null, serviceNotice = null) }
        viewModelScope.launch {
            try {
                val result = api.registerService(typed)
                val category = result.category
                categories.mutate { list -> list.filter { it.slug != category.slug } + category }
                edit(FormField.Services) { form ->
                    form.copy(services = (form.services + category.slug).distinct().take(WorkForm.MAX_SERVICES))
                }
                val notice = when (result.kind) {
                    RegisteredService.Kind.Existing ->
                        "We already have this one: ${category.nameEn}. It's picked for you."
                    RegisteredService.Kind.Created -> "Added ${category.nameEn}. Job providers can find you by it now."
                }
                _ui.update { it.copy(serviceNotice = notice) }
                onAdded()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _ui.update { it.copy(serviceError = error.userMessage()) }
            } finally {
                _ui.update { it.copy(addingService = false) }
            }
        }
    }

    fun toggleService(slug: String) {
        _ui.update { it.copy(serviceNotice = null) }
        edit(FormField.Services) { form ->
            when {
                slug in form.services -> form.copy(services = form.services - slug)
                form.services.size < WorkForm.MAX_SERVICES -> form.copy(services = form.services + slug)
                else -> form
            }
        }
    }

    fun clearServiceError() = _ui.update { it.copy(serviceError = null) }

    /** "Write it for me": the AI drafts a bio from the name, services, years and what is typed so far. */
    fun writeBio(onWritten: (String) -> Unit) {
        val state = _ui.value
        val form = state.form
        if (state.writingBio) return
        if (form.services.isEmpty() || form.name.trim().length < 2) {
            val problem = "Add your name and a service first (step 1)."
            _ui.update { it.copy(errors = it.errors + (FormField.Bio to problem)) }
            return
        }
        _ui.update { it.copy(writingBio = true, errors = it.errors - FormField.Bio) }
        viewModelScope.launch {
            try {
                val result = api.suggestBio(
                    BioSuggestInput(
                        displayName = form.name.trim(),
                        categorySlugs = form.services,
                        yearsExperience = (form.yearsValue ?: 0).coerceIn(WorkForm.YEARS_RANGE),
                        notes = form.bio.trim().takeIf { it.isNotEmpty() },
                    ),
                )
                edit(FormField.Bio) { it.copy(bio = result.bio.take(WorkForm.BIO_MAX)) }
                onWritten(
                    when (result.source) {
                        BioSuggestion.Source.Claude -> "Here's a draft — make it yours."
                        BioSuggestion.Source.Fallback -> "Here's a starting point — add your own details."
                    },
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _ui.update { it.copy(errors = it.errors + (FormField.Bio to error.userMessage())) }
            } finally {
                _ui.update { it.copy(writingBio = false) }
            }
        }
    }

    /** Uploads a framed square photo; the engine makes it the account's picture straight away. */
    fun uploadAvatar(jpeg: ByteArray, side: Int) = avatar(AvatarAction.Upload) {
        profileApi.uploadAvatar(jpeg, side)
        container.session.refreshMe()
    }

    fun removeAvatar() = avatar(AvatarAction.Remove) {
        container.session.updateMe(profileApi.removePicture())
    }

    private fun avatar(action: AvatarAction, block: suspend () -> Unit) {
        if (_ui.value.avatarAction != null) return
        _ui.update { it.copy(avatarAction = action, avatarError = null) }
        viewModelScope.launch {
            try {
                block()
                profile.refresh()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _ui.update { it.copy(avatarError = error.userMessage()) }
            } finally {
                _ui.update { it.copy(avatarAction = null) }
            }
        }
    }

    /**
     * Uploads picked work photos one by one; each stored path joins the form as
     * soon as it is ready (saved with step 3). Only as many as there is room for.
     */
    fun addWorkPhotos(picked: List<suspend () -> SizedPhoto>) {
        val state = _ui.value
        val room = WorkForm.MAX_PHOTOS - state.form.photos.size - state.photosUploading
        val chosen = picked.take(room.coerceAtLeast(0))
        val tooMany = picked.size > chosen.size
        _ui.update {
            it.copy(
                photosUploading = it.photosUploading + chosen.size,
                errors = if (tooMany) {
                    it.errors + (FormField.Photos to "You can add up to ${WorkForm.MAX_PHOTOS} photos.")
                } else {
                    it.errors - FormField.Photos
                },
            )
        }
        if (chosen.isEmpty()) return
        viewModelScope.launch {
            for (read in chosen) {
                try {
                    val photo = read()
                    val stored = api.uploadWorkPhoto(photo.bytes, photo.width, photo.height)
                    edit { form -> form.copy(photos = (form.photos + stored.url).distinct().take(WorkForm.MAX_PHOTOS)) }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    _ui.update { it.copy(errors = it.errors + (FormField.Photos to error.userMessage())) }
                } finally {
                    _ui.update { it.copy(photosUploading = (it.photosUploading - 1).coerceAtLeast(0)) }
                }
            }
        }
    }

    fun removeWorkPhoto(path: String) = edit(FormField.Photos) { it.copy(photos = it.photos - path) }

    private companion object {
        const val DRAFTS = "work-setup"
    }
}

/** The signed-in account as the session holds it now (after a photo or phone change). */
private fun AppContainer.currentMe(): Me? =
    (session.state.value as? SessionState.SignedIn)?.me
