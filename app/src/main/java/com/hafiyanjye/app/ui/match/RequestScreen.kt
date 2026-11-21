package com.hafiyanjye.app.ui.match

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.hafiyanjye.app.R
import com.hafiyanjye.app.core.Format
import com.hafiyanjye.app.core.Tone
import com.hafiyanjye.app.data.api.SessionState
import com.hafiyanjye.app.data.match.Job
import com.hafiyanjye.app.data.match.JobWithSession
import com.hafiyanjye.app.data.match.MatchApi
import com.hafiyanjye.app.ui.Load
import com.hafiyanjye.app.ui.containerViewModel
import com.hafiyanjye.app.ui.kit.ButtonKind
import com.hafiyanjye.app.ui.kit.ButtonSize
import com.hafiyanjye.app.ui.kit.DetailScaffold
import com.hafiyanjye.app.ui.kit.HIcon
import com.hafiyanjye.app.ui.kit.HafiButton
import com.hafiyanjye.app.ui.kit.HafiChip
import com.hafiyanjye.app.ui.kit.HafiDialog
import com.hafiyanjye.app.ui.kit.HafiField
import com.hafiyanjye.app.ui.kit.HafiSheet
import com.hafiyanjye.app.ui.kit.InfoBanner
import com.hafiyanjye.app.ui.kit.LocalToaster
import com.hafiyanjye.app.ui.kit.PulseDot
import com.hafiyanjye.app.ui.kit.Skeleton
import com.hafiyanjye.app.ui.kit.categoryIcon
import com.hafiyanjye.app.ui.match.RequestViewModel.Actions
import com.hafiyanjye.app.ui.nav.Routes
import com.hafiyanjye.app.ui.theme.Hafi
import com.hafiyanjye.app.ui.theme.HafiType
import com.hafiyanjye.app.ui.theme.rememberReducedMotion
import kotlinx.coroutines.delay
import java.time.Instant

/** Where the views of one request go: other screens through [Routes] only. */
internal class RequestNav(private val nav: NavHostController) {
    fun back() = nav.popBackStack()
    fun askForHelp() = nav.navigate(Routes.Find)
    fun person(workerId: Int) = nav.navigate(Routes.person(workerId))
    fun chat(jobId: Int) = nav.navigate(Routes.chatWith(jobId = jobId))
    fun trip(sessionId: Int) = nav.navigate(Routes.trip(sessionId))
}

/** One request of the job provider and its live matching, from pinging people nearby to the review. */
@Composable
fun RequestScreen(jobId: Int, nav: NavHostController) {
    val viewModel = containerViewModel(key = "request-$jobId") { RequestViewModel(MatchApi(it.client), it.hub, jobId) }
    val state by viewModel.request.state.collectAsStateWithLifecycle()
    val notFound by viewModel.notFound.collectAsStateWithLifecycle()
    val toaster = LocalToaster.current
    val links = RequestNav(nav)

    LaunchedEffect(viewModel) { viewModel.notices.collect { toaster.show(it.text, it.tone) } }
    LifecycleResumeEffect(viewModel) {
        viewModel.setVisible(true)
        onPauseOrDispose { viewModel.setVisible(false) }
    }

    val ready = state as? Load.Ready
    DetailScaffold(
        title = ready?.value?.session?.state?.let(::stateLabel) ?: "Request",
        onBack = { links.back() },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (val current = state) {
                Load.Loading -> LoadingRequest()
                is Load.Failed -> RequestError(
                    notFound = notFound,
                    message = current.message,
                    onRetry = viewModel::refresh,
                    onAskForHelp = links::askForHelp,
                )
                is Load.Ready -> ReadyRequest(current, viewModel, links)
            }
        }
    }
}

@Composable
private fun ReadyRequest(current: Load.Ready<JobWithSession>, viewModel: RequestViewModel, links: RequestNav) {
    val data = current.value
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    StatusLine(data.session.state)
    current.staleError?.let { message ->
        InfoBanner(
            "Couldn't refresh. $message",
            tone = Tone.Warn,
            icon = R.drawable.ic_wifi_off,
            action = { HafiButton("Try again", viewModel::refresh, kind = ButtonKind.Outline) },
        )
    }
    JobHeader(
        job = data.job,
        state = data.session.state,
        onEditBudget = { viewModel.open(Overlay.Budget) },
        onCancel = { viewModel.open(Overlay.ConfirmCancel) },
    )
    SessionBody(data, viewModel, busy, links)
    RequestOverlays(data, viewModel, busy, links)
}

/** The state in words, with a live dot while the engine is working; read out when it changes. */
@Composable
private fun StatusLine(state: SessionState) {
    Row(
        modifier = Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (isLive(state)) PulseDot(Hafi.Accent)
        Text(stateLabel(state), style = HafiType.Caption, color = Hafi.FgMuted)
    }
}

