package com.hafiyanjye.app.ui.talent

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hafiyanjye.app.R
import com.hafiyanjye.app.data.talent.TalentEducation
import com.hafiyanjye.app.data.talent.TalentExperience
import com.hafiyanjye.app.data.talent.TalentLanguage
import com.hafiyanjye.app.data.talent.TalentLanguageLevel
import com.hafiyanjye.app.data.talent.TalentLimits
import com.hafiyanjye.app.data.talent.TalentLink
import com.hafiyanjye.app.data.talent.TalentProfileDto
import com.hafiyanjye.app.data.talent.TalentRules
import com.hafiyanjye.app.ui.kit.HafiChip
import com.hafiyanjye.app.ui.kit.HafiField
import com.hafiyanjye.app.ui.kit.HafiIconButton
import com.hafiyanjye.app.ui.kit.HafiSwitchRow
import com.hafiyanjye.app.ui.kit.HafiTextArea
import com.hafiyanjye.app.ui.kit.SegmentedControl
import com.hafiyanjye.app.ui.theme.Hafi
import com.hafiyanjye.app.ui.theme.HafiType

/** The four Kinyarwanda, English, French, Swahili chips, any others, a field for more, then each one's level. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LanguageFields(draft: TalentProfileDto, edit: DraftEdit, enabled: Boolean) {
    var typed by rememberSaveable { mutableStateOf("") }
    val languages = draft.languages
    val full = languages.size >= TalentLimits.Languages
    val addTyped = {
        edit { it.copy(languages = TalentRules.addLanguage(it.languages, typed)) }
        typed = ""
    }
    val others = languages
        .map { it.name }
        .filter { name -> TalentRules.commonLanguages.none { it.equals(name, ignoreCase = true) } }

    FieldGroup("Languages (${languages.size}/${TalentLimits.Languages})") {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            (TalentRules.commonLanguages + others).forEach { name ->
                val chosen = languages.any { it.name.equals(name, ignoreCase = true) }
                HafiChip(
                    text = name,
                    selected = chosen,
                    onClick = { edit { it.copy(languages = TalentRules.toggleLanguage(it.languages, name)) } },
                    enabled = enabled && (chosen || !full),
                )
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            HafiField(
                label = "Another language",
                value = typed,
                onValueChange = { typed = it.take(TalentLimits.LanguageName) },
                placeholder = "Lingala",
                enabled = enabled && !full,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { addTyped() }),
                modifier = Modifier.weight(1f),
            )
            HafiIconButton(
                icon = R.drawable.ic_plus,
                label = "Add language",
                onClick = addTyped,
                enabled = enabled && !full && typed.isNotBlank(),
                modifier = Modifier
                    .padding(bottom = 2.dp)
                    .border(1.dp, Hafi.LineStrong),
            )
        }
    }
    if (languages.isNotEmpty()) {
        FieldGroup("Level") {
            languages.forEach { language -> LanguageLevelRow(language, edit, enabled) }
        }
    }
}

@Composable
private fun LanguageLevelRow(language: TalentLanguage, edit: DraftEdit, enabled: Boolean) {
    val levels = TalentLanguageLevel.entries
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "${language.name} level",
            style = HafiType.Label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        SegmentedControl(
            options = levels.map { it.label },
            selectedIndex = levels.indexOf(language.level),
            onSelect = { index ->
                edit { profile ->
                    profile.copy(
                        languages = profile.languages.map {
                            if (it.name == language.name) it.copy(level = levels[index]) else it
                        },
                    )
                }
            },
            enabled = enabled,
        )
    }
}

/** Jobs, newest first as the person orders them; up to the engine's 15. */
@Composable
internal fun ExperienceFields(draft: TalentProfileDto, edit: DraftEdit, enabled: Boolean) {
    val jobs = draft.experience
    fun change(index: Int, transform: (TalentExperience) -> TalentExperience) = edit { profile ->
        profile.copy(experience = profile.experience.mapIndexed { i, job -> if (i == index) transform(job) else job })
    }
    jobs.forEachIndexed { index, job ->
        RowCard(
            index = index,
            count = jobs.size,
            heading = listOf(job.role.trim(), job.company.trim()).filter { it.isNotEmpty() }.joinToString(" · "),
            placeholder = "New role",
            onMove = { from, to -> edit { it.copy(experience = it.experience.moved(from, to)) } },
            onRemove = { edit { it.copy(experience = it.experience.filterIndexed { i, _ -> i != index }) } },
        ) {
            HafiField(
                label = "Job title",
                value = job.role,
                onValueChange = { value -> change(index) { it.copy(role = value.take(TalentLimits.Text)) } },
                placeholder = "Sales agent",
                enabled = enabled,
            )
            HafiField(
                label = "Company",
                value = job.company,
                onValueChange = { value -> change(index) { it.copy(company = value.take(TalentLimits.Text)) } },
                placeholder = "MTN Rwanda",
                enabled = enabled,
            )
            DateRange(
                start = job.start,
                end = job.end,
                currentLabel = "Current role",
                enabled = enabled,
                onStart = { value -> change(index) { it.copy(start = value) } },
                onEnd = { value -> change(index) { it.copy(end = value) } },
            )
            HafiTextArea(
                label = "Highlights",
                value = job.details,
                onValueChange = { value -> change(index) { it.copy(details = value) } },
                placeholder = "Grew monthly sales in Nyarugenge by 20%\nTrained 4 new agents",
                hint = "One per line.",
                maxLength = TalentLimits.Details,
                minLines = 3,
                enabled = enabled,
            )
        }
    }
    AddRow(
        label = "Add role",
        onClick = { edit { it.copy(experience = it.experience + TalentExperience.blank()) } },
        enabled = enabled && jobs.size < TalentLimits.Experience,
    )
}

