package com.hafiyanjye.app.ui.talent

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hafiyanjye.app.R
import com.hafiyanjye.app.data.api.userMessage
import com.hafiyanjye.app.data.talent.TalentApi
import com.hafiyanjye.app.data.talent.TalentEducation
import com.hafiyanjye.app.data.talent.TalentExperience
import com.hafiyanjye.app.data.talent.TalentLimits
import com.hafiyanjye.app.data.talent.TalentLink
import com.hafiyanjye.app.data.talent.TalentPatch
import com.hafiyanjye.app.data.talent.TalentPolish
import com.hafiyanjye.app.data.talent.TalentProfileDto
import com.hafiyanjye.app.data.talent.TalentRules
import com.hafiyanjye.app.data.talent.TalentSection
import com.hafiyanjye.app.data.talent.talentApi
import com.hafiyanjye.app.ui.containerViewModel
import com.hafiyanjye.app.ui.kit.ButtonKind
import com.hafiyanjye.app.ui.kit.DetailScaffold
import com.hafiyanjye.app.ui.kit.FormAlert
import com.hafiyanjye.app.ui.kit.HafiButton
import com.hafiyanjye.app.ui.kit.HafiField
import com.hafiyanjye.app.ui.kit.HafiSwitchRow
import com.hafiyanjye.app.ui.kit.HafiTextArea
import com.hafiyanjye.app.ui.theme.Hafi
import com.hafiyanjye.app.ui.theme.HafiType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Changes the draft being edited; editors pass a copy-with-change. */
internal typealias DraftEdit = ((TalentProfileDto) -> TalentProfileDto) -> Unit

/** One section editor's form. */
internal data class TalentEditorState(
    /** Which opening of the editor this draft belongs to (see [TalentEditorViewModel.open]). */
    val opening: Int = -1,
    val draft: TalentProfileDto? = null,
    val saving: Boolean = false,
    val polishing: Boolean = false,
    val uploadingPhoto: Boolean = false,
    val error: String? = null,
    val suggestion: TalentPolish? = null,
    /** The details editor accepted a suggested summary: it is saved along, like the web. */
    val acceptedSummary: String? = null,
    /** Set once the engine saved; the screen hands it to the dashboard and closes. */
    val saved: TalentProfileDto? = null,
)

/** A section editor: a draft of the profile, saved with only that section's fields. */
internal class TalentEditorViewModel(private val api: TalentApi) : ViewModel() {
    private val _ui = MutableStateFlow(TalentEditorState())
    val ui: StateFlow<TalentEditorState> = _ui.asStateFlow()

    /**
     * Starts a fresh draft from [profile] on each new [opening]; the same
     * opening (after a rotation) keeps what the person typed.
     */
    fun open(section: TalentSection, profile: TalentProfileDto, opening: Int) {
        if (_ui.value.opening == opening) return
        _ui.value = TalentEditorState(opening = opening, draft = withBlankRow(section, profile))
    }

    /** Lists start with one empty row to fill, like the web. */
    private fun withBlankRow(section: TalentSection, profile: TalentProfileDto): TalentProfileDto = when {
        section == TalentSection.Experience && profile.experience.isEmpty() ->
            profile.copy(experience = listOf(TalentExperience.blank()))
        section == TalentSection.Education && profile.education.isEmpty() ->
            profile.copy(education = listOf(TalentEducation.blank()))
        section == TalentSection.Links && profile.links.isEmpty() ->
            profile.copy(links = listOf(TalentLink(url = "")))
        else -> profile
    }

    fun edit(change: (TalentProfileDto) -> TalentProfileDto) {
        if (_ui.value.saving) return
        _ui.update { it.copy(draft = it.draft?.let(change), error = null) }
    }

