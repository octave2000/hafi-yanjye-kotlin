package com.hafiyanjye.app.ui.talent

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hafiyanjye.app.BuildConfig
import com.hafiyanjye.app.R
import com.hafiyanjye.app.core.Tone
import com.hafiyanjye.app.data.api.userMessage
import com.hafiyanjye.app.data.realtime.RealtimeHub
import com.hafiyanjye.app.data.talent.TalentApi
import com.hafiyanjye.app.data.talent.TalentChanges
import com.hafiyanjye.app.data.talent.TalentCvUploadDto
import com.hafiyanjye.app.data.talent.TalentPatch
import com.hafiyanjye.app.data.talent.TalentProfileDto
import com.hafiyanjye.app.data.talent.TalentSection
import com.hafiyanjye.app.data.talent.talentApi
import com.hafiyanjye.app.ui.Load
import com.hafiyanjye.app.ui.Loader
import com.hafiyanjye.app.ui.containerViewModel
import com.hafiyanjye.app.ui.kit.ButtonKind
import com.hafiyanjye.app.ui.kit.ErrorPanel
import com.hafiyanjye.app.ui.kit.HIcon
import com.hafiyanjye.app.ui.kit.HafiButton
import com.hafiyanjye.app.ui.kit.InfoBanner
import com.hafiyanjye.app.ui.kit.LocalToaster
import com.hafiyanjye.app.ui.kit.PageHeader
import com.hafiyanjye.app.ui.nav.LocalMe
import com.hafiyanjye.app.ui.theme.Hafi
import com.hafiyanjye.app.ui.theme.HafiType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * An existing CV being sent and read. [result] is null while the engine
 * works; [shown] flips once the finished step list has settled, so the
 * outcome never replaces the steps abruptly.
 */
internal data class CvUpload(val result: TalentCvUploadDto? = null, val shown: Boolean = false)

/** The CV tab's actions in flight. */
internal data class TalentUiState(
    /** "Create CV" is making the empty profile. */
    val starting: Boolean = false,
    /** "Share" is turning the public link on. */
    val sharing: Boolean = false,
    val upload: CvUpload? = null,
)

/** One-off things the screen does for the ViewModel: a toast, or the system share sheet. */
internal sealed interface TalentEvent {
    data class Toast(val message: String, val tone: Tone) : TalentEvent
    data class Share(val title: String, val url: String) : TalentEvent
}

/** The CV tab: loads the profile (null until there is one) and runs its actions. */
internal class TalentViewModel(private val api: TalentApi, hub: RealtimeHub) : ViewModel() {

    /** Ready(null) means the person has no talent profile yet. */
    val profile = Loader(viewModelScope) { api.me() }
        .refreshOn(hub.resyncs)
        .refreshOn(TalentChanges.saved)

    private val _ui = MutableStateFlow(TalentUiState())
    val ui: StateFlow<TalentUiState> = _ui.asStateFlow()

    private val _events = Channel<TalentEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var landing: Job? = null

    /** An editor or the interview saved: show what the engine answered with. */
    fun saved(saved: TalentProfileDto) {
        profile.mutate { saved }
    }

    /** "Create CV": an empty profile carrying the account's name, filled in step by step. */
    fun startFromScratch(name: String) {
        if (_ui.value.starting) return
        _ui.update { it.copy(starting = true) }
        viewModelScope.launch {
            try {
                saved(api.update(TalentPatch(fullName = name.trim())))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _events.send(TalentEvent.Toast(error.userMessage(), Tone.Danger))
            } finally {
                _ui.update { it.copy(starting = false) }
            }
        }
    }

    /**
     * Sends a chosen CV. [read] opens the file; its problems are shown as
     * they are. The engine keeps the file even when the AI can't read it.
     */
    fun upload(read: suspend () -> CvFile) {
        if (_ui.value.upload != null) return
        _ui.update { it.copy(upload = CvUpload()) }
        viewModelScope.launch {
            try {
                // The step list is never cut short: it reads oddly when it flashes by.
                val wait = async { delay(MIN_SCAN_MS) }
                val file = read()
                val result = api.uploadCv(file.bytes, file.contentType)
                wait.await()
                _ui.update { it.copy(upload = CvUpload(result)) }
                delay(SETTLE_MS)
                _ui.update { it.copy(upload = CvUpload(result, shown = true)) }
                landLater(result)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: CvFileException) {
                failUpload(error.message ?: "Couldn't upload. Try again.")
            } catch (error: Exception) {
                failUpload(error.userMessage())
            }
        }
    }

