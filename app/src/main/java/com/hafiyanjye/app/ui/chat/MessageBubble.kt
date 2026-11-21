package com.hafiyanjye.app.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import com.hafiyanjye.app.R
import com.hafiyanjye.app.data.chat.ChatAttachment
import com.hafiyanjye.app.data.chat.ChatMessage
import com.hafiyanjye.app.data.chat.MessageKind
import com.hafiyanjye.app.data.chat.formatBytes
import com.hafiyanjye.app.data.chat.linkRanges
import com.hafiyanjye.app.data.chat.messageTime
import com.hafiyanjye.app.ui.kit.HIcon
import com.hafiyanjye.app.ui.kit.HafiIconButton
import com.hafiyanjye.app.ui.theme.Hafi
import com.hafiyanjye.app.ui.theme.HafiType

/** What a bubble can ask the thread to do with its attachment. */
internal class AttachmentActions(
    val openPhoto: (ChatMessage) -> Unit,
    val openFile: (ChatMessage) -> Unit,
    val shareFile: (ChatMessage) -> Unit,
    /** Message ids whose file is being downloaded right now. */
    val busy: Set<Int>,
)

/** A stored message, mine on the right in green, theirs on the left (web: MessageBubble). */
@Composable
internal fun MessageBubble(row: ThreadRow.Message, actions: AttachmentActions) {
    val message = row.message
    val mine = row.mine
    BubbleFrame(mine = mine, faded = false, failed = false) {
        val attachment = message.attachment
        when {
            message.kind == MessageKind.Image && attachment != null -> PhotoAttachment(
                attachment = attachment,
                localUri = null,
                onClick = { actions.openPhoto(message) },
            )
            message.kind == MessageKind.File && attachment != null -> FileAttachment(
                name = attachment.name,
                size = attachment.size,
                mine = mine,
                busy = message.id in actions.busy,
                onOpen = { actions.openFile(message) },
                onShare = { actions.shareFile(message) },
            )
        }
        if (message.body.isNotEmpty()) BodyText(message.body, mine, hasAttachment = attachment != null)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, if (mine) Alignment.End else Alignment.Start),
    ) {
        Text(messageTime(message.createdAt), style = HafiType.Caption, color = Hafi.FgMuted)
        row.receipt?.let { receipt ->
            Text(
                text = if (receipt == Receipt.Seen) "· Seen" else "· Sent",
                style = if (receipt == Receipt.Seen) SeenStyle else HafiType.Caption,
                color = if (receipt == Receipt.Seen) Hafi.AccentStrong else Hafi.FgMuted,
            )
        }
    }
}

/** A message of mine still on its way: faded while sending, with Retry and Discard when it failed. */
@Composable
internal fun PendingBubble(pending: PendingMessage, onRetry: () -> Unit, onDiscard: () -> Unit) {
    val failed = pending.status == PendingStatus.Failed
    val file = pending.file
    BubbleFrame(mine = true, faded = !failed, failed = failed) {
        when {
            file != null && file.isImage -> PhotoAttachment(attachment = null, localUri = file.uri, onClick = null)
            file != null -> FileAttachment(
                name = file.name,
                size = file.size,
                mine = true,
                busy = false,
                onOpen = null,
                onShare = null,
            )
        }
        if (pending.body.isNotEmpty()) BodyText(pending.body, mine = true, hasAttachment = file != null)
    }
    if (failed) {
        FailedLine(pending.error ?: "Not sent", onRetry, onDiscard)
    } else {
        Text(
            text = "Sending…",
            style = HafiType.Caption,
            color = Hafi.FgMuted,
            textAlign = TextAlign.End,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
        )
    }
}

@Composable
private fun FailedLine(error: String, onRetry: () -> Unit, onDiscard: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .semantics { liveRegion = LiveRegionMode.Assertive },
        horizontalAlignment = Alignment.End,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            HIcon(R.drawable.ic_alert_circle, size = 14.dp, tint = Hafi.Danger)
            Text("Not sent: $error", style = HafiType.Caption, color = Hafi.Danger)
        }
        Row {
            SmallTextAction("Retry", R.drawable.ic_rotate_cw, Hafi.Danger, onRetry)
            SmallTextAction("Discard", R.drawable.ic_x, Hafi.FgMuted, onDiscard)
        }
    }
}