    /** Asks the engine for a crisper headline and summary of the saved profile, shown as a suggestion. */
    fun polish() {
        if (_ui.value.polishing) return
        _ui.update { it.copy(polishing = true, error = null, suggestion = null) }
        viewModelScope.launch {
            try {
                val suggestion = api.polish()
                _ui.update { it.copy(suggestion = suggestion) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _ui.update { it.copy(error = error.userMessage()) }
            } finally {
                _ui.update { it.copy(polishing = false) }
            }
        }
    }

    fun dismissSuggestion() {
        _ui.update { it.copy(suggestion = null) }
    }

    /** Basics takes the headline and the summary; the summary editor only the summary. */
    fun acceptSuggestion(section: TalentSection) {
        val suggestion = _ui.value.suggestion ?: return
        _ui.update { state ->
            val draft = state.draft ?: return@update state
            if (section == TalentSection.Basics) {
                state.copy(
                    draft = draft.copy(headline = suggestion.headline, summary = suggestion.summary),
                    acceptedSummary = suggestion.summary,
                    suggestion = null,
                )
            } else {
                state.copy(draft = draft.copy(summary = suggestion.summary), suggestion = null)
            }
        }
    }

    /** Uploads a chosen photo now; it joins the profile when the person saves. */
    fun uploadPhoto(read: suspend () -> SquarePhoto) {
        if (_ui.value.uploadingPhoto) return
        _ui.update { it.copy(uploadingPhoto = true, error = null) }
        viewModelScope.launch {
            try {
                val photo = read()
                val path = api.uploadPhoto(photo.jpeg, photo.side)
                _ui.update { it.copy(draft = it.draft?.copy(photo = path)) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: CvFileException) {
                _ui.update { it.copy(error = error.message) }
            } catch (error: Exception) {
                _ui.update { it.copy(error = error.userMessage()) }
            } finally {
                _ui.update { it.copy(uploadingPhoto = false) }
            }
        }
    }

    fun save(section: TalentSection) {
        val state = _ui.value
        val draft = state.draft ?: return
        if (state.saving || state.uploadingPhoto) return
        val problem = editorError(section, draft)
        if (problem != null) {
            _ui.update { it.copy(error = problem) }
            return
        }
        val patch = sectionPatch(section, draft, state.acceptedSummary)
        _ui.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            try {
                val saved = api.update(patch)
                _ui.update { it.copy(saved = saved) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _ui.update { it.copy(error = error.userMessage()) }
            } finally {
                _ui.update { it.copy(saving = false) }
            }
        }
    }
}

/** What one section's Save sends: only that section's fields, cleaned like the web does. */
internal fun sectionPatch(section: TalentSection, draft: TalentProfileDto, acceptedSummary: String?): TalentPatch =
    when (section) {
        TalentSection.Basics -> TalentPatch(
            fullName = draft.fullName.trim(),
            headline = draft.headline.trim(),
            location = draft.location.trim(),
            phone = draft.phone.trim(),
            email = draft.email.trim(),
            // "" clears the photo; null would leave it out of the request.
            photo = draft.photo.orEmpty(),
            summary = acceptedSummary,
        )
        TalentSection.Summary -> TalentPatch(summary = draft.summary.trim())
        TalentSection.Skills -> TalentPatch(skills = draft.skills)
        TalentSection.Languages -> TalentPatch(languages = draft.languages)
        TalentSection.Goals -> TalentPatch(desiredRoles = draft.desiredRoles, openToWork = draft.openToWork)
        TalentSection.Experience -> TalentPatch(
            experience = draft.experience
                .filter { it.role.isNotBlank() || it.company.isNotBlank() }
                .map { it.copy(role = it.role.trim(), company = it.company.trim(), details = it.details.trim()) },
        )
        TalentSection.Education -> TalentPatch(
            education = draft.education
                .filter { it.school.isNotBlank() || it.degree.isNotBlank() }
                .map { it.copy(school = it.school.trim(), degree = it.degree.trim()) },
        )
        TalentSection.Links -> TalentPatch(
            links = draft.links
                .filter { it.url.isNotBlank() }
                .map { link ->
                    TalentLink(
                        label = link.label.trim().ifEmpty { TalentRules.guessLabel(link.url) },
                        url = TalentRules.normalizeUrl(link.url),
                    )
                },
        )
    }

/** The first thing to fix before saving, or null. Dates and links the engine would silently drop are caught here. */
internal fun editorError(section: TalentSection, draft: TalentProfileDto): String? = when (section) {
    TalentSection.Basics -> TalentRules.emailError(draft.email) ?: TalentRules.phoneError(draft.phone)
    TalentSection.Experience -> draft.experience.firstNotNullOfOrNull { dateProblem(it.start, it.end) }
    TalentSection.Education -> draft.education.firstNotNullOfOrNull { dateProblem(it.start, it.end) }
    TalentSection.Links -> draft.links.firstNotNullOfOrNull { TalentRules.urlError(it.url) }
    else -> null
}

private fun dateProblem(start: String, end: String?): String? =
    TalentRules.dateError(start) ?: TalentRules.dateError(end) ?: TalentRules.rangeError(start, end)

/**
 * One section of the profile in a full-screen form with Cancel and Save,
 * the phone's version of the web's edit sheet.
 */
@Composable
internal fun TalentEditorScreen(
    section: TalentSection,
    profile: TalentProfileDto,
    opening: Int,
    onSaved: (TalentProfileDto) -> Unit,
    onClose: () -> Unit,
) {
    val viewModel = containerViewModel(key = "talent-editor-${section.key}") {
        TalentEditorViewModel(it.talentApi())
    }
    // Once per opening, before the first frame reads the draft.
    remember(opening) {
        viewModel.open(section, profile, opening)
        opening
    }
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val scroll = rememberScrollState()
    LaunchedEffect(ui.error) {
        if (ui.error != null) scroll.animateScrollTo(0)
    }
    LaunchedEffect(ui.saved) {
        ui.saved?.let(onSaved)
    }

    DetailScaffold(
        title = section.title,
        onBack = onClose,
        bottomBar = { EditorFooter(saving = ui.saving, onCancel = onClose, onSave = { viewModel.save(section) }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scroll)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            FormAlert(ui.error)
            val draft = ui.draft ?: return@Column
            val edit: DraftEdit = viewModel::edit
            val enabled = !ui.saving
            when (section) {
                TalentSection.Basics -> BasicsFields(draft, ui, viewModel, enabled)
                TalentSection.Summary -> SummaryFields(draft, ui, viewModel, enabled)
                TalentSection.Skills -> SkillsFields(draft, edit, enabled)
                TalentSection.Goals -> GoalsFields(draft, edit, enabled)
                TalentSection.Languages -> LanguageFields(draft, edit, enabled)
                TalentSection.Experience -> ExperienceFields(draft, edit, enabled)
                TalentSection.Education -> EducationFields(draft, edit, enabled)
                TalentSection.Links -> LinkFields(draft, edit, enabled)
            }
        }
    }
}

@Composable
private fun EditorFooter(saving: Boolean, onCancel: () -> Unit, onSave: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Hafi.Surface)
            .topRule()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
    ) {
        HafiButton("Cancel", onCancel, kind = ButtonKind.Secondary, enabled = !saving)
        HafiButton("Save", onSave, loading = saving, loadingText = "Saving")
    }
}

