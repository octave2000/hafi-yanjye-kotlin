package com.hafiyanjye.app.ui.cvstudio

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.annotation.DrawableRes
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role as SemanticsRole
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hafiyanjye.app.R
import com.hafiyanjye.app.core.Tone
import com.hafiyanjye.app.data.api.Role
import com.hafiyanjye.app.data.cvstudio.CvStudioApi
import com.hafiyanjye.app.data.cvstudio.CvStyles
import com.hafiyanjye.app.data.talent.CvStyle
import com.hafiyanjye.app.data.talent.TalentProfileDto
import com.hafiyanjye.app.data.talent.TalentRules
import com.hafiyanjye.app.data.talent.talentApi
import com.hafiyanjye.app.ui.Load
import com.hafiyanjye.app.ui.containerViewModel
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
import com.hafiyanjye.app.ui.nav.LocalMe
import com.hafiyanjye.app.ui.theme.Hafi
import com.hafiyanjye.app.ui.theme.HafiType
import com.hafiyanjye.app.ui.theme.rememberReducedMotion

/** The CV studio (web /work/cv): live A4 preview, style dock with autosave, PDF and link exports. */
@Composable
internal fun CvStudioScreen(onBack: () -> Unit, onEditContent: () -> Unit) {
    val viewModel = containerViewModel {
        CvStudioViewModel(it.talentApi(), CvStudioApi(it.client), it.app, it.hub)
    }
    val load by viewModel.profile.state.collectAsStateWithLifecycle()
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    StudioEffects(viewModel)
    val profile = (load as? Load.Ready)?.value
    val style = ui.style
    val ready = profile != null && style != null
    DetailScaffold(
        title = "CV studio",
        onBack = onBack,
        actions = {
            if (ready) {
                HafiIconButton(
                    icon = R.drawable.ic_link_2,
                    label = "Copy share link",
                    onClick = viewModel::copyLink,
                    enabled = ui.busy == null,
                )
                HafiIconButton(
                    icon = R.drawable.ic_share_2,
                    label = "Share PDF",
                    onClick = viewModel::sharePdf,
                    enabled = ui.busy == null,
                )
            }
        },
        bottomBar = if (profile != null && style != null) {
            { StudioDock(viewModel, profile, style, ui) }
        } else {
            null
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            when (val state = load) {
                Load.Loading -> StudioSkeleton()
                is Load.Failed -> StudioFailed(state.message, viewModel.profile::refresh)
                is Load.Ready -> if (profile == null || style == null) {
                    NoProfileYet(onEditContent)
                } else {
                    StatusStrip(ui.save, onRetry = viewModel::retrySave, onEditContent = onEditContent)
                    state.staleError?.let {
                        InfoBanner("Couldn't refresh: $it", Modifier.padding(16.dp), tone = Tone.Warn)
                    }
                    CvStage(profile, style, Modifier.weight(1f))
                }
            }
        }
    }
}

/** Toasts, the share sheet, the clipboard and the Android 8–9 save picker. */
@Composable
private fun StudioEffects(viewModel: CvStudioViewModel) {
    val context = LocalContext.current
    val toaster = LocalToaster.current
    val savePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf"),
        viewModel::savePickedPdf,
    )
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is StudioEvent.Toast -> toaster.show(event.message, event.tone)
                is StudioEvent.Launch -> try {
                    context.startActivity(event.intent)
                } catch (_: ActivityNotFoundException) {
                    toaster.show("No app on this phone can take a PDF.", Tone.Danger)
                }
                is StudioEvent.CopyLink -> {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Public CV", event.url))
                    toaster.show("Link copied", Tone.Accent)
                }
                is StudioEvent.PickSaveLocation -> savePicker.launch(event.fileName)
            }
        }
    }
}

/** "Saving…" / "Saved" under the top bar, and the way back to the CV's words. */
@Composable
private fun StatusStrip(save: SaveState, onRetry: () -> Unit, onEditContent: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Hafi.Surface)
            .rule(Hafi.Line)
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .weight(1f)
                .semantics { liveRegion = LiveRegionMode.Polite },
        ) {
            when (save) {
                SaveState.Pending, SaveState.Saving -> SaveLine(R.drawable.ic_loader_circle, "Saving…", Hafi.FgMuted)
                SaveState.Failed -> Row(
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .clickable(role = SemanticsRole.Button, onClickLabel = "Try saving again", onClick = onRetry),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SaveLine(R.drawable.ic_circle_alert, "Look not saved · Try again", Hafi.Danger)
                }
                SaveState.Idle, SaveState.Saved -> SaveLine(R.drawable.ic_check, "Saved", Hafi.AccentStrong)
            }
        }
        HafiButton("Edit content", onEditContent, kind = ButtonKind.Ghost, icon = R.drawable.ic_pencil)
    }
}

@Composable
private fun SaveLine(@DrawableRes icon: Int, text: String, tint: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        HIcon(icon, size = 14.dp, tint = tint)
        Text(text, style = HafiType.Caption, color = if (tint == Hafi.Danger) Hafi.Danger else Hafi.FgMuted)
    }
}

