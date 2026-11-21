package com.hafiyanjye.app.ui.profile

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.hafiyanjye.app.R
import com.hafiyanjye.app.core.Tone
import com.hafiyanjye.app.data.api.ApiException
import com.hafiyanjye.app.data.api.Me
import com.hafiyanjye.app.data.api.userMessage
import com.hafiyanjye.app.data.profile.Countries
import com.hafiyanjye.app.data.profile.Country
import com.hafiyanjye.app.data.profile.CropView
import com.hafiyanjye.app.data.profile.PhoneRules
import com.hafiyanjye.app.data.profile.ProfileApi
import com.hafiyanjye.app.data.profile.ProfileRules
import com.hafiyanjye.app.data.session.Session
import com.hafiyanjye.app.data.session.SessionState
import com.hafiyanjye.app.ui.containerViewModel
import com.hafiyanjye.app.ui.kit.Avatar
import com.hafiyanjye.app.ui.kit.ButtonKind
import com.hafiyanjye.app.ui.kit.DetailScaffold
import com.hafiyanjye.app.ui.kit.FormAlert
import com.hafiyanjye.app.ui.kit.HafiButton
import com.hafiyanjye.app.ui.kit.HafiField
import com.hafiyanjye.app.ui.kit.LocalToaster
import com.hafiyanjye.app.ui.kit.SectionCard
import com.hafiyanjye.app.ui.theme.Hafi
import com.hafiyanjye.app.ui.theme.HafiType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Which photo action is running. */
enum class PhotoBusy { Uploading, Removing }

/** The edit form; kept in the ViewModel so typing survives rotation. */
data class EditProfileUiState(
    val name: String,
    val country: Country,
    /** The number as typed, without the calling code. */
    val national: String,
    val nameError: String? = null,
    val phoneError: String? = null,
    val formError: String? = null,
    val saving: Boolean = false,
    val photoBusy: PhotoBusy? = null,
    val photoError: String? = null,
) {
    val phone: String get() = PhoneRules.full(country, national)

    /** Something to save; an emptied name counts so that pressing Save explains it is required. */
    fun dirty(me: Me): Boolean =
        ProfileRules.updateBody(me, name, phone) != null || (name.isBlank() && me.name != null)
}

/** Saves name and phone, and uploads or removes the photo (web: profile page "Your details"). */
class EditProfileViewModel(private val session: Session, private val api: ProfileApi, me: Me) : ViewModel() {

    /** One-off "Profile saved" signals; a channel so a rotation does not show the toast again. */
    private val _saved = Channel<Unit>(Channel.BUFFERED)
    val saved: Flow<Unit> = _saved.receiveAsFlow()

    private val _ui = MutableStateFlow(
        Countries.split(me.phone).let { (country, national) ->
            EditProfileUiState(name = me.name.orEmpty(), country = country, national = national)
        },
    )
    val ui: StateFlow<EditProfileUiState> = _ui.asStateFlow()

    fun setName(value: String) = _ui.update { it.copy(name = value, nameError = null, formError = null) }

    fun setNational(value: String) = _ui.update { it.copy(national = value, phoneError = null, formError = null) }

    fun setCountry(value: Country) = _ui.update { it.copy(country = value, phoneError = null, formError = null) }

