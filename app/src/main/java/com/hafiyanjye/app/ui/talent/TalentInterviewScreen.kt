package com.hafiyanjye.app.ui.talent

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hafiyanjye.app.R
import com.hafiyanjye.app.core.Tone
import com.hafiyanjye.app.data.api.Me
import com.hafiyanjye.app.data.api.userMessage
import com.hafiyanjye.app.data.talent.TalentApi
import com.hafiyanjye.app.data.talent.TalentEducation
import com.hafiyanjye.app.data.talent.TalentExperience
import com.hafiyanjye.app.data.talent.TalentInterview
import com.hafiyanjye.app.data.talent.TalentInterviewAnswer
import com.hafiyanjye.app.data.talent.TalentInterviewDraft
import com.hafiyanjye.app.data.talent.TalentLanguage
import com.hafiyanjye.app.data.talent.TalentLimits
import com.hafiyanjye.app.data.talent.TalentLink
import com.hafiyanjye.app.data.talent.TalentProfileDto
import com.hafiyanjye.app.data.talent.TalentProfileFields
import com.hafiyanjye.app.data.talent.TalentRules
import com.hafiyanjye.app.data.talent.talentApi
import com.hafiyanjye.app.ui.containerViewModel
import com.hafiyanjye.app.ui.kit.ButtonKind
import com.hafiyanjye.app.ui.kit.DetailScaffold
import com.hafiyanjye.app.ui.kit.HafiButton
import com.hafiyanjye.app.ui.kit.HafiField
import com.hafiyanjye.app.ui.kit.HafiTextArea
import com.hafiyanjye.app.ui.kit.InfoBanner
import com.hafiyanjye.app.ui.kit.LocalToaster
import com.hafiyanjye.app.ui.kit.ProgressBar
import com.hafiyanjye.app.ui.kit.SectionCard
import com.hafiyanjye.app.ui.theme.Hafi
import com.hafiyanjye.app.ui.theme.HafiType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One interview question. [short] questions take one line (a field), the rest a text area. */
internal data class InterviewQuestion(
    val id: String,
    val title: String,
    val hint: String,
    val placeholder: String,
    val short: Boolean = false,
)

/** The fixed interview. Ids match the engine's fallback reader (cv-interview.ts). */
internal val InterviewQuestions = listOf(
    InterviewQuestion(
        id = "role",
        title = "What work do you do, or want to do?",
        hint = "Your trade or job title. One line is enough.",
        placeholder = "e.g. Electrician, accountant, sales agent",
        short = true,
    ),
    InterviewQuestion(
        id = "current",
        title = "Where do you work now, or where did you last work?",
        hint = "The place, your role, when you started, and what you do there.",
        placeholder = "e.g. Simba Supermarket, cashier, since 2022. I serve customers, count the till " +
            "and train new staff.",
    ),
    InterviewQuestion(
        id = "earlier",
        title = "Any jobs before that?",
        hint = "Part-time work, internships, your own business and volunteering all count.",
        placeholder = "e.g. From 2019 to 2021 I sold phones at my uncle's shop in Nyabugogo.",
    ),
    InterviewQuestion(
        id = "study",
        title = "What did you study?",
        hint = "School, course or certificate, and the years.",
        placeholder = "e.g. A2 in Accounting at GS Kimironko, finished 2018. Driving licence B.",
    ),
    InterviewQuestion(
        id = "skills",
        title = "What are you good at?",
        hint = "Tools, machines, software, or things people thank you for.",
        placeholder = "e.g. Excel, customer care, fixing phones, handling cash",
    ),
    InterviewQuestion(
        id = "languages",
        title = "Which languages do you speak, and how well?",
        hint = "Say native, fluent, good or a little.",
        placeholder = "e.g. Kinyarwanda native, English good, a little French",
    ),
    InterviewQuestion(
        id = "location",
        title = "Where do you live?",
        hint = "Your area and city, so nearby employers find you.",
        placeholder = "e.g. Kicukiro, Kigali",
        short = true,
    ),
    InterviewQuestion(
        id = "extra",
        title = "Anything else an employer should know?",
        hint = "Certificates, awards, the job you want next, when you can start.",
        placeholder = "e.g. I want to work in a hotel. I can start right away.",
    ),
)

/** Where the interview is. */
internal sealed interface InterviewPhase {
    data class Ask(val index: Int) : InterviewPhase
    data class Building(val final: Boolean) : InterviewPhase
    data class Follow(val index: Int) : InterviewPhase
    data object Review : InterviewPhase
}