    /** Like the web, the outcome moves on by itself after a moment; Continue is there to go sooner. */
    private fun landLater(result: TalentCvUploadDto) {
        val readable = result.parsed && result.filled.isNotEmpty()
        val wait = if (readable) 2400L + result.filled.size * 40L else 3200L
        landing = viewModelScope.launch {
            delay(wait)
            land()
        }
    }

    private suspend fun failUpload(message: String) {
        _ui.update { it.copy(upload = null) }
        _events.send(TalentEvent.Toast(message, Tone.Danger))
    }

    /** Leave the upload screen for the profile the engine answered with. */
    fun land() {
        val result = _ui.value.upload?.result ?: return
        landing?.cancel()
        saved(result.profile)
        _ui.update { it.copy(upload = null) }
    }

    /**
     * "Share": turns the public link on when it is off (as the web does),
     * then hands the link to the system share sheet.
     */
    fun share(current: TalentProfileDto) {
        if (_ui.value.sharing) return
        val base = BuildConfig.WEB_BASE_URL.trimEnd('/')
        if (base.isEmpty()) {
            _events.trySend(TalentEvent.Toast("Sharing isn't set up in this version of the app.", Tone.Danger))
            return
        }
        _ui.update { it.copy(sharing = true) }
        viewModelScope.launch {
            try {
                if (!current.isPublic) saved(api.update(TalentPatch(isPublic = true)))
                val title = current.fullName.trim().ifEmpty { "My CV" }
                _events.send(TalentEvent.Share(title, "$base/cv/${current.id}"))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _events.send(TalentEvent.Toast("Couldn't share. Try again.", Tone.Danger))
            } finally {
                _ui.update { it.copy(sharing = false) }
            }
        }
    }

    private companion object {
        /** Web MIN_SCAN_MS: three 550 ms steps and a little more. */
        const val MIN_SCAN_MS = 1850L

        /** Web SETTLE_MS: a pause on the finished step list. */
        const val SETTLE_MS = 450L
    }
}

