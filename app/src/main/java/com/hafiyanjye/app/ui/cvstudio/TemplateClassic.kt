package com.hafiyanjye.app.ui.cvstudio

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.hafiyanjye.app.data.talent.TalentLanguage
import com.hafiyanjye.app.data.talent.TalentRules

/** Web template-classic.tsx: a centred letterhead, a double rule, then full-width sections. */
@Composable
internal fun TemplateClassic(content: CvContent, accent: Color) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 72.dp, vertical = 64.dp),
    ) {
        ClassicHeader(content, accent)
        Column(
            modifier = Modifier.padding(vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(CvInk.Ink))
            Box(Modifier.fillMaxWidth().height(1.dp).background(CvInk.Rule))
        }
        Column(verticalArrangement = Arrangement.spacedBy(32.dp)) {
            ClassicSections(content)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ClassicHeader(content: CvContent, accent: Color) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .cvBlock(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        content.photo?.let { CvPhoto(it, 88.dp, modifier = Modifier.padding(bottom = 20.dp)) }
        CvText(
            text = content.name,
            style = cvStyle(32f, CvInk.Ink, leading = 1.12f, weight = FontWeight.SemiBold, tracking = -0.01f),
            modifier = Modifier.semantics { heading() },
            maxLines = 2,
            align = TextAlign.Center,
        )
        if (content.headline.isNotEmpty()) {
            CvText(
                text = content.headline.uppercase(),
                style = cvStyle(11f, accent, tracking = 0.2f),
                modifier = Modifier.padding(top = 10.dp),
                maxLines = 2,
                align = TextAlign.Center,
            )
        }
        if (content.contacts.isNotEmpty()) {
            FlowRow(
                modifier = Modifier.padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                val style = cvStyle(11f, CvInk.Body)
                content.contacts.forEachIndexed { index, contact ->
                    if (index > 0) CvText("|", style.copy(color = CvInk.Rule))
                    CvText(contact.text, style, Modifier.widthIn(max = 260.dp))
                }
            }
        }
    }
}

/** Letter-spaced caps followed by a thin rule running to the margin. */
@Composable
internal fun RuledHeading(title: String, tracking: Float, size: Float = 10.5f) {
    Row(
        modifier = Modifier.padding(bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CvText(
            text = title.uppercase(),
            style = cvStyle(size, CvInk.Ink, weight = FontWeight.SemiBold, tracking = tracking),
            modifier = Modifier.semantics { heading() },
        )
        Box(Modifier.weight(1f).height(1.dp).background(CvInk.Rule))
    }
}

@Composable
private fun ClassicDates(start: String, end: String?, modifier: Modifier = Modifier) {
    CvText(
        text = TalentRules.formatCvRange(start, end),
        style = cvStyle(11f, CvInk.Muted, italic = true, tabular = true),
        modifier = modifier.widthIn(min = 104.dp),
        align = TextAlign.End,
    )
}

/** "Role, Company": the first part strong in ink, the rest in body grey. */
private fun strongThenRest(strong: String, rest: String?) = buildAnnotatedString {
    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(strong) }
    if (!rest.isNullOrBlank()) {
        withStyle(SpanStyle(color = CvInk.Body)) { append(", $rest") }
    }
}

@Composable
private fun ClassicSections(content: CvContent) {
    if (content.summary.isNotEmpty()) {
        Column(Modifier.cvBlock()) {
            RuledHeading("Profile", tracking = 0.24f)
            CvText(content.summary, cvStyle(12f, CvInk.Body, leading = 1.65f), maxLines = 6)
        }
    }
    if (content.experience.isNotEmpty()) {
        Column {
            RuledHeading("Experience", tracking = 0.24f)
            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                content.experience.forEach { job ->
                    Column(Modifier.cvBlock()) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            CvText(
                                text = strongThenRest(job.role, job.company),
                                style = cvStyle(13f, CvInk.Ink, leading = 1.4f),
                                modifier = Modifier
                                    .weight(1f)
                                    .alignByBaseline(),
                            )
                            ClassicDates(job.start, job.end, Modifier.alignByBaseline())
                        }
                        Bullets(job.details, marker = CvInk.Ink)
                    }
                }
            }
        }
    }
    if (content.education.isNotEmpty()) {
        Column {
            RuledHeading("Education", tracking = 0.24f)
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                content.education.forEach { school ->
                    val rest = school.school.takeIf { school.degree.isNotBlank() }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .cvBlock(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        CvText(
                            text = strongThenRest(school.degree.ifBlank { school.school }, rest),
                            style = cvStyle(12.5f, CvInk.Ink, leading = 1.4f),
                            modifier = Modifier
                                .weight(1f)
                                .alignByBaseline(),
                        )
                        ClassicDates(school.start, school.end, Modifier.alignByBaseline())
                    }
                }
            }
        }
    }
    if (content.skills.isNotEmpty() || content.languages.isNotEmpty()) {
        Row(
            modifier = Modifier.cvBlock(),
            horizontalArrangement = Arrangement.spacedBy(48.dp),
        ) {
            if (content.skills.isNotEmpty()) {
                Column(Modifier.weight(3f)) {
                    RuledHeading("Skills", tracking = 0.24f)
                    CvText(
                        text = buildAnnotatedString {
                            content.skills.forEachIndexed { index, skill ->
                                if (index > 0) withStyle(SpanStyle(color = CvInk.Muted)) { append("  ·  ") }
                                append(skill)
                            }
                        },
                        style = cvStyle(11.5f, CvInk.Body, leading = 1.75f),
                        maxLines = 4,
                    )
                }
            }
            if (content.languages.isNotEmpty()) {
                Column(Modifier.weight(2f)) {
                    RuledHeading("Languages", tracking = 0.24f)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        content.languages.forEach { ClassicLanguage(it) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ClassicLanguage(language: TalentLanguage) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CvText(
            text = language.name,
            style = cvStyle(11.5f, CvInk.Ink, weight = FontWeight.SemiBold),
            modifier = Modifier.weight(1f),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            CvText(language.level.label, cvStyle(11.5f, CvInk.Muted, italic = true))
            LevelBars(language.level, on = CvInk.Ink, off = CvInk.Rule)
        }
    }
}