/** The interview's answers and progress; answers outlive closing the dialog until they are saved. */
internal data class InterviewState(
    val prefilled: Boolean = false,
    val answers: Map<String, String> = emptyMap(),
    val phase: InterviewPhase = InterviewPhase.Ask(0),
    /** The AI's follow-up questions, as questions to ask. */
    val followUps: List<InterviewQuestion> = emptyList(),
    val result: TalentInterview? = null,
    val saving: Boolean = false,
    /** Set once the draft was saved; the screen hands it to the dashboard. */
    val saved: TalentProfileDto? = null,
)

/**
 * "Build with AI": one question at a time, up to 3 follow-ups the AI asks
 * about gaps, then a review of the built CV, saved with /talent/me/update.
 */
internal class TalentInterviewViewModel(private val api: TalentApi) : ViewModel() {
    private val _ui = MutableStateFlow(InterviewState())
    val ui: StateFlow<InterviewState> = _ui.asStateFlow()

    private val _errors = Channel<String>(Channel.BUFFERED)
    val errors = _errors.receiveAsFlow()

    /** The first time only: the role and town questions start with what the profile says. */
    fun prefill(base: TalentProfileFields) {
        if (_ui.value.prefilled) return
        val answers = buildMap {
            base.headline.trim().takeIf { it.isNotEmpty() }?.let { put("role", it) }
            base.location.trim().takeIf { it.isNotEmpty() }?.let { put("location", it) }
        }
        _ui.update { it.copy(prefilled = true, answers = answers) }
    }

    fun answer(question: InterviewQuestion, value: String) {
        val limit = if (question.short) TalentLimits.ShortAnswer else TalentLimits.Answer
        _ui.update { it.copy(answers = it.answers + (question.id to value.take(limit))) }
    }

    /** Next question, or, after the last one, build (or finish) the CV. */
    fun next() {
        val state = _ui.value
        when (val phase = state.phase) {
            is InterviewPhase.Ask -> if (phase.index < InterviewQuestions.lastIndex) {
                _ui.update { it.copy(phase = InterviewPhase.Ask(phase.index + 1)) }
            } else {
                build(final = false)
            }
            is InterviewPhase.Follow -> if (phase.index < state.followUps.lastIndex) {
                _ui.update { it.copy(phase = InterviewPhase.Follow(phase.index + 1)) }
            } else if (state.followUps.any { state.answers[it.id].orEmpty().isNotBlank() }) {
                build(final = true)
            } else {
                // No follow-up answered: the first round's draft is the CV.
                _ui.update { it.copy(phase = InterviewPhase.Review) }
            }
            else -> Unit
        }
    }

    fun back() {
        _ui.update { state ->
            when (val phase = state.phase) {
                is InterviewPhase.Ask -> state.copy(phase = InterviewPhase.Ask((phase.index - 1).coerceAtLeast(0)))
                is InterviewPhase.Follow -> state.copy(
                    phase = if (phase.index == 0) lastAsk() else InterviewPhase.Follow(phase.index - 1),
                )
                InterviewPhase.Review -> state.copy(phase = lastQuestion(state))
                is InterviewPhase.Building -> state
            }
        }
    }

    private fun lastAsk() = InterviewPhase.Ask(InterviewQuestions.lastIndex)

    private fun lastQuestion(state: InterviewState): InterviewPhase =
        if (state.followUps.isEmpty()) lastAsk() else InterviewPhase.Follow(state.followUps.lastIndex)

