package com.hafiyanjye.app.ui.cvstudio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hafiyanjye.app.data.talent.TalentRules
import com.hafiyanjye.app.ui.kit.HIcon

/** Width of the coloured column: a third of the page, as on the web. */
internal const val SIDEBAR_WIDTH = CvPageLayout.WIDTH * 0.33f

/**
 * Web template-sidebar.tsx: contact, skills and languages in a coloured
 * column, the rest beside it. The column's colour is painted by the page
 * itself (see [CvPageFrame]) so it runs the full height of every page.
 */
@Composable
internal fun TemplateSidebar(content: CvContent, accent: Color, onAccent: Color) {
    Row(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .width(SIDEBAR_WIDTH.dp)
                .padding(horizontal = 32.dp, vertical = 48.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            SidebarColumn(content, onAccent)
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 44.dp, vertical = 48.dp),
            verticalArrangement = Arrangement.spacedBy(32.dp),
        ) {
            SidebarMain(content, accent)
        }
    }
}

@Composable
private fun SideSection(title: String, onAccent: Color, first: Boolean, body: @Composable ColumnScope.() -> Unit) {
    val ruled = if (first) {
        Modifier
    } else {
        Modifier
            .rule(onAccent.copy(alpha = 0x33 / 255f), top = true)
            .padding(top = 24.dp)
    }
    Column(ruled.cvBlock()) {
        CvText(
            text = title.uppercase(),
            style = cvStyle(10f, onAccent.copy(alpha = 0.7f), weight = FontWeight.SemiBold, tracking = 0.22f),
            modifier = Modifier
                .padding(bottom = 12.dp)
                .semantics { heading() },
        )
        body()
    }
}

@Composable
private fun SidebarColumn(content: CvContent, onAccent: Color) {
    content.photo?.let {
        CvPhoto(it, 160.dp, ring = onAccent, ringWidth = 1.dp, modifier = Modifier.padding(bottom = 8.dp))
    }
    var first = true
    if (content.contacts.isNotEmpty()) {
        SideSection("Contact", onAccent, first) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                content.contacts.forEach { contact ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        HIcon(
                            id = contact.icon,
                            size = 12.dp,
                            tint = onAccent.copy(alpha = 0.7f),
                            modifier = Modifier.padding(top = 2.dp),
                        )
                        CvText(contact.text, cvStyle(11f, onAccent, leading = 1.45f), maxLines = Int.MAX_VALUE)
                    }
                }
            }
        }
        first = false
    }
    if (content.skills.isNotEmpty()) {
        SideSection("Skills", onAccent, first) {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                content.skills.take(14).forEach { CvText(it, cvStyle(11f, onAccent, leading = 1.45f)) }
            }
        }
        first = false
    }
    if (content.languages.isNotEmpty()) {
        SideSection("Languages", onAccent, first) {
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
                                style = cvStyle(11f, onAccent, weight = FontWeight.Medium),
                                modifier = Modifier.weight(1f),
                            )
                            LevelBars(language.level, on = onAccent, off = onAccent.copy(alpha = 0x40 / 255f))
                        }
                        CvText(language.level.label, cvStyle(10f, onAccent.copy(alpha = 0.7f)))
                    }
                }
            }
        }
    }
}

@Composable
private fun SidebarMain(content: CvContent, accent: Color) {
    Column(Modifier.cvBlock()) {
        CvText(
            text = content.name,
            style = cvStyle(32f, CvInk.Ink, leading = 1.08f, weight = FontWeight.SemiBold, tracking = -0.02f),
            modifier = Modifier.semantics { heading() },
            maxLines = 2,
        )
        if (content.headline.isNotEmpty()) {
            CvText(
                text = content.headline.uppercase(),
                style = cvStyle(11f, accent, weight = FontWeight.Medium, tracking = 0.16f),
                modifier = Modifier.padding(top = 10.dp),
                maxLines = 2,
            )
        }
        if (content.summary.isNotEmpty()) {
            CvText(
                text = content.summary,
                style = cvStyle(12f, CvInk.Body, leading = 1.6f),
                modifier = Modifier.padding(top = 24.dp),
                maxLines = 6,
            )
        }
    }
    if (content.experience.isNotEmpty()) {
        Column {
            RuledHeading("Experience", tracking = 0.2f)
            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                content.experience.forEach { job ->
                    Column(Modifier.cvBlock()) {
                        TitleAndDates(job.role, TalentRules.formatCvRange(job.start, job.end), 13f)
                        if (job.company.isNotBlank()) {
                            CvText(job.company, cvStyle(11.5f, CvInk.Body, leading = 1.45f))
                        }
                        Bullets(job.details, marker = accent, max = 4)
                    }
                }
            }
        }
    }
    if (content.education.isNotEmpty()) {
        Column {
            RuledHeading("Education", tracking = 0.2f)
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                content.education.forEach { school ->
                    Column(Modifier.cvBlock()) {
                        TitleAndDates(
                            title = school.degree.ifBlank { school.school },
                            dates = TalentRules.formatCvRange(school.start, school.end),
                            size = 12.5f,
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