/** Schools, courses and certificates; up to the engine's 10. */
@Composable
internal fun EducationFields(draft: TalentProfileDto, edit: DraftEdit, enabled: Boolean) {
    val schools = draft.education
    fun change(index: Int, transform: (TalentEducation) -> TalentEducation) = edit { profile ->
        val schools = profile.education.mapIndexed { i, school -> if (i == index) transform(school) else school }
        profile.copy(education = schools)
    }
    schools.forEachIndexed { index, school ->
        RowCard(
            index = index,
            count = schools.size,
            heading = listOf(school.degree.trim(), school.school.trim()).filter { it.isNotEmpty() }.joinToString(" · "),
            placeholder = "New school",
            onMove = { from, to -> edit { it.copy(education = it.education.moved(from, to)) } },
            onRemove = { edit { it.copy(education = it.education.filterIndexed { i, _ -> i != index }) } },
        ) {
            HafiField(
                label = "School",
                value = school.school,
                onValueChange = { value -> change(index) { it.copy(school = value.take(TalentLimits.Text)) } },
                placeholder = "University of Rwanda",
                enabled = enabled,
            )
            HafiField(
                label = "Degree",
                value = school.degree,
                onValueChange = { value -> change(index) { it.copy(degree = value.take(TalentLimits.Text)) } },
                placeholder = "BSc Accounting",
                enabled = enabled,
            )
            DateRange(
                start = school.start,
                end = school.end,
                currentLabel = "Studying here",
                enabled = enabled,
                onStart = { value -> change(index) { it.copy(start = value) } },
                onEnd = { value -> change(index) { it.copy(end = value) } },
            )
        }
    }
    AddRow(
        label = "Add school",
        onClick = { edit { it.copy(education = it.education + TalentEducation.blank()) } },
        enabled = enabled && schools.size < TalentLimits.Education,
    )
}

/**
 * Start and end side by side, then the "current" switch. While current,
 * [end] is null and the end box shows "Now", like the web's disabled month field.
 */
@Composable
private fun DateRange(
    start: String,
    end: String?,
    currentLabel: String,
    enabled: Boolean,
    onStart: (String) -> Unit,
    onEnd: (String?) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        DateField(
            label = "Start",
            value = start,
            onChange = onStart,
            error = TalentRules.dateError(start),
            enabled = enabled,
            modifier = Modifier.weight(1f),
        )
        DateField(
            label = "End",
            value = end,
            onChange = onEnd,
            error = TalentRules.dateError(end) ?: TalentRules.rangeError(start, end),
            enabled = enabled && end != null,
            modifier = Modifier.weight(1f),
        )
    }
    HafiSwitchRow(
        title = currentLabel,
        checked = end == null,
        onCheckedChange = { current -> onEnd(if (current) null else "") },
        enabled = enabled,
    )
}

/** Web links; the label is optional and guessed from the address when left empty. */
@Composable
internal fun LinkFields(draft: TalentProfileDto, edit: DraftEdit, enabled: Boolean) {
    val links = draft.links
    fun change(index: Int, transform: (TalentLink) -> TalentLink) = edit { profile ->
        profile.copy(links = profile.links.mapIndexed { i, link -> if (i == index) transform(link) else link })
    }
    links.forEachIndexed { index, link ->
        val heading = link.label.trim().ifEmpty {
            if (link.url.isNotBlank()) TalentRules.guessLabel(link.url) else ""
        }
        RowCard(
            index = index,
            count = links.size,
            heading = heading,
            placeholder = "New link",
            onMove = { from, to -> edit { it.copy(links = it.links.moved(from, to)) } },
            onRemove = { edit { it.copy(links = it.links.filterIndexed { i, _ -> i != index }) } },
        ) {
            HafiField(
                label = "URL",
                value = link.url,
                onValueChange = { value -> change(index) { it.copy(url = value.take(TalentLimits.LinkUrl)) } },
                placeholder = "linkedin.com/in/aline-uwase",
                error = TalentRules.urlError(link.url),
                enabled = enabled,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            )
            HafiField(
                label = "Label",
                value = link.label,
                onValueChange = { value -> change(index) { it.copy(label = value.take(TalentLimits.LinkLabel)) } },
                placeholder = "LinkedIn",
                hint = "Optional. We guess it from the URL.",
                enabled = enabled,
            )
        }
    }
    AddRow(
        label = "Add link",
        onClick = { edit { it.copy(links = it.links + TalentLink(url = "")) } },
        enabled = enabled && links.size < TalentLimits.Links,
    )
}

/** A small heading over a group of fields. */
@Composable
internal fun FieldGroup(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = title.uppercase(),
            style = HafiType.Micro,
            color = Hafi.FgMuted,
            modifier = Modifier.semantics { heading() },
        )
        content()
    }
}
