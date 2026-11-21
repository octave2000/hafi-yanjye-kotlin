package com.hafiyanjye.app.ui.talent

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hafiyanjye.app.R
import com.hafiyanjye.app.data.talent.TalentLanguage
import com.hafiyanjye.app.data.talent.TalentLink
import com.hafiyanjye.app.data.talent.TalentProfileDto
import com.hafiyanjye.app.data.talent.TalentRules
import com.hafiyanjye.app.data.talent.TalentSection
import com.hafiyanjye.app.ui.kit.ButtonKind
import com.hafiyanjye.app.ui.kit.ButtonSize
import com.hafiyanjye.app.ui.kit.Divider
import com.hafiyanjye.app.ui.kit.HIcon
import com.hafiyanjye.app.ui.kit.HafiButton
import com.hafiyanjye.app.ui.kit.HafiIconButton
import com.hafiyanjye.app.ui.kit.ProgressBar
import com.hafiyanjye.app.ui.kit.SectionCard
import com.hafiyanjye.app.ui.kit.Skeleton
import com.hafiyanjye.app.ui.theme.Hafi
import com.hafiyanjye.app.ui.theme.HafiType

/** What the dashboard can ask its screen to do. */
internal class DashboardActions(
    val onEdit: (TalentSection) -> Unit,
    val onInterview: () -> Unit,
    val onStudio: () -> Unit,
    val onShare: () -> Unit,
    val onChooseCv: () -> Unit,
)

/** Profile exists: header, actions, strength, next step, the sections, the CV card and the checklist. */
@Composable
internal fun TalentDashboard(profile: TalentProfileDto, sharing: Boolean, actions: DashboardActions) {
    Hero(profile, onEditBasics = { actions.onEdit(TalentSection.Basics) })
    HeroActions(sharing, actions)
    ProgressBar(profile.completeness / 100f, label = "Profile strength")
    NextStep(profile, actions)
    TalentSection.entries
        .filter { it != TalentSection.Basics }
        .forEach { section -> Panel(section, profile, onOpen = { actions.onEdit(section) }) }
    CvCard(profile, actions.onStudio)
    Checklist(profile.missing, actions.onEdit)
}

@Composable
private fun Hero(profile: TalentProfileDto, onEditBasics: () -> Unit) {
    val name = profile.fullName.trim()
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Portrait(
            name = name,
            photo = profile.photo,
            size = 72.dp,
            modifier = Modifier
                .clickable(role = Role.Button, onClickLabel = "Edit photo and details", onClick = onEditBasics)
                .semantics { contentDescription = "Edit photo and details" },
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (profile.openToWork) StatusPill()
            Text(
                text = name.ifEmpty { "Your name" },
                style = HafiType.Title,
                modifier = Modifier.semantics { heading() },
            )
            if (profile.headline.isNotBlank()) {
                Text(profile.headline, style = HafiType.Body, color = Hafi.FgMuted)
            } else {
                LinkText("Add a headline", onEditBasics)
            }
            ContactLine(profile, onEditBasics)
        }
    }
}

/** Town, email and phone, each with its icon, then a pencil (or words when there is nothing yet). */
@Composable
private fun ContactLine(profile: TalentProfileDto, onEditBasics: () -> Unit) {
    val contact = listOf(
        Triple(R.drawable.ic_map_pin, profile.location.trim(), "Location"),
        Triple(R.drawable.ic_mail, profile.email.trim(), "Email"),
        Triple(R.drawable.ic_phone, profile.phone.trim(), "Phone"),
    ).filter { it.second.isNotEmpty() }
    contact.forEach { (icon, value, label) ->
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HIcon(icon, size = 14.dp, tint = Hafi.FgMuted, label = label)
            Text(
                text = value,
                style = HafiType.Caption,
                color = Hafi.FgMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
    if (contact.isEmpty()) {
        LinkText("Add contact details", onEditBasics)
    } else {
        HafiIconButton(
            icon = R.drawable.ic_pencil,
            label = "Edit contact details",
            onClick = onEditBasics,
            tint = Hafi.FgMuted,
        )
    }
}

@Composable
private fun HeroActions(sharing: Boolean, actions: DashboardActions) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HafiButton(
            text = "Build CV",
            onClick = actions.onStudio,
            modifier = Modifier.fillMaxWidth(),
            size = ButtonSize.Large,
            icon = R.drawable.ic_file_text,
        )
        HafiButton(
            text = "Build with AI",
            onClick = actions.onInterview,
            modifier = Modifier.fillMaxWidth(),
            kind = ButtonKind.Secondary,
            icon = R.drawable.ic_sparkles,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HafiButton(
                text = "Share",
                onClick = actions.onShare,
                modifier = Modifier.weight(1f),
                kind = ButtonKind.Outline,
                icon = R.drawable.ic_share_2,
                loading = sharing,
            )
            HafiButton(
                text = "Re-upload CV",
                onClick = actions.onChooseCv,
                modifier = Modifier.weight(1f),
                kind = ButtonKind.Outline,
                icon = R.drawable.ic_upload,
            )
        }
    }
}

