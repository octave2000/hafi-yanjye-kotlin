package com.hafiyanjye.app.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.hafiyanjye.app.R
import com.hafiyanjye.app.data.chat.ChatLimits
import com.hafiyanjye.app.ui.kit.ButtonKind
import com.hafiyanjye.app.ui.kit.FormAlert
import com.hafiyanjye.app.ui.kit.HafiButton
import com.hafiyanjye.app.ui.kit.HafiIconButton
import com.hafiyanjye.app.ui.kit.HafiSheet
import com.hafiyanjye.app.ui.theme.Hafi
import com.hafiyanjye.app.ui.theme.HafiType

/**
 * The bar under a thread (web: Composer): attach, write, send. The counter
 * shows from 1,800 characters; over 2,000 the message cannot be sent.
 */
@Composable
internal fun Composer(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onAttach: () -> Unit,
    fileProblem: String?,
    modifier: Modifier = Modifier,
) {
    val over = text.length > ChatLimits.MAX_MESSAGE_CHARS
    val canSend = text.isNotBlank() && !over
    // Drawn in DetailScaffold's bottom bar, which already sits above the keyboard and navigation bar.
    Column(modifier.fillMaxWidth()) {
        if (fileProblem != null) {
            FormAlert(fileProblem, Modifier.padding(start = 8.dp, end = 8.dp, top = 8.dp))
        }
        if (text.length >= ChatLimits.COUNTER_FROM) CharacterCounter(text.length, over)
        Row(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            HafiIconButton(R.drawable.ic_paperclip, "Attach photos or documents", onAttach, tint = Hafi.FgMuted)
            MessageField(text, onTextChange, over, Modifier.weight(1f))
            SendButton(enabled = canSend, onClick = onSend)
        }
    }
}

@Composable
private fun CharacterCounter(length: Int, over: Boolean) {
    val left = ChatLimits.MAX_MESSAGE_CHARS - length
    Text(
        text = if (over) "${-left} over the limit" else "$left left",
        style = HafiType.Caption,
        color = if (over) Hafi.Danger else Hafi.FgMuted,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 60.dp, end = 60.dp, top = 6.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
    )
}

@Composable
private fun MessageField(text: String, onTextChange: (String) -> Unit, over: Boolean, modifier: Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val borderColor = when {
        over -> Hafi.Danger
        focused -> Hafi.Accent
        else -> Hafi.LineStrong
    }
    BasicTextField(
        value = text,
        onValueChange = onTextChange,
        textStyle = HafiType.Body.copy(color = Hafi.Fg),
        cursorBrush = SolidColor(Hafi.Accent),
        interactionSource = interaction,
        maxLines = 6,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        modifier = modifier
            .heightIn(min = 48.dp)
            .semantics {
                contentDescription = "Message"
                if (over) error("Messages can be at most 2,000 characters")
            },
        decorationBox = { field ->
            Box(
                modifier = Modifier
                    .border(if (focused) 2.dp else 1.dp, borderColor)
                    .background(Hafi.Surface)
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (text.isEmpty()) {
                    Text("Write a message", style = HafiType.Body, color = Hafi.FgMuted)
                }
                field()
            }
        },
    )
}

@Composable
private fun SendButton(enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .background(if (enabled) Hafi.Accent else Hafi.Surface3),
        contentAlignment = Alignment.Center,
    ) {
        HafiIconButton(
            icon = R.drawable.ic_send_horizontal,
            label = "Send message",
            onClick = onClick,
            tint = if (enabled) Hafi.AccentFg else Hafi.FgMuted,
            enabled = enabled,
        )
    }
}

/** Photos through the system photo picker, documents through the file picker. */
@Composable
internal fun AttachSheet(onPhotos: () -> Unit, onDocuments: () -> Unit, onDismiss: () -> Unit) {
    HafiSheet(onDismiss = onDismiss, title = "Send a photo or file") {
        Column(
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Up to 10 at once. Photos are made smaller before sending; " +
                    "PDF, Word, Excel and PowerPoint files up to 10 MB.",
                style = HafiType.Body,
                color = Hafi.FgMuted,
            )
            HafiButton(
                text = "Photos",
                onClick = onPhotos,
                modifier = Modifier.fillMaxWidth(),
                kind = ButtonKind.Outline,
                icon = R.drawable.ic_image,
            )
            HafiButton(
                text = "Documents",
                onClick = onDocuments,
                modifier = Modifier.fillMaxWidth(),
                kind = ButtonKind.Outline,
                icon = R.drawable.ic_file_text,
            )
        }
    }
}
