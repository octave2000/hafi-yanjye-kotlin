package com.hafiyanjye.app.ui.cvstudio

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hafiyanjye.app.BuildConfig
import com.hafiyanjye.app.R
import com.hafiyanjye.app.core.Tone
import com.hafiyanjye.app.data.cvstudio.CvStudioApi
import com.hafiyanjye.app.data.cvstudio.CvStyles
import com.hafiyanjye.app.data.cvstudio.PublicCvRules
import com.hafiyanjye.app.data.talent.TalentEducation
import com.hafiyanjye.app.data.talent.TalentExperience
import com.hafiyanjye.app.data.talent.TalentLanguage
import com.hafiyanjye.app.data.talent.TalentLink
import com.hafiyanjye.app.data.talent.TalentPublicDto
import com.hafiyanjye.app.data.talent.TalentRules
import com.hafiyanjye.app.ui.Load
import com.hafiyanjye.app.ui.containerViewModel
import com.hafiyanjye.app.ui.kit.Avatar
import com.hafiyanjye.app.ui.kit.Badge
import com.hafiyanjye.app.ui.kit.ButtonKind
import com.hafiyanjye.app.ui.kit.DetailScaffold
import com.hafiyanjye.app.ui.kit.EmptyState
import com.hafiyanjye.app.ui.kit.ErrorPanel
import com.hafiyanjye.app.ui.kit.HIcon
import com.hafiyanjye.app.ui.kit.HafiButton
import com.hafiyanjye.app.ui.kit.HafiIconButton
import com.hafiyanjye.app.ui.kit.InfoBanner
import com.hafiyanjye.app.ui.kit.LocalToaster
import com.hafiyanjye.app.ui.kit.Skeleton
import com.hafiyanjye.app.ui.kit.Tag
import com.hafiyanjye.app.ui.theme.Hafi
import com.hafiyanjye.app.ui.theme.HafiType
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val UpdatedFormat = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.UK)