@Composable
private fun SmallTextAction(label: String, icon: Int, color: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clickable(role = Role.Button, onClick = onClick)
            .widthIn(min = 48.dp)
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        HIcon(icon, size = 14.dp, tint = color)
        Text(label, style = HafiType.Label, color = color)
    }
}

@Composable
private fun BubbleFrame(
    mine: Boolean,
    faded: Boolean,
    failed: Boolean,
    content: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
        val border = when {
            failed -> Hafi.Danger
            mine -> Hafi.Accent
            else -> Hafi.Line
        }
        Column(
            modifier = Modifier
                // At most 85% of the width, hugging the text, on the sender's side.
                .fillMaxWidth(0.85f)
                .wrapContentWidth(if (mine) Alignment.End else Alignment.Start)
                .alpha(if (faded) 0.7f else 1f)
                .background(if (mine) Hafi.Accent else Hafi.Surface)
                .border(if (failed) 2.dp else 1.dp, border),
        ) {
            content()
        }
    }
}

@Composable
private fun BodyText(body: String, mine: Boolean, hasAttachment: Boolean) {
    val linkColor = if (mine) Hafi.AccentFg else Hafi.AccentStrong
    val text = remember(body, linkColor) { linkify(body, linkColor) }
    Text(
        text = text,
        style = HafiType.Body,
        color = if (mine) Hafi.AccentFg else Hafi.Fg,
        modifier = Modifier.padding(
            horizontal = if (hasAttachment) 10.dp else 14.dp,
            vertical = if (hasAttachment) 6.dp else 8.dp,
        ),
    )
}

/** Message text with its web links clickable (web: RichText). */
private fun linkify(body: String, linkColor: Color): AnnotatedString {
    val ranges = linkRanges(body)
    if (ranges.isEmpty()) return AnnotatedString(body)
    val style = TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline))
    return buildAnnotatedString {
        var cursor = 0
        for (range in ranges) {
            append(body.substring(cursor, range.first))
            val url = body.substring(range)
            withLink(LinkAnnotation.Url(url, style)) { append(url) }
            cursor = range.last + 1
        }
        append(body.substring(cursor))
    }
}

@Composable
private fun PhotoAttachment(attachment: ChatAttachment?, localUri: String?, onClick: (() -> Unit)?) {
    val ratio = attachment?.let { photo ->
        val width = photo.width
        val height = photo.height
        if (width != null && height != null && width > 0 && height > 0) width.toFloat() / height else null
    } ?: (4f / 3f)
    val clickable = if (onClick != null) {
        Modifier.clickable(role = Role.Button, onClickLabel = "Open photo", onClick = onClick)
    } else {
        Modifier
    }
    ChatPhoto(
        url = attachment?.url,
        localUri = localUri,
        contentDescription = if (onClick != null) "Photo, tap to open" else "Photo being sent",
        modifier = Modifier
            .padding(4.dp)
            .widthIn(max = 256.dp)
            .fillMaxWidth()
            .aspectRatio(ratio.coerceIn(0.5f, 2f))
            .then(clickable),
    )
}

@Composable
private fun FileAttachment(
    name: String,
    size: Long,
    mine: Boolean,
    busy: Boolean,
    onOpen: (() -> Unit)?,
    onShare: (() -> Unit)?,
) {
    val tint = if (mine) Hafi.AccentFg else Hafi.AccentStrong
    val textColor = if (mine) Hafi.AccentFg else Hafi.Fg
    val subColor = if (mine) Hafi.AccentFg else Hafi.FgMuted
    Row(
        modifier = Modifier.widthIn(max = 280.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val open = if (onOpen != null && !busy) {
            Modifier.clickable(role = Role.Button, onClickLabel = "Open $name", onClick = onOpen)
        } else {
            Modifier
        }
        Row(
            modifier = Modifier
                .weight(1f, fill = false)
                .then(open)
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            HIcon(R.drawable.ic_file_text, size = 24.dp, tint = tint)
            Column {
                Text(name, style = HafiType.Label, color = textColor, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    text = if (busy) "Opening…" else formatBytes(size),
                    style = HafiType.Caption,
                    color = subColor,
                )
            }
        }
        if (onShare != null) {
            HafiIconButton(
                icon = R.drawable.ic_share_2,
                label = "Share or save $name",
                onClick = onShare,
                tint = tint,
                enabled = !busy,
            )
        }
    }
}

private val SeenStyle = HafiType.Caption.copy(fontWeight = FontWeight.SemiBold)