/**
 * The job seeker's CV tab: first-time setup until a talent profile exists,
 * then its dashboard, with the section editors and the AI interview on top.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TalentScreen(onStudio: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel = containerViewModel { TalentViewModel(it.talentApi(), it.hub) }
    val state by viewModel.profile.state.collectAsStateWithLifecycle()
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val me = LocalMe.current
    val toaster = LocalToaster.current
    val context = LocalContext.current

    // Which editor is open survives rotation; the counter gives each opening a fresh draft.
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    var editorOpening by rememberSaveable { mutableIntStateOf(0) }
    var interview by rememberSaveable { mutableStateOf(false) }
    val openEditor = { section: TalentSection ->
        editorOpening++
        editing = section.key
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is TalentEvent.Toast -> toaster.show(event.message, event.tone)
                is TalentEvent.Share -> {
                    val send = Intent(Intent.ACTION_SEND)
                        .setType("text/plain")
                        .putExtra(Intent.EXTRA_SUBJECT, event.title)
                        .putExtra(Intent.EXTRA_TEXT, event.url)
                    context.startActivity(Intent.createChooser(send, "Share your CV"))
                }
            }
        }
    }

    val appContext = context.applicationContext
    val pickCv = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) viewModel.upload { readCvFile(appContext, uri) }
    }
    val chooseCv = { pickCv.launch(CvMimeTypes) }

    PullToRefreshBox(
        isRefreshing = (state as? Load.Ready)?.refreshing == true,
        onRefresh = viewModel.profile::refresh,
        modifier = modifier.fillMaxSize(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            when (val load = state) {
                Load.Loading -> DashboardSkeleton()
                is Load.Failed -> {
                    PageHeader(eyebrow = "Work profile", title = "Your CV")
                    ErrorPanel("Couldn't load your profile. ${load.message}", viewModel.profile::refresh)
                }
                is Load.Ready -> {
                    load.staleError?.let { message ->
                        InfoBanner("Couldn't refresh. $message", tone = Tone.Warn)
                    }
                    val profile = load.value
                    if (profile == null) {
                        Onboarding(
                            starting = ui.starting,
                            onChooseCv = chooseCv,
                            onInterview = { interview = true },
                            onStart = { viewModel.startFromScratch(me.name.orEmpty()) },
                        )
                    } else {
                        TalentDashboard(
                            profile = profile,
                            sharing = ui.sharing,
                            actions = DashboardActions(
                                onEdit = openEditor,
                                onInterview = { interview = true },
                                onStudio = onStudio,
                                onShare = { viewModel.share(profile) },
                                onChooseCv = chooseCv,
                            ),
                        )
                    }
                }
            }
        }
    }

    val loaded = (state as? Load.Ready)?.value
    val section = TalentSection.fromKey(editing)
    if (section != null && loaded != null) {
        FullScreenDialog(onDismiss = { editing = null }) {
            TalentEditorScreen(
                section = section,
                profile = loaded,
                opening = editorOpening,
                onSaved = { saved ->
                    viewModel.saved(saved)
                    editing = null
                },
                onClose = { editing = null },
            )
        }
    }
    if (interview && state is Load.Ready) {
        FullScreenDialog(onDismiss = { interview = false }) {
            TalentInterviewScreen(
                profile = loaded,
                me = me,
                onSaved = { saved ->
                    viewModel.saved(saved)
                    interview = false
                },
                onClose = { interview = false },
            )
        }
    }
    ui.upload?.let { upload -> CvUploadFlow(upload, onLand = viewModel::land) }
}

/** A dialog over the whole screen, for the editors and the interview. */
@Composable
internal fun FullScreenDialog(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        content = content,
    )
}

// ── First-time setup ────────────────────────────────────────────────────────

@Composable
private fun Onboarding(
    starting: Boolean,
    onChooseCv: () -> Unit,
    onInterview: () -> Unit,
    onStart: () -> Unit,
) {
    PageHeader(
        eyebrow = "Work profile",
        title = "Your next job starts here.",
        description = "Upload your CV, or answer a few questions and AI writes it for you.",
    )
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ChoiceCard(R.drawable.ic_file_up, "Upload your CV", "PDF or photo · up to 5 MB") {
            HafiButton(
                text = "Choose file",
                onClick = onChooseCv,
                icon = R.drawable.ic_file_up,
                enabled = !starting,
            )
        }
        ChoiceCard(R.drawable.ic_sparkles, "No CV yet?", "Answer 8 quick questions.") {
            HafiButton(
                text = "Build with AI",
                onClick = onInterview,
                kind = ButtonKind.Secondary,
                icon = R.drawable.ic_sparkles,
                enabled = !starting,
            )
        }
        ChoiceCard(R.drawable.ic_pen_line, "Create CV", "Fill in your details step by step.") {
            HafiButton(
                text = "Start",
                onClick = onStart,
                kind = ButtonKind.Secondary,
                icon = R.drawable.ic_arrow_right,
                loading = starting,
            )
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf(
            R.drawable.ic_timer to "Ready in seconds",
            R.drawable.ic_pen_line to "Edit anything",
            R.drawable.ic_link_2 to "Share with one link",
        ).forEach { (icon, label) ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HIcon(icon, size = 16.dp, tint = Hafi.FgMuted)
                Text(label, style = HafiType.Caption, color = Hafi.FgMuted)
            }
        }
    }
}

@Composable
private fun ChoiceCard(
    @DrawableRes icon: Int,
    title: String,
    line: String,
    action: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Hafi.Surface)
            .border(1.dp, Hafi.Line)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconMark(icon)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = HafiType.Heading, modifier = Modifier.semantics { heading() })
                Text(line, style = HafiType.Caption, color = Hafi.FgMuted)
            }
        }
        action()
    }
}