/** Anyone's public CV (web /cv/:id): hero, sections, footer and a contact bar. [id] is the talent profile id. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PublicCvScreen(id: Int, onBack: () -> Unit) {
    val viewModel = containerViewModel(key = "public-cv-$id") { PublicCvViewModel(CvStudioApi(it.client), id, it.hub) }
    val load by viewModel.profile.state.collectAsStateWithLifecycle()
    val profile = (load as? Load.Ready)?.value
    var viewingCv by rememberSaveable { mutableStateOf(false) }
    val hasContact = profile != null &&
        (PublicCvRules.telUri(profile.phone) != null || PublicCvRules.mailUri(profile.email) != null)
    DetailScaffold(
        title = profile?.fullName?.trim()?.ifEmpty { null } ?: "CV",
        onBack = onBack,
        bottomBar = if (profile != null && hasContact) {
            { ContactBar(profile) }
        } else {
            null
        },
    ) { padding ->
        Box(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .background(Hafi.Surface),
        ) {
            when (val state = load) {
                Load.Loading -> PublicSkeleton()
                is Load.Failed -> Column(Modifier.padding(16.dp)) {
                    ErrorPanel(state.message, viewModel.profile::refresh)
                }
                is Load.Ready -> {
                    val value = state.value
                    if (value == null) {
                        NotFound(onBack)
                    } else {
                        PullToRefreshBox(
                            isRefreshing = state.refreshing,
                            onRefresh = viewModel.profile::refresh,
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            PublicCvBody(value, state.staleError, onViewCv = { viewingCv = true })
                        }
                    }
                }
            }
        }
    }
    if (viewingCv && profile != null) {
        CvViewer(profile, onClose = { viewingCv = false })
    }
}

@Composable
private fun PublicCvBody(profile: TalentPublicDto, staleError: String?, onViewCv: () -> Unit) {
    val summary = profile.summary.trim()
    val skills = profile.skills.filter { it.isNotBlank() }
    val jobs = profile.experience.filter { it.role.isNotBlank() || it.company.isNotBlank() }
    val schools = profile.education.filter { it.school.isNotBlank() || it.degree.isNotBlank() }
    val languages = profile.languages.filter { it.name.isNotBlank() }
    val links = profile.links.filter { it.url.isNotBlank() }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(40.dp),
    ) {
        if (staleError != null) InfoBanner("Couldn't refresh: $staleError", tone = Tone.Warn)
        ProfileHero(profile, onViewCv)
        if (summary.isNotEmpty()) {
            Section("About") { Text(summary, style = HafiType.Body.copy(lineHeight = HafiType.Body.fontSize * 1.75)) }
        }
        if (jobs.isNotEmpty()) Section("Experience") { ExperienceTimeline(jobs) }
        if (skills.isNotEmpty()) Section("Skills") { SkillChips(skills) }
        if (languages.isNotEmpty()) Section("Languages") { LanguageList(languages) }
        if (schools.isNotEmpty()) Section("Education") { EducationList(schools) }
        if (links.isNotEmpty()) Section("Links") { LinkList(links) }
        Footer(profile)
    }
}

/** Eyebrow header over a hairline, then the content (web profile-sections Section). */
@Composable
private fun Section(title: String, body: @Composable () -> Unit) {
    Column {
        Text(
            text = title.uppercase(),
            style = HafiType.Micro,
            color = Hafi.FgMuted,
            modifier = Modifier
                .fillMaxWidth()
                .rule(Hafi.Line)
                .padding(bottom = 8.dp)
                .semantics { heading() },
        )
        Box(Modifier.height(20.dp))
        body()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProfileHero(profile: TalentPublicDto, onViewCv: () -> Unit) {
    val name = profile.fullName.trim().ifEmpty { "Job seeker" }
    val years = PublicCvRules.yearsOfExperience(profile.experience)
    val languageCount = profile.languages.count { it.name.isNotBlank() }
    val meta = buildList {
        profile.location.trim().takeIf { it.isNotEmpty() }?.let { add(R.drawable.ic_map_pin to it) }
        if (years > 0) {
            add(R.drawable.ic_briefcase_business to "$years ${if (years == 1) "year" else "years"} experience")
        }
        if (languageCount > 0) {
            add(R.drawable.ic_languages to "$languageCount ${if (languageCount == 1) "language" else "languages"}")
        }
    }
    Column(
        Modifier
            .fillMaxWidth()
            .drawBehind {
                drawLine(Hafi.Accent, Offset(0f, 1.5.dp.toPx()), Offset(size.width, 1.5.dp.toPx()), 3.dp.toPx())
            }
            .padding(top = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Avatar(name, profile.photo?.takeIf { profile.style.showPhoto }, size = 96.dp)
        if (profile.openToWork) Badge("Open to work", Tone.Accent, live = true)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(name, style = HafiType.Display, modifier = Modifier.semantics { heading() })
            profile.headline.trim().takeIf { it.isNotEmpty() }?.let {
                Text(it, style = HafiType.Body.copy(fontSize = HafiType.Subheading.fontSize), color = Hafi.FgMuted)
            }
        }
        if (meta.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                meta.forEach { (icon, label) ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        HIcon(icon, size = 16.dp, tint = Hafi.FgMuted)
                        Text(label, style = HafiType.Caption, color = Hafi.FgMuted)
                    }
                }
            }
        }
        HeroActions(profile, onViewCv)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HeroActions(profile: TalentPublicDto, onViewCv: () -> Unit) {
    val context = LocalContext.current
    val toaster = LocalToaster.current
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HafiButton(
            text = "Share",
            onClick = {
                val url = CvStyles.publicUrl(BuildConfig.WEB_BASE_URL, profile.id)
                if (url == null) {
                    toaster.show("Sharing isn't set up in this version of the app.", Tone.Danger)
                } else {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, url)
                        putExtra(Intent.EXTRA_SUBJECT, "${profile.fullName.trim().ifEmpty { "CV" }} — Hafi Yanjye")
                    }
                    context.startActivity(Intent.createChooser(send, "Share this CV"))
                }
            },
            kind = ButtonKind.Outline,
            icon = R.drawable.ic_share_2,
        )
        HafiButton("View CV", onViewCv, kind = ButtonKind.Outline, icon = R.drawable.ic_file_text)
    }
}