@Composable
private fun BasicsFields(
    draft: TalentProfileDto,
    ui: TalentEditorState,
    viewModel: TalentEditorViewModel,
    enabled: Boolean,
) {
    val context = LocalContext.current.applicationContext
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.uploadPhoto { readSquarePhoto(context, uri) }
    }
    val edit: DraftEdit = viewModel::edit
    FieldGroup("Profile") {
        PhotoPicker(
            name = draft.fullName,
            photo = draft.photo,
            uploading = ui.uploadingPhoto,
            enabled = enabled,
            onPick = {
                pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
        )
        HafiField(
            label = "Full name",
            value = draft.fullName,
            onValueChange = { value -> edit { it.copy(fullName = value.take(TalentLimits.Text)) } },
            placeholder = "Aline Uwase",
            enabled = enabled,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
        )
        HafiField(
            label = "Headline",
            value = draft.headline,
            onValueChange = { value -> edit { it.copy(headline = value.take(TalentLimits.Text)) } },
            placeholder = "Accountant · 3 years in hospitality",
            hint = "Your role and experience, in one line.",
            enabled = enabled,
        )
        PolishButton(busy = ui.polishing, onClick = viewModel::polish)
        ui.suggestion?.let { idea ->
            Suggestion(
                onUse = { viewModel.acceptSuggestion(TalentSection.Basics) },
                onDrop = viewModel::dismissSuggestion,
            ) {
                Text(idea.headline, style = HafiType.BodyStrong)
                Text(idea.summary, style = HafiType.Body, color = Hafi.FgMuted)
            }
        }
    }
    FieldGroup("Contact") {
        HafiField(
            label = "City",
            value = draft.location,
            onValueChange = { value -> edit { it.copy(location = value.take(TalentLimits.Text)) } },
            placeholder = "Kigali, Kicukiro",
            enabled = enabled,
        )
        HafiField(
            label = "Phone",
            value = draft.phone,
            onValueChange = { value -> edit { it.copy(phone = value.take(TalentLimits.Text)) } },
            placeholder = "+250 788 123 456",
            error = TalentRules.phoneError(draft.phone),
            enabled = enabled,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
        )
        HafiField(
            label = "Email",
            value = draft.email,
            onValueChange = { value -> edit { it.copy(email = value.take(TalentLimits.Text)) } },
            placeholder = "aline.uwase@gmail.com",
            error = TalentRules.emailError(draft.email),
            enabled = enabled,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        )
    }
}

