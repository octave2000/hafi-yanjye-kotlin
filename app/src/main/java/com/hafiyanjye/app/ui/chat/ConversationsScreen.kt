package com.hafiyanjye.app.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hafiyanjye.app.R
import com.hafiyanjye.app.core.Tone
import com.hafiyanjye.app.data.chat.ChatContext
import com.hafiyanjye.app.data.chat.ChatContextType
import com.hafiyanjye.app.data.chat.Conversation
import com.hafiyanjye.app.data.chat.ConversationList
import com.hafiyanjye.app.data.chat.MessageKind
import com.hafiyanjye.app.data.chat.listTime
import com.hafiyanjye.app.data.chat.previewText
import com.hafiyanjye.app.ui.Load
import com.hafiyanjye.app.ui.kit.ButtonKind
import com.hafiyanjye.app.ui.kit.Divider
import com.hafiyanjye.app.ui.kit.EmptyState
import com.hafiyanjye.app.ui.kit.ErrorPanel
import com.hafiyanjye.app.ui.kit.HIcon
import com.hafiyanjye.app.ui.kit.HafiButton
import com.hafiyanjye.app.ui.kit.InfoBanner
import com.hafiyanjye.app.ui.kit.PageHeader
import com.hafiyanjye.app.ui.theme.Hafi
import com.hafiyanjye.app.ui.theme.HafiType

/** The Chat tab (web: chat/layout.tsx and ConversationList): every conversation, newest first. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ConversationsScreen(
    viewModel: ConversationsViewModel,
    meId: Int,
    onOpen: (conversationId: Int) -> Unit,
    onBrowseRentals: () -> Unit,
) {
    val state by viewModel.loader.state.collectAsStateWithLifecycle()
    val online by viewModel.online.collectAsStateWithLifecycle()
    val ready = state as? Load.Ready
    val unread = ready?.value?.unread ?: 0
    Column(Modifier.fillMaxSize()) {
        PageHeader(
            title = "Hafi Chat",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            description = if (unread > 0) "$unread unread" else null,
        )
        PullToRefreshBox(
            isRefreshing = ready?.refreshing == true,
            onRefresh = viewModel.loader::refresh,
            modifier = Modifier.weight(1f),
        ) {
            when (val shown = state) {
                Load.Loading -> ConversationsSkeleton()
                is Load.Failed -> ErrorPanel(
                    "Couldn't load your chats. ${shown.message}",
                    viewModel.loader::refresh,
                    Modifier.padding(16.dp),
                )
                is Load.Ready -> ConversationRows(
                    list = shown.value,
                    staleError = shown.staleError,
                    online = online,
                    meId = meId,
                    onOpen = onOpen,
                    onRetry = viewModel.loader::refresh,
                    onBrowseRentals = onBrowseRentals,
                )
            }
        }
    }
}

@Composable
private fun ConversationRows(
    list: ConversationList,
    staleError: String?,
    online: Set<Int>,
    meId: Int,
    onOpen: (Int) -> Unit,
    onRetry: () -> Unit,
    onBrowseRentals: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp),
    ) {
        if (staleError != null) {
            item(key = "stale") {
                InfoBanner(
                    text = "Couldn't refresh your chats. $staleError",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    tone = Tone.Warn,
                    action = { HafiButton("Try again", onRetry, kind = ButtonKind.Ghost) },
                )
            }
        }
        if (list.items.isEmpty()) {
            item(key = "empty") {
                EmptyState(
                    title = "No chats yet",
                    description = "Tap Chat on a car or home you like, or on your match after a request. " +
                        "Your conversations show up here.",
                    modifier = Modifier.padding(16.dp),
                    icon = R.drawable.ic_messages_square,
                    action = { HafiButton("Browse rentals", onBrowseRentals) },
                )
            }
        }
        items(list.items, key = { it.id }) { conversation ->
            ConversationRow(
                conversation = conversation,
                online = conversation.other.userId in online,
                meId = meId,
                onClick = { onOpen(conversation.id) },
            )
            Divider()
        }
    }
}

@Composable
private fun ConversationRow(conversation: Conversation, online: Boolean, meId: Int, onClick: () -> Unit) {
    val unread = conversation.unread
    val hasUnread = unread > 0
    val timeLabel = listTime(conversation.updatedAt)
    val preview = previewText(conversation, meId)
    val spoken = buildString {
        append(conversation.other.name)
        append(if (online) ", online" else ", offline")
        conversation.context?.let { append(", about ").append(it.title) }
        append(". ").append(preview)
        append(". ").append(timeLabel)
        if (hasUnread) append(". $unread unread")
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClickLabel = "Open chat", onClick = onClick)
            .background(Hafi.Surface)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .clearAndSetSemantics { contentDescription = spoken },
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PresenceAvatar(conversation.other.name, conversation.other.picture, online)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = conversation.other.name,
                    style = if (hasUnread) HafiType.BodyStrong else HafiType.Label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = timeLabel,
                    style = if (hasUnread) UnreadTime else HafiType.Caption,
                    color = if (hasUnread) Hafi.AccentStrong else Hafi.FgMuted,
                )
            }
            conversation.context?.let { ContextLine(it) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                PreviewLine(conversation, preview, hasUnread, Modifier.weight(1f))
                if (hasUnread) {
                    Spacer(Modifier.width(8.dp))
                    UnreadCount(unread)
                }
            }
        }
    }
}

@Composable
private fun ContextLine(context: ChatContext) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        HIcon(contextIcon(context), size = 14.dp, tint = Hafi.FgMuted)
        Text(
            text = context.title,
            style = HafiType.Caption,
            color = Hafi.FgMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun PreviewLine(conversation: Conversation, preview: String, hasUnread: Boolean, modifier: Modifier) {
    val icon = when (conversation.lastMessage?.kind) {
        MessageKind.Image -> R.drawable.ic_image
        MessageKind.File -> R.drawable.ic_file_text
        else -> null
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (icon != null) HIcon(icon, size = 14.dp, tint = Hafi.FgMuted)
        Text(
            text = preview,
            style = HafiType.Body,
            color = if (hasUnread) Hafi.Fg else Hafi.FgMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun UnreadCount(count: Int) {
    Box(
        modifier = Modifier
            .defaultMinSize(minWidth = 20.dp, minHeight = 20.dp)
            .background(Hafi.Accent, CircleShape)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (count > 99) "99+" else count.toString(),
            style = HafiType.Micro,
            color = Hafi.AccentFg,
            modifier = Modifier.semantics { contentDescription = "$count unread" },
        )
    }
}

/** The web's icon for what a chat is about: a house, a car, or a wrench for a job. */
internal fun contextIcon(context: ChatContext): Int = when {
    context.type == ChatContextType.Job -> R.drawable.ic_wrench
    context.listingKind == "home" -> R.drawable.ic_house
    else -> R.drawable.ic_car_front
}

private val UnreadTime = HafiType.Caption.copy(fontWeight = FontWeight.SemiBold)