@Composable
private fun ExperienceTimeline(jobs: List<TalentExperience>) {
    Column {
        jobs.forEachIndexed { index, job ->
            val last = index == jobs.lastIndex
            val range = TalentRules.formatCvRange(job.start, job.end)
            val sub = listOf(if (job.role.isNotBlank()) job.company else "", range).filter { it.isNotBlank() }
            val current = job.end.isNullOrEmpty()
            Row(
                Modifier.drawBehind {
                    if (!last) {
                        val x = 3.5.dp.toPx()
                        drawLine(Hafi.Line, Offset(x, 12.dp.toPx()), Offset(x, size.height), 1.dp.toPx())
                    }
                },
            ) {
                Box(
                    Modifier
                        .padding(top = 7.dp)
                        .size(8.dp)
                        .background(if (current) Hafi.Accent else Hafi.Surface)
                        .border(1.dp, Hafi.Accent),
                )
                Column(
                    Modifier
                        .padding(start = 20.dp, bottom = if (last) 0.dp else 32.dp)
                        .weight(1f),
                ) {
                    Text(job.role.ifBlank { job.company }, style = HafiType.Subheading)
                    if (sub.isNotEmpty()) {
                        Text(
                            text = sub.joinToString("  ·  "),
                            style = HafiType.Caption,
                            color = Hafi.FgMuted,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    val lines = detailLines(job.details).take(4)
                    if (lines.isNotEmpty()) {
                        Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            lines.forEach { line ->
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Box(
                                        Modifier
                                            .padding(top = 11.dp)
                                            .size(width = 10.dp, height = 1.dp)
                                            .background(Hafi.FgMuted),
                                    )
                                    Text(line, style = HafiType.Body, modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SkillChips(skills: List<String>) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        skills.forEach { Tag(it) }
    }
}

@Composable
private fun LanguageList(languages: List<TalentLanguage>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        languages.forEach { language ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(language.name, style = HafiType.Body, modifier = Modifier.weight(1f))
                Text(language.level.label, style = HafiType.Caption, color = Hafi.FgMuted)
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    repeat(4) { bar ->
                        Box(
                            Modifier
                                .size(width = 14.dp, height = 4.dp)
                                .background(if (bar <= language.level.ordinal) Hafi.Accent else Hafi.Line),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EducationList(schools: List<TalentEducation>) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        schools.forEach { school ->
            val range = TalentRules.formatCvRange(school.start, school.end)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(school.degree.ifBlank { school.school }, style = HafiType.BodyStrong)
                if (school.degree.isNotBlank() && school.school.isNotBlank()) {
                    Text(school.school, style = HafiType.Caption, color = Hafi.FgMuted)
                }
                if (range.isNotEmpty()) Text(range, style = HafiType.Caption, color = Hafi.FgMuted)
            }
        }
    }
}

@Composable
private fun LinkList(links: List<TalentLink>) {
    val uri = LocalUriHandler.current
    val toaster = LocalToaster.current
    Column(Modifier.rule(Hafi.Line, top = true)) {
        links.forEach { link ->
            val label = link.label.trim()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clickable(role = Role.Button, onClickLabel = "Open link") {
                        runCatching { uri.openUri(PublicCvRules.absoluteUrl(link.url)) }
                            .onFailure { toaster.show("Couldn't open that link.", Tone.Danger) }
                    }
                    .rule(Hafi.Line),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                    Text(
                        text = label.ifEmpty { PublicCvRules.shortUrl(link.url) },
                        style = HafiType.Body,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (label.isNotEmpty()) {
                        Text(
                            text = PublicCvRules.shortUrl(link.url),
                            style = HafiType.Caption,
                            color = Hafi.FgMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                HIcon(R.drawable.ic_arrow_up_right, size = 16.dp, tint = Hafi.FgMuted)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Footer(profile: TalentPublicDto) {
    val updated = remember(profile.updatedAt) { UpdatedFormat.format(profile.updatedAt.atZone(ZoneId.systemDefault())) }
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .rule(Hafi.Line, top = true)
            .padding(top = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text("Profile on Hafi Yanjye", style = HafiType.Caption, color = Hafi.FgMuted)
        Text("Updated $updated", style = HafiType.Caption, color = Hafi.FgMuted)
    }
}

/** The main contact, then the other as an icon button, pinned to the bottom (web ContactBar). */
@Composable
private fun ContactBar(profile: TalentPublicDto) {
    val uri = LocalUriHandler.current
    val toaster = LocalToaster.current
    val tel = PublicCvRules.telUri(profile.phone)
    val mail = PublicCvRules.mailUri(profile.email)
    fun open(target: String, failure: String) {
        runCatching { uri.openUri(target) }.onFailure { toaster.show(failure, Tone.Danger) }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (tel != null) {
            HafiButton(
                text = "Call",
                onClick = { open(tel, "Couldn't open the phone app.") },
                modifier = Modifier.weight(1f),
                icon = R.drawable.ic_phone,
            )
            if (mail != null) {
                Box(Modifier.border(1.dp, Hafi.LineStrong)) {
                    HafiIconButton(R.drawable.ic_mail, "Email", { open(mail, "Couldn't open an email app.") })
                }
            }
        } else if (mail != null) {
            HafiButton(
                text = "Email",
                onClick = { open(mail, "Couldn't open an email app.") },
                modifier = Modifier.weight(1f),
                icon = R.drawable.ic_mail,
            )
        }
    }
}

/** The person's CV as A4 pages in their own template, full screen. */
@Composable
private fun CvViewer(profile: TalentPublicDto, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        DetailScaffold(title = "CV", onBack = onClose) { padding ->
            CvStage(
                fields = profile,
                style = profile.style,
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
            )
        }
    }
}

@Composable
private fun NotFound(onBack: () -> Unit) {
    Column(Modifier.padding(16.dp)) {
        EmptyState(
            title = "Profile not found",
            description = "This profile is private, or the link is wrong.",
            icon = R.drawable.ic_user_round_x,
            action = { HafiButton("Go back", onBack, kind = ButtonKind.Outline, icon = R.drawable.ic_arrow_left) },
        )
    }
}

@Composable
private fun PublicSkeleton() {
    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(
            Modifier
                .size(96.dp)
                .border(1.dp, Hafi.Line),
        )
        Skeleton(
            Modifier
                .fillMaxWidth(0.7f)
                .height(34.dp),
        )
        Skeleton(
            Modifier
                .fillMaxWidth(0.85f)
                .height(16.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(2) {
                Box(
                    Modifier
                        .width(112.dp)
                        .height(48.dp)
                        .border(1.dp, Hafi.Line),
                )
            }
        }
        Box(Modifier.height(24.dp))
        repeat(4) {
            Skeleton(
                Modifier
                    .fillMaxWidth(if (it % 2 == 0) 1f else 0.8f)
                    .height(12.dp),
            )
        }
    }
}