@Composable
private fun PhotoPicker(
    name: String,
    photo: String?,
    uploading: Boolean,
    enabled: Boolean,
    onPick: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Portrait(name = name, photo = photo, size = 80.dp)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            HafiButton(
                text = if (photo == null) "Upload photo" else "Change photo",
                onClick = onPick,
                kind = ButtonKind.Secondary,
                icon = R.drawable.ic_camera,
                loading = uploading,
                loadingText = "Uploading",
                enabled = enabled,
            )
            Text("JPG or PNG. A clear, front-facing photo.", style = HafiType.Caption, color = Hafi.FgMuted)
        }
    }
}

@Composable
private fun SummaryFields(
    draft: TalentProfileDto,
    ui: TalentEditorState,
    viewModel: TalentEditorViewModel,
    enabled: Boolean,
) {
    HafiTextArea(
        label = "About you",
        value = draft.summary,
        onValueChange = { value -> viewModel.edit { it.copy(summary = value) } },
        placeholder = "Accountant with 3 years in Kigali hotels. I close the books on time every month " +
            "and cut supplier costs by 12% at Serena.",
        hint = "What you do, for how long, and one result.",
        maxLength = TalentLimits.Summary,
        minLines = 6,
        maxLines = 14,
        enabled = enabled,
    )
    PolishButton(busy = ui.polishing, onClick = viewModel::polish)
    ui.suggestion?.let { idea ->
        Suggestion(
            onUse = { viewModel.acceptSuggestion(TalentSection.Summary) },
            onDrop = viewModel::dismissSuggestion,
        ) {
            Text(idea.summary, style = HafiType.Body)
        }
    }
}

/** "Polish": the AI rewrites the saved headline and summary as a suggestion. */
@Composable
private fun PolishButton(busy: Boolean, onClick: () -> Unit) {
    HafiButton(
        text = "Polish with AI",
        onClick = onClick,
        kind = ButtonKind.Ghost,
        icon = R.drawable.ic_sparkles,
        loading = busy,
        loadingText = "Writing",
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SkillsFields(draft: TalentProfileDto, edit: DraftEdit, enabled: Boolean) {
    ChipInput(
        label = "Your skills",
        value = draft.skills,
        onChange = { skills -> edit { it.copy(skills = skills) } },
        max = TalentLimits.Skills,
        placeholder = "QuickBooks",
        hint = "Press Done to add. Most relevant first.",
        enabled = enabled,
    )
    val ideas = TalentRules.skillSuggestions(draft.desiredRoles, draft.headline, draft.skills)
    if (ideas.isNotEmpty() && draft.skills.size < TalentLimits.Skills) {
        FieldGroup("Suggested") {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ideas.forEach { skill ->
                    SuggestChip(skill, enabled = enabled) {
                        edit { it.copy(skills = TalentRules.addChip(it.skills, skill, TalentLimits.Skills)) }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GoalsFields(draft: TalentProfileDto, edit: DraftEdit, enabled: Boolean) {
    FieldGroup("Status") {
        HafiSwitchRow(
            title = "Open to work",
            checked = draft.openToWork,
            onCheckedChange = { open -> edit { it.copy(openToWork = open) } },
            description = if (draft.openToWork) {
                "Employers see an Open to work badge on your profile."
            } else {
                "The badge is hidden from your profile."
            },
            enabled = enabled,
        )
    }
    FieldGroup("Target roles") {
        ChipInput(
            label = "Roles",
            value = draft.desiredRoles,
            onChange = { roles -> edit { it.copy(desiredRoles = roles) } },
            max = TalentLimits.DesiredRoles,
            placeholder = "Accountant",
            hint = "Up to ${TalentLimits.DesiredRoles}. Press Done to add.",
            enabled = enabled,
        )
        val ideas = TalentRules.roleSuggestions(draft.desiredRoles)
        if (ideas.isNotEmpty() && draft.desiredRoles.size < TalentLimits.DesiredRoles) {
            Text("Suggested", style = HafiType.Label, color = Hafi.Fg)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ideas.forEach { role ->
                    SuggestChip(role, enabled = enabled) {
                        edit { profile ->
                            val roles = TalentRules.addChip(profile.desiredRoles, role, TalentLimits.DesiredRoles)
                            profile.copy(desiredRoles = roles)
                        }
                    }
                }
            }
        }
    }
}