    private fun build(final: Boolean) {
        val state = _ui.value
        val asked = InterviewQuestions + if (final) state.followUps else emptyList()
        val answers = asked.mapNotNull { question ->
            state.answers[question.id]
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.let { TalentInterviewAnswer(question.id, question.title, it) }
        }
        if (answers.isEmpty()) {
            _errors.trySend("Answer at least one question first.")
            _ui.update { it.copy(phase = InterviewPhase.Ask(0)) }
            return
        }
        _ui.update { it.copy(phase = InterviewPhase.Building(final)) }
        viewModelScope.launch {
            try {
                val result = api.interview(answers, final)
                val followUps = if (final) emptyList() else result.followUps.take(MAX_FOLLOW_UPS)
                _ui.update { current ->
                    if (followUps.isEmpty()) {
                        current.copy(result = result, phase = InterviewPhase.Review)
                    } else {
                        current.copy(
                            result = result,
                            followUps = followUps.mapIndexed { i, title -> followUpQuestion(i, title) },
                            phase = InterviewPhase.Follow(0),
                        )
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _errors.send(error.userMessage())
                _ui.update { it.copy(phase = if (final) lastQuestion(it) else lastAsk()) }
            }
        }
    }

    private fun followUpQuestion(index: Int, title: String) = InterviewQuestion(
        id = "followup-${index + 1}",
        title = title,
        hint = "This makes your CV stronger. Skip it if you like.",
        placeholder = "Your answer",
    )

    fun save() {
        val state = _ui.value
        val draft = state.result?.draft ?: return
        if (state.saving) return
        _ui.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                val saved = api.update(draft.toPatch())
                // Saved: the next interview starts fresh, as the web clears its stored answers.
                _ui.value = InterviewState(saved = saved)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _errors.send(error.userMessage())
                _ui.update { it.copy(saving = false) }
            }
        }
    }

    fun savedHandled() {
        _ui.update { it.copy(saved = null) }
    }

    private companion object {
        const val MAX_FOLLOW_UPS = 3
    }
}

/** What the interview starts from when the person has no profile yet: their account details. */
private data class AccountFields(
    override val fullName: String,
    override val email: String,
    override val phone: String,
) : TalentProfileFields {
    override val headline = ""
    override val summary = ""
    override val location = ""
    override val photo: String? = null
    override val skills = emptyList<String>()
    override val languages = emptyList<TalentLanguage>()
    override val experience = emptyList<TalentExperience>()
    override val education = emptyList<TalentEducation>()
    override val links = emptyList<TalentLink>()
    override val desiredRoles = emptyList<String>()
    override val openToWork = true
}

/** The full-screen "Build with AI" interview over the CV tab. */
@Composable
internal fun TalentInterviewScreen(
    profile: TalentProfileDto?,
    me: Me,
    onSaved: (TalentProfileDto) -> Unit,
    onClose: () -> Unit,
) {
    val viewModel = containerViewModel(key = "talent-interview") { TalentInterviewViewModel(it.talentApi()) }
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val toaster = LocalToaster.current
    val base: TalentProfileFields = profile
        ?: AccountFields(me.name?.trim().orEmpty(), me.email, me.phone.orEmpty())
    // Once per profile, before the first frame reads the answers.
    remember(base) {
        viewModel.prefill(base)
        base
    }

    LaunchedEffect(viewModel) {
        viewModel.errors.collect { toaster.show(it, Tone.Danger) }
    }
    LaunchedEffect(ui.saved) {
        val saved = ui.saved ?: return@LaunchedEffect
        viewModel.savedHandled()
        toaster.show("Your CV is saved. Style it or share it from your profile.", Tone.Accent)
        onSaved(saved)
    }

    val scroll = rememberScrollState()
    LaunchedEffect(ui.phase) { scroll.scrollTo(0) }
    val phase = ui.phase
    val question = when (phase) {
        is InterviewPhase.Ask -> InterviewQuestions.getOrNull(phase.index)
        is InterviewPhase.Follow -> ui.followUps.getOrNull(phase.index)
        else -> null
    }

    DetailScaffold(
        title = "Build with AI",
        onBack = onClose,
        actions = { if (question != null) StepCounter(ui) },
        bottomBar = question?.let { asked ->
            {
                QuestionFooter(
                    ui = ui,
                    answered = ui.answers[asked.id].orEmpty().isNotBlank(),
                    onBack = viewModel::back,
                    onNext = viewModel::next,
                )
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            ProgressBar(progressOf(ui))
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scroll)
                    .padding(horizontal = 20.dp, vertical = 28.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                when {
                    question != null -> QuestionStep(
                        question = question,
                        eyebrow = eyebrowOf(ui),
                        followUp = phase is InterviewPhase.Follow,
                        value = ui.answers[question.id].orEmpty(),
                        onChange = { viewModel.answer(question, it) },
                        onNext = viewModel::next,
                    )
                    phase is InterviewPhase.Building -> Building(
                        if (phase.final) "Finishing your CV" else "Building your CV",
                    )
                    phase == InterviewPhase.Review -> ui.result?.let { result ->
                        InterviewReview(
                            base = base,
                            result = result,
                            saving = ui.saving,
                            onSave = viewModel::save,
                            onChangeAnswers = viewModel::back,
                        )
                    }
                }
            }
        }
    }
}

