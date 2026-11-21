package com.hafiyanjye.app.ui.cvstudio

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.hafiyanjye.app.data.talent.TalentLanguage
import com.hafiyanjye.app.data.talent.TalentRules

/** Web template-minimal.tsx: a light name in the accent, then label-in-the-margin rows. */
@Composable
internal fun TemplateMinimal(content: CvContent, accent: Color) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(72.dp),
    ) {
        MinimalHeader(content, accent)
        Box(
            Modifier
                .padding(vertical = 48.dp)
                .fillMaxWidth()
                .height(1.dp)
                .background(CvInk.Rule),
        )
        Column(verticalArrangement = Arrangement.spacedBy(40.dp)) {
            MinimalRows(content, accent)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MinimalHeader(content: CvContent, accent: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .cvBlock(),
        horizontalArrangement = Arrangement.spacedBy(32.dp),
    ) {
        Column(Modifier.weight(1f)) {
            // Only Poppins ships with the app and it has no Light file; the system draws the nearest weight.
            CvText(
                text = content.name,
                style = cvStyle(40f, accent, leading = 1.05f, weight = FontWeight.Light, tracking = -0.03f),
                modifier = Modifier.semantics { heading() },
                maxLines = 2,
            )
            if (content.headline.isNotEmpty()) {
                CvText(
                    text = content.headline,
                    style = cvStyle(14f, CvInk.Ink, leading = 1.4f),
                    modifier = Modifier.padding(top = 12.dp),
                    maxLines = 2,
                )
            }
            if (content.contacts.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier.padding(top = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    val style = cvStyle(11f, CvInk.Muted)
                    content.contacts.forEachIndexed { index, contact ->
                        if (index > 0) CvText("/", style.copy(color = CvInk.Rule))
                        CvText(contact.text, style, Modifier.widthIn(max = 260.dp))
                    }
                }
            }
        }
        content.photo?.let { CvPhoto(it, 88.dp) }
    }
}

/** Two columns: a quiet label in a 120-unit margin, content on the right. */
@Composable
private fun MinimalRow(label: String, modifier: Modifier = Modifier, body: @Composable ColumnScope.() -> Unit) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
        CvText(
            text = label.uppercase(),
            style = cvStyle(10f, CvInk.Muted, weight = FontWeight.Medium, tracking = 0.22f),
            modifier = Modifier
                .width(120.dp)
                .padding(top = 3.dp)
                .semantics { heading() },
        )
        Column(Modifier.weight(1f), content = body)
    }
}

@Composable
private fun MinimalDates(start: String, end: String?, modifier: Modifier = Modifier) {
    CvText(TalentRules.formatCvRange(start, end), cvStyle(10.5f, CvInk.Muted, tabular = true), modifier)
}

private fun withQuietTail(strong: String, tail: String?) = buildAnnotatedString {
    append(strong)
    if (!tail.isNullOrBlank()) {
        withStyle(SpanStyle(fontWeight = FontWeight.Normal, color = CvInk.Muted)) { append("  ·  $tail") }
    }
}

@Composable
private fun MinimalRows(content: CvContent, accent: Color) {
    if (content.summary.isNotEmpty()) {
        MinimalRow("Profile", Modifier.cvBlock()) {
            CvText(content.summary, cvStyle(12f, CvInk.Body, leading = 1.7f), maxLines = 6)
        }
    }
    if (content.experience.isNotEmpty()) {
        MinimalRow("Experience") {
            Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                content.experience.forEach { job ->
                    Column(Modifier.cvBlock()) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            CvText(
                                text = withQuietTail(job.role, job.company),
                                style = cvStyle(13f, CvInk.Ink, leading = 1.4f, weight = FontWeight.Medium),
                                modifier = Modifier
                                    .weight(1f)
                                    .alignByBaseline(),
                            )
                            MinimalDates(job.start, job.end, Modifier.alignByBaseline())
                        }
                        Bullets(job.details, marker = accent, max = 4)
                    }
                }
            }
        }
    }
    if (content.education.isNotEmpty()) {
        MinimalRow("Education") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                content.education.forEach { school ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .cvBlock(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        CvText(
                            text = withQuietTail(
                                school.degree.ifBlank { school.school },
                                school.school.takeIf { school.degree.isNotBlank() },
                            ),
                            style = cvStyle(12.5f, CvInk.Ink, leading = 1.4f, weight = FontWeight.Medium),
                            modifier = Modifier
                                .weight(1f)
                                .alignByBaseline(),
                            maxLines = 2,
                        )
                        MinimalDates(school.start, school.end, Modifier.alignByBaseline())
                    }
                }
            }
        }
    }
    if (content.skills.isNotEmpty()) {
        MinimalRow("Skills", Modifier.cvBlock()) {
            CvText(content.skills.joinToString(", "), cvStyle(11.5f, CvInk.Body, leading = 1.8f), maxLines = 3)
        }
    }
    if (content.languages.isNotEmpty()) {
        MinimalRow("Languages", Modifier.cvBlock()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                content.languages.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(40.dp)) {
                        pair.forEach { MinimalLanguage(it, Modifier.weight(1f)) }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun MinimalLanguage(language: TalentLanguage, modifier: Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            CvText(language.name, cvStyle(11.5f, CvInk.Ink), Modifier.weight(1f, fill = false))
            CvText(language.level.label, cvStyle(10f, CvInk.Muted), Modifier.padding(start = 8.dp))
        }
        LevelBars(language.level, on = CvInk.Ink, off = CvInk.Rule)
    }
}