/** The body for the session's state. A change of state fades; the content inside stays live. */
@Composable
private fun SessionBody(data: JobWithSession, viewModel: RequestViewModel, busy: String?, links: RequestNav) {
    val reduced = rememberReducedMotion()
    AnimatedContent(
        targetState = sessionView(data.session),
        transitionSpec = {
            fadeIn(tween(if (reduced) 0 else 220)) togetherWith fadeOut(tween(if (reduced) 0 else 120))
        },
        label = "session state",
    ) { view ->
        when (view) {
            SessionView.Pinging -> PingingView(data, viewModel, busy)
            SessionView.DirectWaiting -> DirectWaitingView(data)
            SessionView.Shortlisting -> ShortlistingView(data.session)
            SessionView.Eliminating -> EliminationView(data, viewModel, busy)
            SessionView.Matched -> MatchedView(data, viewModel, busy, links)
            SessionView.Completed -> CompletedView(data.session, viewModel, busy, links)
            SessionView.NobodyFree -> NobodyFreeView(data, viewModel, busy)
            SessionView.DirectEnded -> DirectEndedView(data, viewModel, busy)
            SessionView.Cancelled -> CancelledView(onAskAgain = links::askForHelp)
        }
    }
}

/** The budget sheet, the cancel and hire confirmations, the reject sheet and the person sheet. */
@Composable
private fun RequestOverlays(data: JobWithSession, viewModel: RequestViewModel, busy: String?, links: RequestNav) {
    val overlay by viewModel.overlay.collectAsStateWithLifecycle()
    val session = data.session
    when (val shown = overlay) {
        Overlay.Budget -> if (canEditBudget(session.state)) {
            BudgetSheet(
                budgetRwf = data.job.budgetRwf,
                saving = busy == Actions.Budget,
                onSave = viewModel::setBudget,
                onDismiss = viewModel::close,
            )
        }
        Overlay.ConfirmCancel -> if (canCancel(session.state)) {
            HafiDialog(
                title = "Cancel this request?",
                text = "We will stop pinging people near you and let anyone who said they were available " +
                    "know you no longer need them.",
                confirmLabel = "Cancel request",
                onConfirm = viewModel::cancel,
                onDismiss = viewModel::close,
                dismissLabel = "Keep looking",
                destructive = true,
                busy = busy == Actions.Cancel,
            )
        }
        Overlay.Hire -> session.pick?.takeIf { !session.hired }?.let { pick ->
            HafiDialog(
                title = "Hire ${pick.displayName}?",
                text = "We'll tell ${pick.firstName} they're hired and stop asking other people. When the work " +
                    "is finished, mark the job done and leave a review.",
                confirmLabel = "Hire ${pick.firstName}",
                onConfirm = { viewModel.hire(session.id, pick.firstName) },
                onDismiss = viewModel::close,
                dismissLabel = "Not yet",
                busy = busy == Actions.Hire,
            )
        }
        Overlay.Reject -> session.pick?.takeIf { !session.hired }?.let { pick ->
            RejectSheet(
                pickName = pick.displayName,
                hasNext = hasNextInLine(session),
                busy = busy,
                onReject = { reason -> viewModel.reject(session.id, reason) },
                onDismiss = viewModel::close,
            )
        }
        is Overlay.Person -> PersonSheet(
            details = shown.details,
            data = data,
            busy = busy,
            onChoose = { person -> viewModel.choose(session.id, person) },
            onFullProfile = { workerId ->
                viewModel.close()
                links.person(workerId)
            },
            onDismiss = viewModel::close,
        )
        null -> Unit
    }
}

/** The clock for countdowns: the deadlines come from the engine, only the ticking is local. */
@Composable
internal fun rememberNow(): State<Instant> = produceState(Instant.now()) {
    while (true) {
        delay(1000)
        value = Instant.now()
    }
}

@Composable
private fun JobHeader(job: Job, state: SessionState, onEditBudget: () -> Unit, onCancel: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        HIcon(
            categoryIcon(job.category.icon),
            size = 32.dp,
            tint = Hafi.AccentStrong,
            modifier = Modifier.padding(top = 2.dp),
        )
        Column(Modifier.weight(1f)) {
            Eyebrow(job.category.nameEn)
            Text(
                job.summary,
                style = HafiType.Subheading,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .semantics { heading() },
            )
            JobFacts(job, editable = canEditBudget(state), onEditBudget = onEditBudget)
        }
        if (canCancel(state)) {
            HafiButton("Cancel", onCancel, kind = ButtonKind.Ghost, icon = R.drawable.ic_x)
        }
    }
}