/** How far along: answered questions over all questions, full on the review. */
private fun progressOf(ui: InterviewState): Float {
    val total = InterviewQuestions.size + ui.followUps.size
    val done = when (val phase = ui.phase) {
        is InterviewPhase.Ask -> phase.index
        is InterviewPhase.Follow -> InterviewQuestions.size + phase.index
        else -> total
    }
    return done.toFloat() / total
}

private fun eyebrowOf(ui: InterviewState): String = when (val phase = ui.phase) {
    is InterviewPhase.Follow -> "A few more details · ${phase.index + 1} of ${ui.followUps.size}"
    is InterviewPhase.Ask -> "Question ${phase.index + 1} of ${InterviewQuestions.size}"
    else -> ""
}

@Composable
private fun StepCounter(ui: InterviewState) {
    val total = InterviewQuestions.size + ui.followUps.size
    val at = when (val phase = ui.phase) {
        is InterviewPhase.Ask -> phase.index + 1
        is InterviewPhase.Follow -> InterviewQuestions.size + phase.index + 1
        else -> total
    }
    Text(
        text = "$at of $total",
        style = HafiType.Caption,
        color = Hafi.FgMuted,
        modifier = Modifier.padding(end = 16.dp),
    )
}

@Composable
private fun QuestionStep(
    question: InterviewQuestion,
    eyebrow: String,
    followUp: Boolean,
    value: String,
    onChange: (String) -> Unit,
    onNext: () -> Unit,
) {
    Eyebrow(eyebrow)
    Text(
        text = question.title,
        style = HafiType.Title,
        modifier = Modifier.semantics {
            heading()
            liveRegion = LiveRegionMode.Polite
        },
    )
    Text(question.hint, style = HafiType.Body, color = Hafi.FgMuted)
    if (question.short) {
        HafiField(
            label = "Your answer",
            value = value,
            onValueChange = onChange,
            placeholder = question.placeholder,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { onNext() }),
        )
    } else {
        HafiTextArea(
            label = "Your answer",
            value = value,
            onValueChange = onChange,
            placeholder = question.placeholder,
            maxLength = TalentLimits.Answer,
            minLines = 5,
            maxLines = 12,
        )
    }
    Text(
        text = if (followUp) {
            "The AI asks only about what's missing."
        } else {
            "Answer in your own words, in English, French or Kinyarwanda. Skip anything that doesn't fit."
        },
        style = HafiType.Caption,
        color = Hafi.FgMuted,
    )
}

/** Back, Skip (when the answer is empty and more questions follow) and Next / Build my CV / Finish my CV. */
@Composable
private fun QuestionFooter(ui: InterviewState, answered: Boolean, onBack: () -> Unit, onNext: () -> Unit) {
    val phase = ui.phase
    val isLast = when (phase) {
        is InterviewPhase.Ask -> phase.index == InterviewQuestions.lastIndex
        is InterviewPhase.Follow -> phase.index == ui.followUps.lastIndex
        else -> false
    }
    val nextLabel = when {
        !isLast -> "Next"
        phase is InterviewPhase.Ask -> "Build my CV"
        else -> "Finish my CV"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Hafi.Surface)
            .topRule()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HafiButton(
            text = "Back",
            onClick = onBack,
            kind = ButtonKind.Ghost,
            icon = R.drawable.ic_arrow_left,
            enabled = phase != InterviewPhase.Ask(0),
        )
        Spacer(Modifier.weight(1f))
        if (!answered && !isLast) {
            HafiButton("Skip", onNext, kind = ButtonKind.Ghost)
        }
        HafiButton(
            text = nextLabel,
            onClick = onNext,
            icon = R.drawable.ic_arrow_right,
            enabled = answered || isLast,
        )
    }
}

private val BuildSteps = listOf(
    "Reading your answers",
    "Writing your jobs",
    "Choosing your skills",
    "Writing your summary",
)

/** Web: each building step shows 1.4 s; the last one waits for the engine. */
private const val BUILD_STEP_MS = 1400L