/** Underlined words that act like a button, with a full-height touch target. */
@Composable
private fun LinkText(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(text, style = HafiType.Body, color = Hafi.FgMuted, textDecoration = TextDecoration.Underline)
    }
}

/** Below 40 % the next step is the interview; otherwise the heaviest missing part. */
@Composable
private fun NextStep(profile: TalentProfileDto, actions: DashboardActions) {
    if (profile.completeness < TalentRules.AI_NUDGE_BELOW) {
        AccentCard {
            Eyebrow("Next step")
            Text("Answer 8 quick questions and AI fills in your profile", style = HafiType.BodyStrong)
            HafiButton(
                text = "Build with AI",
                onClick = actions.onInterview,
                kind = ButtonKind.Secondary,
                icon = R.drawable.ic_sparkles,
            )
        }
        return
    }
    val nudge = TalentRules.nextStep(profile.missing) ?: return
    AccentCard {
        Eyebrow("Next step")
        Text(nudge.line, style = HafiType.BodyStrong)
        HafiButton(
            text = "Add",
            onClick = { actions.onEdit(nudge.section) },
            kind = ButtonKind.Secondary,
            icon = R.drawable.ic_arrow_right,
        )
    }
}

@Composable
private fun Panel(section: TalentSection, profile: TalentProfileDto, onOpen: () -> Unit) {
    val empty = section.isEmpty(profile)
    val count = section.count(profile)?.takeIf { !empty }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .semantics(mergeDescendants = true) { heading() },
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(section.title, style = HafiType.Subheading)
                if (count != null) Text(count.toString(), style = HafiType.Subheading, color = Hafi.FgSubtle)
            }
            if (!empty) {
                HafiIconButton(
                    icon = R.drawable.ic_pencil,
                    label = "Edit ${section.title.lowercase()}",
                    onClick = onOpen,
                    tint = Hafi.FgMuted,
                )
            }
        }
        Divider()
        if (empty) {
            HafiButton(
                text = section.addLabel,
                onClick = onOpen,
                modifier = Modifier.fillMaxWidth(),
                kind = ButtonKind.Outline,
                icon = R.drawable.ic_plus,
            )
        } else {
            PanelBody(section, profile)
        }
    }
}

@Composable
private fun PanelBody(section: TalentSection, profile: TalentProfileDto) {
    when (section) {
        TalentSection.Basics -> Unit
        TalentSection.Summary -> Text(profile.summary.trim(), style = HafiType.Body)
        TalentSection.Experience -> Timeline(
            profile.experience.map { job ->
                TimelineItem(
                    title = job.role.ifEmpty { job.company }.ifEmpty { "Untitled role" },
                    sub = joinParts(
                        if (job.role.isNotEmpty()) job.company else "",
                        TalentRules.formatCvRange(job.start, job.end),
                    ),
                    current = job.end == null && job.start.isNotEmpty(),
                    details = job.details.trim(),
                )
            },
        )
        TalentSection.Education -> Timeline(
            profile.education.map { school ->
                TimelineItem(
                    title = school.school.ifEmpty { school.degree },
                    sub = joinParts(
                        if (school.school.isNotEmpty()) school.degree else "",
                        TalentRules.formatCvRange(school.start, school.end),
                    ),
                )
            },
        )
        TalentSection.Skills -> ChipFlow(profile.skills)
        TalentSection.Goals -> ChipFlow(profile.desiredRoles)
        TalentSection.Languages -> LanguageList(profile.languages)
        TalentSection.Links -> LinkList(profile.links)
    }
}

private fun joinParts(vararg parts: String): String = parts.filter { it.isNotEmpty() }.joinToString(" · ")

@Composable
private fun LanguageList(languages: List<TalentLanguage>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        languages.forEach { language ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics(mergeDescendants = true) {},
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = language.name,
                    style = HafiType.BodyStrong,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(language.level.label, style = HafiType.Caption, color = Hafi.FgMuted)
                LevelBars(language.level)
            }
        }
    }
}