/** The bottom dock: the open style panel, the three panel tabs, and the two main actions. */
@Composable
private fun StudioDock(viewModel: CvStudioViewModel, profile: TalentProfileDto, style: CvStyle, ui: StudioUi) {
    var open by rememberSaveable { mutableStateOf<StyleTab?>(null) }
    val reduced = rememberReducedMotion()
    val worker = LocalMe.current.role == Role.Worker
    Column(Modifier.fillMaxWidth()) {
        AnimatedVisibility(
            visible = open != null,
            enter = if (reduced) fadeIn(tween(150)) else fadeIn(tween(200)) + slideInVertically(tween(200)) { it / 4 },
            exit = if (reduced) {
                fadeOut(tween(120))
            } else {
                fadeOut(tween(150)) + slideOutVertically(tween(150)) { it / 4 }
            },
        ) {
            open?.let { tab ->
                StylePanel(tab, viewModel, profile, style, onClose = { open = null })
            }
        }
        DockTabs(open, onToggle = { tab -> open = if (open == tab) null else tab })
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            HafiButton(
                text = "Download PDF",
                onClick = viewModel::downloadPdf,
                modifier = Modifier.weight(1f),
                icon = R.drawable.ic_download,
                loading = ui.busy == StudioBusy.Download,
                enabled = ui.busy == null || ui.busy == StudioBusy.Download,
            )
            if (worker && TalentRules.hasStudioCv(profile)) {
                HafiButton(
                    text = "Add to work profile",
                    onClick = viewModel::attachToWorkProfile,
                    modifier = Modifier.weight(1f),
                    kind = ButtonKind.Outline,
                    icon = R.drawable.ic_file_check_2,
                    loading = ui.busy == StudioBusy.Attach,
                    loadingText = "Adding",
                    enabled = ui.busy == null || ui.busy == StudioBusy.Attach,
                )
            }
        }
    }
}

@Composable
private fun DockTabs(open: StyleTab?, onToggle: (StyleTab) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp)
            .border(1.dp, Hafi.LineStrong),
    ) {
        StyleTab.entries.forEach { tab ->
            val active = open == tab
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .background(if (active) Hafi.AccentSoft else Hafi.Surface)
                    .then(if (active) Modifier.border(2.dp, Hafi.Accent) else Modifier)
                    .semantics { stateDescription = if (active) "Open" else "Closed" }
                    .clickable(role = SemanticsRole.Tab, onClick = { onToggle(tab) }),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = tab.label,
                    style = if (active) HafiType.Label else HafiType.Caption,
                    color = if (active) Hafi.AccentStrong else Hafi.FgMuted,
                )
            }
        }
    }
}

@Composable
private fun StylePanel(
    tab: StyleTab,
    viewModel: CvStudioViewModel,
    profile: TalentProfileDto,
    style: CvStyle,
    onClose: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(max = 360.dp)
            .rule(Hafi.Line)
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 4.dp, bottom = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = tab.label.uppercase(),
                style = HafiType.Micro,
                color = Hafi.FgMuted,
                modifier = Modifier.semantics { heading() },
            )
            if (tab == StyleTab.Colour) {
                Text(
                    text = style.accent.uppercase(),
                    style = HafiType.Caption,
                    color = Hafi.FgMuted,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            Box(Modifier.weight(1f))
            HafiIconButton(R.drawable.ic_x, "Close ${tab.label.lowercase()}", onClose)
        }
        Column(Modifier.padding(end = 12.dp)) {
            when (tab) {
                StyleTab.Template -> {
                    TemplatePicker(profile, style) { picked -> viewModel.change { it.copy(template = picked) } }
                    Box(Modifier.height(8.dp))
                    TemplateOptions(
                        style = style,
                        onPhoto = { show -> viewModel.change { it.copy(showPhoto = show) } },
                        onSurprise = { viewModel.change { CvStyles.surprise(it) } },
                    )
                }
                StyleTab.Colour -> AccentPicker(style) { hex -> viewModel.change { it.copy(accent = hex) } }
                StyleTab.Typeface -> FontPicker(style) { font -> viewModel.change { it.copy(font = font) } }
            }
        }
    }
}

@Composable
private fun StudioSkeleton() {
    Column(
        Modifier
            .fillMaxSize()
            .background(Hafi.Surface)
            .padding(16.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Skeleton(
            Modifier
                .fillMaxWidth(0.3f)
                .height(16.dp),
        )
        Column(
            Modifier
                .fillMaxWidth()
                .border(1.dp, Hafi.Line)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Skeleton(
                Modifier
                    .fillMaxWidth()
                    .height(72.dp),
            )
            repeat(6) {
                Skeleton(
                    Modifier
                        .fillMaxWidth(if (it % 2 == 0) 0.9f else 0.7f)
                        .height(12.dp),
                )
            }
        }
    }
}

@Composable
private fun StudioFailed(message: String, onRetry: () -> Unit) {
    Column(Modifier.padding(16.dp)) {
        Text(
            text = "Couldn't open your CV",
            style = HafiType.Heading,
            modifier = Modifier
                .padding(bottom = 12.dp)
                .semantics { heading() },
        )
        ErrorPanel(message, onRetry)
    }
}

@Composable
private fun NoProfileYet(onStart: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        EmptyState(
            title = "Start your profile first",
            description = "Add your details once, then style your CV here.",
            icon = R.drawable.ic_file_user,
            action = { HafiButton("Go to Work", onStart, icon = R.drawable.ic_arrow_right) },
        )
    }
}
