package com.hafiyanjye.app.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.hafiyanjye.app.R
import com.hafiyanjye.app.data.chat.ChatMessage
import com.hafiyanjye.app.ui.kit.ButtonKind
import com.hafiyanjye.app.ui.kit.Divider
import com.hafiyanjye.app.ui.kit.HafiButton
import com.hafiyanjye.app.ui.kit.HafiIconButton
import com.hafiyanjye.app.ui.theme.Hafi
import com.hafiyanjye.app.ui.theme.HafiType

private const val MIN_ZOOM = 1f
private const val MAX_ZOOM = 5f

/**
 * A chat photo over the whole screen: pinch or double-tap to zoom, drag to
 * look around, with buttons for the same so it works without gestures.
 */
@Composable
internal fun PhotoViewer(
    message: ChatMessage,
    sharing: Boolean,
    onShare: () -> Unit,
    onDismiss: () -> Unit,
) {
    val attachment = message.attachment ?: return
    var zoom by remember { mutableFloatStateOf(MIN_ZOOM) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    fun zoomTo(value: Float) {
        zoom = value.coerceIn(MIN_ZOOM, MAX_ZOOM)
        if (zoom == MIN_ZOOM) offset = Offset.Zero
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .background(Hafi.Surface)
                .systemBarsPadding(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = attachment.name,
                    style = HafiType.Subheading,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { heading() },
                )
                HafiIconButton(R.drawable.ic_x, "Close photo", onDismiss)
            }
            Divider()
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clipToBounds()
                    .pointerInput(Unit) {
                        detectTapGestures(onDoubleTap = { zoomTo(if (zoom > MIN_ZOOM) MIN_ZOOM else 2.5f) })
                    }
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, gestureZoom, _ ->
                            zoomTo(zoom * gestureZoom)
                            if (zoom > MIN_ZOOM) offset += pan
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                ChatPhoto(
                    url = attachment.url,
                    localUri = null,
                    contentDescription = message.body.ifBlank { "Photo sent in the chat" },
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = zoom
                            scaleY = zoom
                            translationX = offset.x
                            translationY = offset.y
                        },
                )
            }
            Divider()
            ViewerControls(
                zoom = zoom,
                sharing = sharing,
                onZoomOut = { zoomTo(zoom - 1f) },
                onZoomIn = { zoomTo(zoom + 1f) },
                onShare = onShare,
            )
        }
    }
}

@Composable
private fun ViewerControls(
    zoom: Float,
    sharing: Boolean,
    onZoomOut: () -> Unit,
    onZoomIn: () -> Unit,
    onShare: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        HafiIconButton(R.drawable.ic_minus, "Zoom out", onZoomOut, enabled = zoom > MIN_ZOOM)
        HafiIconButton(R.drawable.ic_plus, "Zoom in", onZoomIn, enabled = zoom < MAX_ZOOM)
        Box(Modifier.weight(1f))
        HafiButton(
            text = "Share or save",
            onClick = onShare,
            kind = ButtonKind.Outline,
            loading = sharing,
            loadingText = "Preparing…",
            icon = R.drawable.ic_share_2,
        )
    }
}
