package com.hafiyanjye.app.ui.trip

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.hafiyanjye.app.data.trip.DelayReason
import com.hafiyanjye.app.data.trip.TripApi
import com.hafiyanjye.app.ui.kit.ButtonKind
import com.hafiyanjye.app.ui.kit.FormAlert
import com.hafiyanjye.app.ui.kit.HafiButton
import com.hafiyanjye.app.ui.kit.HafiChip
import com.hafiyanjye.app.ui.kit.HafiSheet
import com.hafiyanjye.app.ui.kit.HafiTextArea
import com.hafiyanjye.app.ui.theme.Hafi
import com.hafiyanjye.app.ui.theme.HafiType

/** "Running late": how long and why, sent to the job provider at once (web: DelayDialog). */
@Composable
internal fun DelaySheet(
    hirer: String,
    sending: Boolean,
    error: String?,
    onSend: (minutes: Int, reason: DelayReason, note: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var minutes by rememberSaveable { mutableIntStateOf(DefaultMinutes) }
    var reason by rememberSaveable { mutableStateOf(DelayReason.Traffic) }
    var note by rememberSaveable { mutableStateOf("") }
    HafiSheet(onDismiss = { if (!sending) onDismiss() }, title = "Running late?") {
        Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Text(
                text = "We tell $hirer right away, with the reason, so they're not left waiting.",
                style = HafiType.Body,
                color = Hafi.FgMuted,
            )
            ChoiceGroup("Why") {
                DelayReason.entries.forEach { item ->
                    HafiChip(text = item.label, selected = reason == item, onClick = { reason = item })
                }
            }
            ChoiceGroup("About how late") {
                TripApi.DelayMinutes.forEach { value ->
                    HafiChip(text = "$value min", selected = minutes == value, onClick = { minutes = value })
                }
            }
            HafiTextArea(
                label = "A word for $hirer",
                value = note,
                onValueChange = { note = it.take(TripApi.DelayNoteMax) },
                placeholder = "Stuck at Kimironko roundabout, sorry",
                optional = true,
                enabled = !sending,
                minLines = 2,
                maxLength = TripApi.DelayNoteMax,
            )
            FormAlert(error)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HafiButton(
                    text = "Cancel",
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    kind = ButtonKind.Outline,
                    enabled = !sending,
                )
                HafiButton(
                    text = "Tell $hirer",
                    onClick = { onSend(minutes, reason, note) },
                    modifier = Modifier.weight(1f),
                    loading = sending,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChoiceGroup(title: String, chips: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = HafiType.Label,
            color = Hafi.Fg,
            modifier = Modifier.semantics { heading() },
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            chips()
        }
    }
}

private const val DefaultMinutes = 15