/** Links open in the browser: they are the person's own pages, not maps or routes. */
@Composable
private fun LinkList(links: List<TalentLink>) {
    val uris = LocalUriHandler.current
    Column {
        links.forEach { link ->
            val domain = TalentRules.domainOf(link.url)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clickable(role = Role.Button, onClickLabel = "Open link") {
                        // A link the browser can't open must not crash the tab.
                        runCatching { uris.openUri(link.url) }
                    },
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = link.label.ifEmpty { domain },
                    style = HafiType.BodyStrong,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (link.label.isNotEmpty()) {
                    Text(
                        text = domain,
                        style = HafiType.Caption,
                        color = Hafi.FgMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
                HIcon(R.drawable.ic_arrow_up_right, size = 16.dp, tint = Hafi.FgMuted)
            }
        }
    }
}

private data class TimelineItem(
    val title: String,
    val sub: String,
    val current: Boolean = false,
    val details: String = "",
)

/** A vertical rule with square markers; the current job's marker is filled and says "Current". */
@Composable
private fun Timeline(items: List<TimelineItem>) {
    Column {
        items.forEachIndexed { index, item ->
            val last = index == items.lastIndex
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .drawBehind {
                        val ruleX = 4.dp.toPx()
                        if (!last) {
                            drawRect(
                                color = Hafi.Line,
                                topLeft = Offset(ruleX, 8.dp.toPx()),
                                size = Size(1.dp.toPx(), size.height),
                            )
                        }
                        val side = 9.dp.toPx()
                        val corner = Offset(0f, 7.dp.toPx())
                        drawRect(if (item.current) Hafi.Accent else Hafi.Surface, corner, Size(side, side))
                        if (!item.current) {
                            drawRect(Hafi.LineStrong, corner, Size(side, side), style = Stroke(1.dp.toPx()))
                        }
                    }
                    .padding(start = 24.dp, bottom = if (last) 0.dp else 20.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(item.title, style = HafiType.BodyStrong)
                val sub = joinParts(item.sub, if (item.current) "Current" else "")
                if (sub.isNotEmpty()) Text(sub, style = HafiType.Caption, color = Hafi.FgMuted)
                if (item.details.isNotEmpty()) {
                    Text(
                        text = item.details,
                        style = HafiType.Label,
                        color = Hafi.FgMuted,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }
    }
}

/** The way into the CV studio, with the chosen template and whether the public link is on. */
@Composable
private fun CvCard(profile: TalentProfileDto, onStudio: () -> Unit) {
    SectionCard(title = "Your CV") {
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconMark(R.drawable.ic_file_user)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("${profile.style.template.label} template", style = HafiType.BodyStrong)
                Text(
                    text = if (profile.isPublic) "Public link on" else "Private",
                    style = HafiType.Caption,
                    color = Hafi.FgMuted,
                )
            }
        }
        HafiButton(
            text = "Open studio",
            onClick = onStudio,
            modifier = Modifier.fillMaxWidth(),
            kind = ButtonKind.Outline,
            icon = R.drawable.ic_arrow_right,
        )
    }
}

@Composable
private fun Checklist(missing: List<String>, onEdit: (TalentSection) -> Unit) {
    val items = TalentRules.checklist
    val done = items.count { it.key !in missing }
    SectionCard(
        title = "Checklist",
        action = {
            Text(
                text = "$done/${items.size}",
                style = HafiType.Label,
                color = Hafi.Fg,
                modifier = Modifier.semantics { contentDescription = "$done of ${items.size} done" },
            )
        },
    ) {
        Column {
            items.forEach { item ->
                val isDone = item.key !in missing
                val rowModifier = if (isDone) {
                    Modifier.semantics(mergeDescendants = true) { stateDescription = "Done" }
                } else {
                    Modifier.clickable(role = Role.Button, onClickLabel = "Add") { onEdit(item.section) }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .then(rowModifier),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (isDone) {
                        HIcon(R.drawable.ic_check, size = 16.dp, tint = Hafi.AccentStrong)
                    } else {
                        Box(
                            Modifier
                                .size(16.dp)
                                .border(1.dp, Hafi.LineStrong),
                        )
                    }
                    Text(
                        text = item.label,
                        style = HafiType.Label,
                        color = if (isDone) Hafi.FgMuted else Hafi.Fg,
                        modifier = Modifier.weight(1f),
                    )
                    if (!isDone) Text("Add", style = HafiType.Caption, color = Hafi.AccentStrong)
                }
            }
        }
    }
}

/** Same layout as the dashboard, in hairline-coloured blocks. */
@Composable
internal fun DashboardSkeleton() {
    Column(
        modifier = Modifier.semantics { contentDescription = "Loading your profile" },
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Skeleton(Modifier.size(72.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Skeleton(
                    Modifier
                        .fillMaxWidth(0.7f)
                        .height(28.dp),
                )
                Skeleton(
                    Modifier
                        .fillMaxWidth(0.5f)
                        .height(16.dp),
                )
                Skeleton(
                    Modifier
                        .fillMaxWidth(0.8f)
                        .height(12.dp),
                )
            }
        }
        Skeleton(
            Modifier
                .fillMaxWidth()
                .height(54.dp),
        )
        Skeleton(
            Modifier
                .fillMaxWidth()
                .height(6.dp),
        )
        repeat(3) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Skeleton(
                    Modifier
                        .width(96.dp)
                        .height(14.dp),
                )
                Divider()
                Skeleton(
                    Modifier
                        .fillMaxWidth(0.5f)
                        .height(16.dp),
                )
                Skeleton(
                    Modifier
                        .fillMaxWidth(0.75f)
                        .height(12.dp),
                )
            }
        }
    }
}