/** Where the work is and the budget; the budget is a button while it can still change. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun JobFacts(job: Job, editable: Boolean, onEditBudget: () -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        job.addressLabel?.let { place ->
            Row(
                modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                HIcon(R.drawable.ic_map_pin, size = 16.dp, tint = Hafi.FgMuted)
                Text(
                    place,
                    style = HafiType.Label,
                    color = Hafi.FgMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        val budgetModifier = if (editable) {
            Modifier
                .clickable(role = Role.Button, onClick = onEditBudget)
                .semantics(mergeDescendants = true) {
                    contentDescription = job.budgetRwf?.let { "Budget ${Format.rwf(it)}, change budget" }
                        ?: "No budget, add a budget"
                }
        } else {
            Modifier
        }
        Row(
            modifier = Modifier
                .defaultMinSize(minHeight = 48.dp)
                .then(budgetModifier),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            HIcon(R.drawable.ic_wallet, size = 16.dp, tint = Hafi.FgMuted)
            Text(Format.budget(job.budgetRwf), style = HafiType.Label, color = if (editable) Hafi.Fg else Hafi.FgMuted)
            if (editable) HIcon(R.drawable.ic_pencil, size = 14.dp, tint = Hafi.FgMuted)
        }
    }
}

private val QuickBudgets = listOf(5_000, 10_000, 20_000, 50_000)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BudgetSheet(budgetRwf: Int?, saving: Boolean, onSave: (Int) -> Unit, onDismiss: () -> Unit) {
    var typed by rememberSaveable { mutableStateOf(budgetRwf?.toString().orEmpty()) }
    val amount = parseBudget(typed)
    HafiSheet(onDismiss = onDismiss, title = "Change your budget") {
        Text(
            "A realistic budget gets more people saying yes. You only agree a price once you talk.",
            style = HafiType.Body,
            color = Hafi.FgMuted,
        )
        Spacer(Modifier.height(16.dp))
        HafiField(
            label = "Budget (RWF)",
            value = typed,
            onValueChange = { typed = it.filter(Char::isDigit).take(9) },
            hint = amount?.let(Format::rwf),
            error = if (typed.isNotEmpty() && amount == null) {
                "Enter between ${Format.rwf(MinBudgetRwf)} and ${Format.rwf(MaxBudgetRwf)}"
            } else {
                null
            },
            enabled = !saving,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        Spacer(Modifier.height(12.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            QuickBudgets.forEach { pick ->
                HafiChip(
                    Format.rwf(pick),
                    selected = amount == pick,
                    onClick = { typed = pick.toString() },
                    enabled = !saving,
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        HafiButton(
            "Save budget",
            onClick = { amount?.let(onSave) },
            modifier = Modifier.fillMaxWidth(),
            size = ButtonSize.Large,
            enabled = amount != null && amount != budgetRwf,
            loading = saving,
        )
    }
}

@Composable
private fun LoadingRequest() {
    Column(
        modifier = Modifier.semantics { contentDescription = "Loading your request" },
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Skeleton(Modifier.size(40.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Skeleton(
                    Modifier
                        .width(80.dp)
                        .height(12.dp),
                )
                Skeleton(
                    Modifier
                        .fillMaxWidth(0.75f)
                        .height(20.dp),
                )
                Skeleton(
                    Modifier
                        .fillMaxWidth(0.5f)
                        .height(16.dp),
                )
            }
        }
        Skeleton(
            Modifier
                .fillMaxWidth()
                .height(220.dp),
        )
        Skeleton(
            Modifier
                .fillMaxWidth()
                .height(160.dp),
        )
    }
}

@Composable
private fun RequestError(notFound: Boolean, message: String, onRetry: () -> Unit, onAskForHelp: () -> Unit) {
    Panel(centered = true) {
        HIcon(R.drawable.ic_search_x, size = 48.dp, tint = Hafi.FgSubtle)
        Spacer(Modifier.height(16.dp))
        Text(
            if (notFound) "We couldn't find this request" else "We couldn't load this request",
            style = HafiType.Heading,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics {
                heading()
                liveRegion = LiveRegionMode.Polite
            },
        )
        Spacer(Modifier.height(4.dp))
        Text(
            if (notFound) "It may belong to another account, or the link is out of date." else message,
            style = HafiType.Body,
            color = Hafi.FgMuted,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        if (!notFound) {
            HafiButton("Try again", onRetry, modifier = Modifier.fillMaxWidth(), kind = ButtonKind.Outline)
            Spacer(Modifier.height(8.dp))
        }
        HafiButton("Ask for help with something", onAskForHelp, modifier = Modifier.fillMaxWidth())
    }
}
