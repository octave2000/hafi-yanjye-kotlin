package com.hafiyanjye.app.ui.pings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.hafiyanjye.app.R
import com.hafiyanjye.app.core.Format
import com.hafiyanjye.app.data.api.Ping
import com.hafiyanjye.app.ui.kit.ButtonKind
import com.hafiyanjye.app.ui.kit.ButtonSize
import com.hafiyanjye.app.ui.kit.FormAlert
import com.hafiyanjye.app.ui.kit.HIcon
import com.hafiyanjye.app.ui.kit.HafiButton
import com.hafiyanjye.app.ui.kit.HafiChip
import com.hafiyanjye.app.ui.kit.HafiField
import com.hafiyanjye.app.ui.kit.HafiSheet
import com.hafiyanjye.app.ui.kit.HafiTextArea
import com.hafiyanjye.app.ui.theme.Hafi
import com.hafiyanjye.app.ui.theme.HafiType

/** Bottom sheet to answer a ping: available (with a quote and a note), not now, or withdraw. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AnswerSheet(
    ping: Ping,
    state: AnswerSheetState,
    onAvailable: (quoteRwf: Int?, note: String) -> Unit,
    onNotNow: () -> Unit,
    onWithdraw: () -> Unit,
    onClearError: () -> Unit,
    onNavigate: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val left = rememberSecondsLeft(ping.expiresAt)
    val view = pingView(ping, left)

    HafiSheet(onDismiss = onDismiss, title = ping.categoryName) {
        Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PingBadges(ping, view)
                Text(
                    ping.summary,
                    style = HafiType.Heading,
                    modifier = Modifier.semantics { heading() },
                )
            }

            if (ping.direct && view.answerable) {
                Text(
                    "This person picked you from Find help. Accept and you're hired at once; " +
                        "their contact appears on this screen.",
                    style = HafiType.Body,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Hafi.Accent)
                        .padding(12.dp),
                )
            }

            SheetFacts(ping, view, left)

            if (ping.requirements.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("What they need", style = HafiType.BodyStrong)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        ping.requirements.forEach { RequirementChip(it) }
                    }
                }
            }

            when (view) {
                PingView.Chosen -> MatchedPing(
                    ping,
                    onNavigate = { route ->
                        onDismiss()
                        onNavigate(route)
                    },
                )

                PingView.New, PingView.Seen -> AnswerForm(ping, state, onAvailable, onNotNow, onClearError)

                PingView.Waiting -> WaitingPanel(ping, state, onWithdraw)

                PingView.Declined, PingView.Expired, PingView.Withdrawn -> Text(
                    text = closedText(view),
                    style = HafiType.Body,
                    color = Hafi.FgMuted,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Hafi.Surface2)
                        .padding(16.dp),
                )
            }
        }
    }
}

private fun closedText(view: PingView): String = when (view) {
    PingView.Declined -> "You said not now to this job."
    PingView.Expired -> "This ping has expired. Answer faster next time to get more jobs."
    else -> "You withdrew from this job."
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SheetFacts(ping: Ping, view: PingView, secondsLeft: Long) {
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .background(Hafi.Surface2)
            .border(1.dp, Hafi.Line)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SheetFact(
            R.drawable.ic_wallet,
            "Budget",
            ping.budgetRwf?.let(Format::rwf) ?: "Not set: send your price",
            strong = true,
        )
        SheetFact(R.drawable.ic_navigation, "Distance", "${Format.km(ping.distanceKm)} away")
        SheetFact(R.drawable.ic_map_pin, "Area", ping.areaLabel ?: "Kigali")
        if (view.answerable) {
            val lastMinute = isLastMinute(view, secondsLeft)
            SheetFact(
                R.drawable.ic_clock,
                "Answer within",
                formatCountdown(secondsLeft),
                strong = lastMinute,
                danger = lastMinute,
                spoken = "Answer within ${spokenCountdown(secondsLeft)}",
            )
        }
    }
}

/** Quote, note and the two answers. Typed text survives rotation; a bad quote is caught before sending. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AnswerForm(
    ping: Ping,
    state: AnswerSheetState,
    onAvailable: (quoteRwf: Int?, note: String) -> Unit,
    onNotNow: () -> Unit,
    onClearError: () -> Unit,
) {
    val busy = state.busy != null
    var quote by rememberSaveable(ping.id) { mutableStateOf(ping.quoteRwf?.toString().orEmpty()) }
    var note by rememberSaveable(ping.id) { mutableStateOf(ping.note.orEmpty()) }
    var quoteError by rememberSaveable(ping.id) { mutableStateOf<String?>(null) }
    val typed = parseQuote(quote)
    val chips = quoteChips(ping.budgetRwf)

    fun changeQuote(text: String) {
        onClearError()
        quoteError = null
        quote = text.filter(Char::isDigit).take(QUOTE_DIGITS)
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HafiField(
            label = "Your quote (RWF)",
            value = quote,
            onValueChange = ::changeQuote,
            placeholder = ping.budgetRwf?.let { "e.g. $it" } ?: "Your price in RWF",
            optional = true,
            error = quoteError,
            enabled = !busy,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        if (chips.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                chips.forEach { chip ->
                    HafiChip(
                        text = "${chip.label} · ${Format.rwf(chip.value)}",
                        selected = (typed as? QuoteInput.Amount)?.rwf == chip.value,
                        onClick = { changeQuote(chip.value.toString()) },
                        enabled = !busy,
                    )
                }
            }
        }
    }
    HafiTextArea(
        label = "Short note",
        value = note,
        onValueChange = {
            onClearError()
            note = it
        },
        placeholder = "I can be there in 20 minutes and I bring my own tools.",
        optional = true,
        enabled = !busy,
        maxLength = NOTE_MAX,
    )
    FormAlert(state.error)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HafiButton(
            text = if (ping.direct) "Accept and get hired" else "I'm available",
            onClick = {
                when (typed) {
                    is QuoteInput.Invalid -> quoteError = typed.message
                    is QuoteInput.Amount -> onAvailable(typed.rwf, note)
                    QuoteInput.None -> onAvailable(null, note)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            size = ButtonSize.Large,
            enabled = !busy || state.busy == AnswerAction.Available,
            loading = state.busy == AnswerAction.Available,
        )
        HafiButton(
            text = if (ping.direct) "Decline" else "Not now",
            onClick = onNotNow,
            modifier = Modifier.fillMaxWidth(),
            kind = ButtonKind.Outline,
            size = ButtonSize.Large,
            enabled = !busy || state.busy == AnswerAction.NotNow,
            loading = state.busy == AnswerAction.NotNow,
        )
    }
}

/** Said "available": what was sent, and the way out while the job provider has not chosen. */
@Composable
private fun WaitingPanel(ping: Ping, state: AnswerSheetState, onWithdraw: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Hafi.Surface2)
            .border(1.dp, Hafi.Line)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HIcon(R.drawable.ic_clock, size = 16.dp, tint = Hafi.AccentStrong)
            Text("Waiting for the job provider…", style = HafiType.BodyStrong)
        }
        val quoted = ping.quoteRwf?.let { " for ${Format.rwf(it)}" }.orEmpty()
        Text(
            "You said you're available$quoted. If plans change, withdraw so they can pick someone else.",
            style = HafiType.Body,
            color = Hafi.FgMuted,
        )
        ping.note?.takeIf { it.isNotBlank() }?.let { note ->
            Text(
                "“$note”",
                style = HafiType.Body,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Hafi.Surface)
                    .padding(12.dp),
            )
        }
        FormAlert(state.error)
        HafiButton(
            text = "Withdraw",
            onClick = onWithdraw,
            modifier = Modifier.fillMaxWidth(),
            kind = ButtonKind.Destructive,
            loading = state.busy == AnswerAction.Withdraw,
        )
    }
}

@Composable
private fun SheetFact(
    icon: Int,
    label: String,
    value: String,
    strong: Boolean = false,
    danger: Boolean = false,
    spoken: String? = null,
) {
    Column(
        modifier = Modifier.semantics(mergeDescendants = true) {
            if (spoken != null) contentDescription = spoken
        },
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HIcon(icon, size = 14.dp, tint = Hafi.FgMuted)
            Text(label, style = HafiType.Caption, color = Hafi.FgMuted)
        }
        Text(
            value,
            style = HafiType.Body.copy(fontWeight = if (strong) FontWeight.SemiBold else FontWeight.Normal),
            color = if (danger) Hafi.Danger else Hafi.Fg,
        )
    }
}

/** Enough digits for the engine's 10,000,000 RWF limit and one more, so too-high is caught, not cut. */
private const val QUOTE_DIGITS = 9
