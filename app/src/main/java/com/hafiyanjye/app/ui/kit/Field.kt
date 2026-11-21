package com.hafiyanjye.app.ui.kit

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.hafiyanjye.app.R
import com.hafiyanjye.app.ui.theme.Hafi
import com.hafiyanjye.app.ui.theme.HafiType

/**
 * A labelled text box with a hint or an inline error underneath. The label is
 * always visible (never only a placeholder) and is read with the box.
 */
@Composable
fun HafiField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    hint: String? = null,
    error: String? = null,
    optional: Boolean = false,
    enabled: Boolean = true,
    password: Boolean = false,
    autofill: ContentType? = null,
    @DrawableRes leadingIcon: Int? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    var revealed by rememberSaveable { mutableStateOf(false) }
    val borderColor by animateColorAsState(
        targetValue = when {
            error != null -> Hafi.Danger
            focused -> Hafi.Accent
            else -> Hafi.LineStrong
        },
        animationSpec = tween(150),
        label = "field border",
    )

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = HafiType.Label, color = Hafi.Fg)
            if (optional) {
                Text(
                    "Optional",
                    style = HafiType.Caption,
                    color = Hafi.FgSubtle,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
        }

        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = true,
            textStyle = HafiType.Body.copy(color = Hafi.Fg),
            cursorBrush = SolidColor(Hafi.Accent),
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            visualTransformation = if (password && !revealed) {
                PasswordVisualTransformation()
            } else {
                VisualTransformation.None
            },
            interactionSource = interaction,
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    contentDescription = label
                    if (autofill != null) contentType = autofill
                    if (error != null) error(error)
                },
            decorationBox = { inner ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .background(if (enabled) Hafi.Surface else Hafi.Surface2)
                        .border(if (focused || error != null) 2.dp else 1.dp, borderColor)
                        .padding(start = 14.dp, end = if (password) 4.dp else 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (leadingIcon != null) {
                        HIcon(leadingIcon, size = 18.dp, tint = Hafi.FgSubtle, modifier = Modifier.padding(end = 10.dp))
                    }
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        if (value.isEmpty() && placeholder != null) {
                            Text(placeholder, style = HafiType.Body, color = Hafi.FgSubtle, maxLines = 1)
                        }
                        inner()
                    }
                    if (password) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clickable(
                                    role = Role.Button,
                                    onClickLabel = if (revealed) "Hide password" else "Show password",
                                ) { revealed = !revealed },
                            contentAlignment = Alignment.Center,
                        ) {
                            HIcon(
                                id = if (revealed) R.drawable.ic_eye_off else R.drawable.ic_eye,
                                size = 18.dp,
                                tint = Hafi.FgMuted,
                                label = if (revealed) "Hide password" else "Show password",
                            )
                        }
                    }
                }
            },
        )

        when {
            error != null -> Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HIcon(R.drawable.ic_circle_alert, size = 14.dp, tint = Hafi.Danger)
                Text(error, style = HafiType.Caption, color = Hafi.Danger)
            }
            hint != null -> Text(hint, style = HafiType.Caption, color = Hafi.FgMuted)
        }
    }
}
