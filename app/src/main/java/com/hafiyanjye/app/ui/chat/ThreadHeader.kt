package com.hafiyanjye.app.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hafiyanjye.app.R
import com.hafiyanjye.app.data.chat.ChatContext
import com.hafiyanjye.app.data.chat.ChatContextType
import com.hafiyanjye.app.data.chat.ChatHire
import com.hafiyanjye.app.data.chat.Conversation
import com.hafiyanjye.app.ui.kit.ButtonKind
import com.hafiyanjye.app.ui.kit.Divider
import com.hafiyanjye.app.ui.kit.FormAlert
import com.hafiyanjye.app.ui.kit.HIcon
import com.hafiyanjye.app.ui.kit.HafiButton
import com.hafiyanjye.app.ui.kit.HafiChip
import com.hafiyanjye.app.ui.kit.HafiSheet
import com.hafiyanjye.app.ui.kit.HafiTextArea
import com.hafiyanjye.app.ui.location.LocationPicker
import com.hafiyanjye.app.ui.location.PickedLocation
import com.hafiyanjye.app.ui.theme.Hafi
import com.hafiyanjye.app.ui.theme.HafiType

/**
 * Who the chat is with and what it is about (web: ThreadHeader): picture,
 * online or offline, Call when a phone is shared, Hire when they have a work
 * profile, and a bar that leads to the listing or the job.
 */
@Composable
internal fun ThreadHeader(
    conversation: Conversation,
    online: Boolean,
    onCall: (String) -> Unit,
    onHire: () -> Unit,
    onOpenContext: (ChatContext) -> Unit,
) {
    val other = conversation.other
    Column(
        Modifier
            .fillMaxWidth()
            .background(Hafi.Surface),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PresenceAvatar(other.name, other.picture, online, size = 40.dp)
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clearAndSetSemantics { contentDescription = if (online) "Online" else "Offline" },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                PresenceDot(online)
                Text(
                    text = if (online) "Online" else "Offline",
                    style = if (online) OnlineStyle else HafiType.Caption,
                    color = if (online) Hafi.AccentStrong else Hafi.FgMuted,
                )
            }
            other.phone?.let { phone ->
                HafiButton(
                    text = "Call",
                    onClick = { onCall(phone) },
                    kind = ButtonKind.Outline,
                    icon = R.drawable.ic_phone,
                )
            }
            conversation.hire?.let { hire ->
                HafiButton(
                    text = "Hire",
                    onClick = onHire,
                    icon = R.drawable.ic_handshake,
                    modifier = Modifier.semantics { contentDescription = "Hire ${hire.displayName}" },
                )
            }
        }
        conversation.context?.let { context ->
            Divider()
            ContextBar(context, onOpen = { onOpenContext(context) })
        }
        Divider()
    }
}

@Composable
private fun ContextBar(context: ChatContext, onOpen: () -> Unit) {
    val isJob = context.type == ChatContextType.Job
    val action = when {
        !isJob -> "Open listing"
        context.mine -> "View job"
        else -> "View request"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClickLabel = action, onClick = onOpen)
            .background(Hafi.Surface2)
            .heightIn(min = 48.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        HIcon(contextIcon(context), size = 16.dp, tint = Hafi.AccentStrong)
        Text(
            text = if (isJob) "Job:" else "About:",
            style = HafiType.Caption,
            color = Hafi.FgMuted,
        )
        Text(
            text = context.title,
            style = HafiType.Label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (isJob) Text(action, style = HafiType.Label, color = Hafi.AccentStrong)
        HIcon(R.drawable.ic_chevron_right, size = 16.dp, tint = Hafi.FgMuted)
    }
}

/**
 * Hire the person in the chat directly (web: HireDialog): what you need,
 * which of their services, where. They accept or decline; the job opens.
 */
@Composable
internal fun HireSheet(
    hire: ChatHire,
    state: ThreadHireState,
    onSend: (serviceSlug: String?, need: String, location: PickedLocation?) -> Unit,
    onDismiss: () -> Unit,
) {
    val firstName = hire.displayName.substringBefore(' ')
    var need by rememberSaveable(hire.workerId) { mutableStateOf("") }
    var service by rememberSaveable(hire.workerId) { mutableStateOf(hire.categories.firstOrNull()?.slug) }
    var location by remember(hire.workerId) { mutableStateOf<PickedLocation?>(null) }
    HafiSheet(onDismiss = { if (!state.sending) onDismiss() }, title = "Hire ${hire.displayName}") {
        Column(
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "We send $firstName your request. You're hired together as soon as they accept, " +
                    "then you can chat or call.",
                style = HafiType.Body,
                color = Hafi.FgMuted,
            )
            HafiTextArea(
                label = "What do you need?",
                value = need,
                onValueChange = { need = it.take(MAX_NEED_CHARS) },
                placeholder = "e.g. Two sockets in the kitchen stopped working, today if possible",
                enabled = !state.sending,
                maxLength = MAX_NEED_CHARS,
            )
            if (hire.categories.size > 1) {
                ServiceChoice(hire, service, enabled = !state.sending, onChoose = { service = it })
            }
            LocationPicker(
                value = location,
                onChange = { location = it },
                label = "Where",
                enabled = !state.sending,
            )
            FormAlert(state.error)
            HafiButton(
                text = "Send hire request",
                onClick = { onSend(service, need, location) },
                modifier = Modifier.fillMaxWidth(),
                loading = state.sending,
                loadingText = "Sending request",
                icon = R.drawable.ic_user_check,
            )
            HafiButton(
                text = "Cancel",
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                kind = ButtonKind.Outline,
                enabled = !state.sending,
            )
        }
    }
}

@Composable
private fun ServiceChoice(hire: ChatHire, selected: String?, enabled: Boolean, onChoose: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Which service", style = HafiType.Label)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            hire.categories.forEach { category ->
                HafiChip(
                    text = category.nameEn,
                    selected = category.slug == selected,
                    onClick = { onChoose(category.slug) },
                    enabled = enabled,
                )
            }
        }
    }
}

private const val MAX_NEED_CHARS = 500
private val OnlineStyle = HafiType.Caption.copy(fontWeight = FontWeight.SemiBold)