    fun save(me: Me) {
        val form = _ui.value
        if (form.saving || form.photoBusy != null) return
        val nameError = ProfileRules.nameProblem(form.name)
        val phoneError = PhoneRules.problem(form.country, form.national)
        if (nameError != null || phoneError != null) {
            _ui.update { it.copy(nameError = nameError, phoneError = phoneError) }
            return
        }
        val body = ProfileRules.updateBody(me, form.name, form.phone) ?: return
        _ui.update { it.copy(saving = true, formError = null) }
        viewModelScope.launch {
            try {
                val updated = api.update(body)
                // The whole app reads the account from the session.
                session.updateMe(updated)
                val (country, national) = Countries.split(updated.phone)
                _saved.trySend(Unit)
                _ui.update {
                    it.copy(
                        saving = false,
                        name = updated.name.orEmpty(),
                        // Keep the chosen country when the phone was removed.
                        country = if (updated.phone == null) it.country else country,
                        national = national,
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                val message = error.userMessage()
                // The engine's field-level complaints here are about the number or the name.
                _ui.update {
                    when {
                        message.contains("number", ignoreCase = true) ->
                            it.copy(saving = false, phoneError = message)
                        message.contains("name", ignoreCase = true) ->
                            it.copy(saving = false, nameError = message)
                        else -> it.copy(saving = false, formError = message)
                    }
                }
            }
        }
    }

    fun uploadPhoto(jpeg: ByteArray) = photo(PhotoBusy.Uploading, "Couldn't upload the photo.") {
        val stored = api.uploadAvatar(jpeg, CropView.OUTPUT)
        // The engine has already made it the picture; reloading the account is only to show it.
        try {
            session.refreshMe()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            val me = (session.state.value as? SessionState.SignedIn)?.me
            if (me != null) session.updateMe(me.copy(picture = stored.url))
        }
    }

    fun removePhoto() = photo(PhotoBusy.Removing, "Couldn't remove the photo.") {
        session.updateMe(api.removePicture())
    }

    private fun photo(kind: PhotoBusy, fallback: String, job: suspend () -> Unit) {
        if (_ui.value.photoBusy != null || _ui.value.saving) return
        _ui.update { it.copy(photoBusy = kind, photoError = null) }
        viewModelScope.launch {
            try {
                job()
                _ui.update { it.copy(photoBusy = null) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                val message = (error as? ApiException)?.message?.takeIf { it.isNotBlank() }
                    ?: "$fallback Check your connection and try again."
                _ui.update { it.copy(photoBusy = null, photoError = message) }
            }
        }
    }
}

/** Photo, name and phone (pushed from the profile tab). */
@Composable
fun EditProfileScreen(me: Me, nav: NavHostController) {
    val viewModel = containerViewModel { EditProfileViewModel(it.session, ProfileApi(it.client), me) }
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val toaster = LocalToaster.current
    val nameFocus = remember { FocusRequester() }
    val phoneFocus = remember { FocusRequester() }

    LaunchedEffect(ui.nameError, ui.phoneError) {
        when {
            ui.nameError != null -> nameFocus.requestFocus()
            ui.phoneError != null -> phoneFocus.requestFocus()
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.saved.collect { toaster.show("Profile saved", Tone.Accent) }
    }

    DetailScaffold(title = "Edit profile", onBack = { nav.popBackStack() }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            SectionCard(title = "Profile photo") {
                PhotoPicker(me, ui, viewModel)
            }

            SectionCard(title = "Your details") {
                HafiField(
                    label = "Full name",
                    modifier = Modifier.focusRequester(nameFocus),
                    value = ui.name,
                    onValueChange = viewModel::setName,
                    placeholder = "Aline Uwase",
                    error = ui.nameError,
                    enabled = !ui.saving && ui.photoBusy == null,
                    autofill = ContentType.PersonFullName,
                    leadingIcon = R.drawable.ic_user_round,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Next,
                    ),
                )
                CountryCodeField(
                    country = ui.country,
                    onChange = viewModel::setCountry,
                    enabled = !ui.saving && ui.photoBusy == null,
                )
                HafiField(
                    label = "Phone number",
                    modifier = Modifier.focusRequester(phoneFocus),
                    value = ui.national,
                    onValueChange = viewModel::setNational,
                    placeholder = ui.country.example ?: "Phone number",
                    hint = "Without the country code. Shared only with people you match with.",
                    error = ui.phoneError,
                    optional = true,
                    enabled = !ui.saving && ui.photoBusy == null,
                    autofill = ContentType.PhoneNumberNational,
                    leadingIcon = R.drawable.ic_phone,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Phone,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { viewModel.save(me) }),
                )
                FormAlert(ui.formError)
                HafiButton(
                    text = "Save changes",
                    onClick = { viewModel.save(me) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = ui.dirty(me) && ui.photoBusy == null,
                    loading = ui.saving,
                    loadingText = "Saving…",
                )
            }

            Text(
                "Your email, ${me.email}, is how you sign in and can't be changed here.",
                style = HafiType.Caption,
                color = Hafi.FgSubtle,
            )
        }
    }
}

/** Profile photo with "Add a photo" / "Change photo" and "Remove". A picked photo is framed first, then uploaded. */
@Composable
private fun PhotoPicker(me: Me, ui: EditProfileUiState, viewModel: EditProfileViewModel) {
    var picked by rememberSaveable { mutableStateOf<Uri?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) picked = uri
    }
    val busy = ui.photoBusy != null || ui.saving

    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.alpha(if (busy) 0.4f else 1f)) {
            Avatar(me.displayName, me.picture, size = 80.dp)
        }
        val status = when {
            ui.photoError != null -> "Error: ${ui.photoError}"
            ui.photoBusy == PhotoBusy.Uploading -> "Uploading…"
            ui.photoBusy == PhotoBusy.Removing -> "Removing…"
            me.picture != null -> "People you match with see this photo."
            me.hasWorkProfile -> "A clear photo of your face builds trust (+15 points)."
            else -> "A clear photo of your face helps the people you hire recognise you."
        }
        Text(
            status,
            style = HafiType.Body,
            color = if (ui.photoError != null) Hafi.Danger else Hafi.FgMuted,
            modifier = Modifier
                .weight(1f)
                .semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        HafiButton(
            text = if (me.picture != null) "Change photo" else "Add a photo",
            onClick = {
                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            modifier = Modifier.weight(1f),
            kind = ButtonKind.Outline,
            icon = R.drawable.ic_camera,
            enabled = !busy || ui.photoBusy == PhotoBusy.Uploading,
            loading = ui.photoBusy == PhotoBusy.Uploading,
            loadingText = "Uploading…",
        )
        if (me.picture != null) {
            HafiButton(
                text = "Remove",
                onClick = viewModel::removePhoto,
                modifier = Modifier.weight(1f),
                kind = ButtonKind.Ghost,
                icon = R.drawable.ic_trash_2,
                enabled = !busy || ui.photoBusy == PhotoBusy.Removing,
                loading = ui.photoBusy == PhotoBusy.Removing,
                loadingText = "Removing…",
            )
        }
    }

    picked?.let { uri ->
        PhotoCropDialog(
            uri = uri,
            onCancel = { picked = null },
            onSave = { jpeg ->
                picked = null
                viewModel.uploadPhoto(jpeg)
            },
        )
    }
}
