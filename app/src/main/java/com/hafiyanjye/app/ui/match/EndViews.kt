package com.hafiyanjye.app.ui.match

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hafiyanjye.app.R
import com.hafiyanjye.app.data.match.DirectAnswer
import com.hafiyanjye.app.data.match.JobWithSession
import com.hafiyanjye.app.data.match.MatchSession
import com.hafiyanjye.app.ui.kit.ButtonKind
import com.hafiyanjye.app.ui.kit.ButtonSize
import com.hafiyanjye.app.ui.kit.HIcon
import com.hafiyanjye.app.ui.kit.HafiButton
import com.hafiyanjye.app.ui.kit.HafiSwitchRow
import com.hafiyanjye.app.ui.kit.HafiTextArea
import com.hafiyanjye.app.ui.kit.StarInput
import com.hafiyanjye.app.ui.match.RequestViewModel.Actions
import com.hafiyanjye.app.ui.theme.Hafi
import com.hafiyanjye.app.ui.theme.HafiType
import java.util.Locale

// ─── completed ───────────────────────────────────────────────────────────────

/** The job is done: who did it, then the review (once), then a way to ask for the next thing. */
@Composable
internal fun CompletedView(session: MatchSession, viewModel: RequestViewModel, busy: String?, links: RequestNav) {
    val pick = session.pick
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Panel {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HIcon(R.drawable.ic_check_circle_2, size = 32.dp, tint = Hafi.AccentStrong)
                Column(Modifier.weight(1f)) {
                    PanelHeading("Job done")
                    if (pick != null) Text("Done by ${pick.displayName}", style = HafiType.Body, color = Hafi.FgMuted)
                }
            }
            if (pick != null) {
                HafiButton(
                    "View ${pick.firstName}'s profile",
                    onClick = { links.person(pick.workerId) },
                    kind = ButtonKind.Ghost,
                )
            }
        }
        when {
            session.reviewed -> Panel(
                centered = true,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            ) {
                PanelHeading("Thanks, review sent")
                Text(
                    "Your rating helps neighbours find good people faster.",
                    style = HafiType.Body,
                    color = Hafi.FgMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp),
                )
                FindSomeoneElse(links, Modifier.padding(top = 16.dp))
            }
            session.canReview && pick != null -> ReviewForm(
                sessionId = session.id,
                firstName = pick.firstName,
                busy = busy,
                onSend = viewModel::review,
            )
            else -> FindSomeoneElse(links)
        }
    }
}

@Composable
private fun FindSomeoneElse(links: RequestNav, modifier: Modifier = Modifier) {
    HafiButton(
        "Find someone else",
        onClick = links::askForHelp,
        modifier = modifier.fillMaxWidth(),
        size = ButtonSize.Large,
    )
}

@Composable
private fun ReviewForm(sessionId: Int, firstName: String, busy: String?, onSend: (Int, Int, String) -> Unit) {
    var rating by rememberSaveable(sessionId) { mutableIntStateOf(0) }
    var text by rememberSaveable(sessionId) { mutableStateOf("") }
    val sending = busy == Actions.Review
    Panel {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            PanelHeading("How was $firstName?")
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StarInput(rating, { rating = it }, enabled = !sending)
                if (rating > 0) Text(StarWords[rating], style = HafiType.Label, color = Hafi.FgMuted)
            }
            HafiTextArea(
                "Tell others about it",
                text,
                { text = it },
                placeholder = "Arrived on time, fixed the leak in 30 minutes, fair price.",
                optional = true,
                maxLength = 1000,
                enabled = !sending,
            )
            HafiButton(
                if (rating == 0) "Pick a star rating" else "Send review",
                onClick = { onSend(sessionId, rating, text) },
                modifier = Modifier.fillMaxWidth(),
                size = ButtonSize.Large,
                enabled = busy == null && rating in 1..5,
                loading = sending,
            )
        }
    }
}

// ─── nobody free ─────────────────────────────────────────────────────────────

/** Nobody nearby said yes in time: be told when someone is free, or search a wider area. */
@Composable
internal fun NobodyFreeView(data: JobWithSession, viewModel: RequestViewModel, busy: String?) {
    Panel(centered = true) {
        HIcon(R.drawable.ic_radar, size = 48.dp, tint = Hafi.FgSubtle)
        PanelHeading("No one nearby is free right now", Modifier.padding(top = 16.dp))
        Text(
            "Everyone we asked is busy or didn't reply in time. This often changes within the hour.",
            style = HafiType.Body,
            color = Hafi.FgMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp),
        )
        HafiSwitchRow(
            "Notify me when someone is free",
            checked = data.job.notifyMe,
            onCheckedChange = viewModel::setNotifyMe,
            modifier = Modifier.padding(top = 20.dp),
            description = "We send you a notification as soon as someone nearby can take it.",
            enabled = busy == null,
        )
        HafiButton(
            "Try a wider area",
            onClick = viewModel::retry,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            size = ButtonSize.Large,
            enabled = busy == null,
            loading = busy == Actions.Retry,
            icon = R.drawable.ic_search,
        )
    }
}

/** A direct hire request that ended: say why, and offer the normal search. */
@Composable
internal fun DirectEndedView(data: JobWithSession, viewModel: RequestViewModel, busy: String?) {
    val direct = data.session.direct ?: return
    val heading = if (direct.answer == DirectAnswer.NoAnswer) {
        "${direct.firstName} didn't answer in time"
    } else {
        "${direct.firstName} can't take this one"
    }
    Panel(centered = true) {
        HIcon(R.drawable.ic_user_x, size = 48.dp, tint = Hafi.FgSubtle)
        PanelHeading(heading, Modifier.padding(top = 16.dp))
        Text(
            "No problem: we can ask other people nearby who do " +
                "${data.job.category.nameEn.lowercase(Locale.ROOT)}, and you choose who comes.",
            style = HafiType.Body,
            color = Hafi.FgMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp),
        )
        HafiButton(
            "Ask people nearby instead",
            onClick = viewModel::retry,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 20.dp),
            size = ButtonSize.Large,
            enabled = busy == null,
            loading = busy == Actions.Retry,
            icon = R.drawable.ic_search,
        )
    }
}

// ─── cancelled ───────────────────────────────────────────────────────────────

@Composable
internal fun CancelledView(onAskAgain: () -> Unit) {
    Panel(centered = true) {
        HIcon(R.drawable.ic_ban, size = 36.dp, tint = Hafi.FgMuted)
        PanelHeading("You cancelled this request", Modifier.padding(top = 16.dp))
        Text(
            "Nobody else will be contacted about it. Need help with something?",
            style = HafiType.Body,
            color = Hafi.FgMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp),
        )
        HafiButton(
            "Ask again",
            onClick = onAskAgain,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 20.dp),
            size = ButtonSize.Large,
        )
    }
}
