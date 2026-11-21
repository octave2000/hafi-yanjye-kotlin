package com.hafiyanjye.app.ui.cvstudio

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hafiyanjye.app.data.talent.TalentRules
import com.hafiyanjye.app.ui.kit.HIcon

/** Web template-bold.tsx: a full-width accent header, then content beside a 200-unit aside. */
@Composable
internal fun TemplateBold(content: CvContent, accent: Color, onAccent: Color) {
    Column(Modifier.fillMaxWidth()) {
        BoldHeader(content, accent, onAccent)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 56.dp, end = 56.dp, top = 40.dp, bottom = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(40.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(32.dp)) {
                BoldMain(content, accent)
            }
            if (content.skills.isNotEmpty() || content.languages.isNotEmpty()) {
                Column(Modifier.width(200.dp), verticalArrangement = Arrangement.spacedBy(32.dp)) {
                    BoldAside(content, accent)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BoldHeader(content: CvContent, accent: Color, onAccent: Color) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(accent)
            .padding(start = 56.dp, end = 56.dp, top = 48.dp, bottom = 32.dp)
            .cvBlock(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(32.dp)) {
            content.photo?.let { CvPhoto(it, 104.dp, ring = onAccent, ringWidth = 2.dp) }
            Column(Modifier.weight(1f)) {
                CvText(
                    text = content.name,
                    style = cvStyle(34f, onAccent, leading = 1.08f, weight = FontWeight.SemiBold, tracking = -0.02f),
                    modifier = Modifier.semantics { heading() },
                    maxLines = 2,
                )
                if (content.headline.isNotEmpty()) {
                    CvText(
                        text = content.headline,
                        style = cvStyle(14f, onAccent.copy(alpha = 0.85f), leading = 1.4f),
                        modifier = Modifier.padding(top = 8.dp),
                        maxLines = 2,
                    )
                }
            }
        }
        if (content.contacts.isNotEmpty()) {
            FlowRow(
                modifier = Modifier
                    .padding(top = 32.dp)
                    .fillMaxWidth()
                    .rule(onAccent.copy(alpha = 0x38 / 255f), top = true)
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                content.contacts.forEach { contact ->
                    Row(
                        modifier = Modifier.widthIn(max = 300.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        HIcon(contact.icon, size = 12.dp, tint = onAccent.copy(alpha = 0.7f))
                        CvText(contact.text, cvStyle(11f, onAccent))
                    }
                }
            }
        }
    }
}

/** Small caps over a hairline, with a short accent tick on its left end. */
@Composable
private fun BoldHeading(title: String, accent: Color) {
    CvText(
        text = title.uppercase(),
        style = cvStyle(10.5f, CvInk.Ink, weight = FontWeight.SemiBold, tracking = 0.18f),
        modifier = Modifier
            .padding(bottom = 16.dp)
            .fillMaxWidth()
            .drawBehind {
                val stroke = 1.dp.toPx()
                val y = size.height - stroke / 2
                drawLine(CvInk.Rule, Offset(0f, y), Offset(size.width, y), stroke)
                drawLine(accent, Offset(0f, y), Offset(24.dp.toPx(), y), stroke)
            }
            .padding(bottom = 8.dp)
            .semantics { heading() },
    )
}

@Composable
private fun BoldMain(content: CvContent, accent: Color) {
    if (content.summary.isNotEmpty()) {
        Column(Modifier.cvBlock()) {
            BoldHeading("Profile", accent)
            CvText(content.summary, cvStyle(12f, CvInk.Body, leading = 1.6f), maxLines = 6)
        }
    }
    if (content.experience.isNotEmpty()) {
        Column {
            BoldHeading("Experience", accent)
            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                content.experience.forEach { job ->
                    Column(Modifier.cvBlock()) {
                        TitleAndDates(job.role, TalentRules.formatCvRange(job.start, job.end), 13f, tracking = 0.02f)
                        if (job.company.isNotBlank()) {
                            CvText(job.company, cvStyle(11.5f, accent, leading = 1.45f, weight = FontWeight.Medium))
                        }
                        Bullets(job.details, marker = CvInk.Muted)
                    }
                }
            }
        }
    }
    if (content.education.isNotEmpty()) {
        Column {
            BoldHeading("Education", accent)
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                content.education.forEach { school ->
                    Column(Modifier.cvBlock()) {
                        TitleAndDates(
                            title = school.degree.ifBlank { school.school },
                            dates = TalentRules.formatCvRange(school.start, school.end),
                            size = 12.5f,
                            tracking = 0.02f,
                        )
                        if (school.degree.isNotBlank() && school.school.isNotBlank()) {
                            CvText(school.school, cvStyle(11.5f, CvInk.Body, leading = 1.45f), maxLines = 2)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BoldAside(content: CvContent, accent: Color) {
    if (content.skills.isNotEmpty()) {
        Column(Modifier.cvBlock()) {
            BoldHeading("Skills", accent)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                content.skills.take(18).forEach { skill ->
                    CvText(
                        text = skill,
                        style = cvStyle(10.5f, CvInk.Ink, leading = 1.4f),
                        modifier = Modifier
                            .border(1.dp, CvInk.Rule)
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }
        }
    }
    if (content.languages.isNotEmpty()) {
        Column(Modifier.cvBlock()) {
            BoldHeading("Languages", accent)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                content.languages.forEach { language ->
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CvText(
                                text = language.name,
                                style = cvStyle(11.5f, CvInk.Ink, weight = FontWeight.Medium),
                                modifier = Modifier.weight(1f),
                            )
                            LevelBars(language.level, on = accent, off = CvInk.Rule)
                        }
                        CvText(language.level.label, cvStyle(10f, CvInk.Muted))
                    }
                }
            }
        }
    }
}

/**
 * A job or school title with its dates on the same baseline, the dates
 * never squeezed (Bold and Sidebar share this row).
 */
@Composable
internal fun TitleAndDates(title: String, dates: String, size: Float, tracking: Float = 0f) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        CvText(
            text = title,
            style = cvStyle(size, CvInk.Ink, leading = 1.35f, weight = FontWeight.SemiBold),
            modifier = Modifier
                .weight(1f)
                .alignByBaseline(),
        )
        CvText(
            text = dates,
            style = cvStyle(10.5f, CvInk.Muted, tracking = tracking, tabular = true),
            modifier = Modifier.alignByBaseline(),
        )
    }
}