@Composable
private fun Building(label: String) {
    var step by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (step < BuildSteps.lastIndex) {
            delay(BUILD_STEP_MS)
            step++
        }
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconMark(R.drawable.ic_sparkles)
        Column {
            Text(label, style = HafiType.Heading, modifier = Modifier.semantics { heading() })
            Text("This takes a few seconds.", style = HafiType.Caption, color = Hafi.FgMuted)
        }
    }
    StepList(BuildSteps, step)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InterviewReview(
    base: TalentProfileFields,
    result: TalentInterview,
    saving: Boolean,
    onSave: () -> Unit,
    onChangeAnswers: () -> Unit,
) {
    val draft = result.draft
    Eyebrow("Ready to review")
    Text(
        text = "Your CV is ready",
        style = HafiType.Title,
        modifier = Modifier.semantics {
            heading()
            liveRegion = LiveRegionMode.Polite
        },
    )
    Text("Check it over. You can change anything after saving.", style = HafiType.Body, color = Hafi.FgMuted)
    val changed = TalentRules.changedKeys(base, draft)
    if (changed.isNotEmpty()) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            changed.forEach { key ->
                FieldChips[key]?.let { (icon, label) -> PlainChip(label, icon = icon) }
            }
        }
    }
    if (!result.ai) {
        InfoBanner(
            "The AI helper is busy right now, so we filled in the simple parts. " +
                "Add your jobs and studies by hand, or try again later.",
            icon = R.drawable.ic_info,
        )
    }
    val replaced = TalentRules.replacedLabels(base, draft)
    if (replaced.isNotEmpty()) {
        Text(
            text = "Saving replaces your current ${TalentRules.joinWords(replaced)} with this version.",
            style = HafiType.Caption,
            color = Hafi.FgMuted,
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HafiButton(
            text = "Save to my profile",
            onClick = onSave,
            modifier = Modifier.fillMaxWidth(),
            icon = R.drawable.ic_check,
            loading = saving,
        )
        HafiButton(
            text = "Change answers",
            onClick = onChangeAnswers,
            modifier = Modifier.fillMaxWidth(),
            kind = ButtonKind.Secondary,
            icon = R.drawable.ic_arrow_left,
            enabled = !saving,
        )
    }
    DraftPreview(base, draft)
}

/** The CV as it will read after saving: the draft over what the profile already had. */
@Composable
private fun DraftPreview(base: TalentProfileFields, draft: TalentInterviewDraft) {
    SectionCard(title = base.fullName.trim().ifEmpty { "Your CV" }) {
        if (draft.headline.isNotBlank()) Text(draft.headline, style = HafiType.BodyStrong)
        if (draft.location.isNotBlank()) Text(draft.location, style = HafiType.Caption, color = Hafi.FgMuted)
        if (draft.summary.isNotBlank()) Text(draft.summary, style = HafiType.Body)
    }
    if (draft.experience.isNotEmpty()) {
        SectionCard(title = "Experience") {
            draft.experience.forEach { job ->
                PreviewEntry(
                    title = listOf(job.role, job.company).filter(String::isNotBlank).joinToString(" · "),
                    dates = TalentRules.formatCvRange(job.start, job.end),
                    details = job.details,
                )
            }
        }
    }
    if (draft.education.isNotEmpty()) {
        SectionCard(title = "Education") {
            draft.education.forEach { school ->
                PreviewEntry(
                    title = listOf(school.school, school.degree).filter(String::isNotBlank).joinToString(" · "),
                    dates = TalentRules.formatCvRange(school.start, school.end),
                )
            }
        }
    }
    if (draft.skills.isNotEmpty()) SectionCard(title = "Skills") { ChipFlow(draft.skills) }
    if (draft.languages.isNotEmpty()) {
        SectionCard(title = "Languages") {
            draft.languages.forEach { language ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(language.name, style = HafiType.Body, modifier = Modifier.weight(1f))
                    Text(language.level.label, style = HafiType.Caption, color = Hafi.FgMuted)
                    Spacer(Modifier.width(12.dp))
                    LevelBars(language.level)
                }
            }
        }
    }
    if (draft.desiredRoles.isNotEmpty()) SectionCard(title = "Looking for") { ChipFlow(draft.desiredRoles) }
}

@Composable
private fun PreviewEntry(title: String, dates: String, details: String = "") {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = HafiType.BodyStrong)
        if (dates.isNotEmpty()) Text(dates, style = HafiType.Caption, color = Hafi.FgMuted)
        if (details.isNotBlank()) Text(details.trim(), style = HafiType.Body, color = Hafi.FgMuted)
    }
}
