package com.hafiyanjye.app.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.hafiyanjye.app.R
import com.hafiyanjye.app.data.api.Me
import com.hafiyanjye.app.data.profile.ProfileRules
import com.hafiyanjye.app.ui.kit.ButtonKind
import com.hafiyanjye.app.ui.kit.FormAlert
import com.hafiyanjye.app.ui.kit.HafiButton
import com.hafiyanjye.app.ui.kit.HafiField
import com.hafiyanjye.app.ui.theme.Hafi
import com.hafiyanjye.app.ui.theme.HafiType

/**
 * "Delete my account": says plainly what goes, then asks for the word DELETE
 * (and the password, for accounts that have one) before deleting.
 */
@Composable
internal fun DeleteAccountSection(
    me: Me,
    deleting: Boolean,
    error: String?,
    enabled: Boolean,
    onClearError: () -> Unit,
    onDelete: (confirm: String, password: String) -> Unit,
) {
    var open by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Hafi.Surface)
            .border(1.dp, Hafi.Danger.copy(alpha = 0.4f))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Delete account", style = HafiType.Subheading, modifier = Modifier.semantics { heading() })
            Text(
                "Removes your account and everything in it for good. This can't be undone.",
                style = HafiType.Body,
                color = Hafi.FgMuted,
            )
        }
        HafiButton(
            text = "Delete my account",
            onClick = { open = true },
            kind = ButtonKind.Outline,
            icon = R.drawable.ic_trash_2,
            enabled = enabled,
        )
    }

    if (open) {
        DeleteAccountDialog(
            me = me,
            deleting = deleting,
            error = error,
            onDelete = onDelete,
            onClose = {
                open = false
                onClearError()
            },
        )
    }
}

@Composable
private fun DeleteAccountDialog(
    me: Me,
    deleting: Boolean,
    error: String?,
    onDelete: (confirm: String, password: String) -> Unit,
    onClose: () -> Unit,
) {
    var confirm by rememberSaveable { mutableStateOf("") }
    // Not saveable on purpose: a password is never written to saved state.
    var password by remember { mutableStateOf("") }
    val ready = ProfileRules.canDelete(me, confirm, password)
    val submit = { if (ready && !deleting) onDelete(confirm, password) }

    Dialog(onDismissRequest = { if (!deleting) onClose() }) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .background(Hafi.Surface)
                .border(1.dp, Hafi.Line)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Delete your account?", style = HafiType.Heading, modifier = Modifier.semantics { heading() })
                Text("This deletes ${me.email} and can't be undone.", style = HafiType.Body, color = Hafi.FgMuted)
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val lines = buildList {
                    add("Your profile, photo and phone number")
                    add("Your requests and your chats")
                    if (me.hasWorkProfile) add("Your work profile, services, reviews and documents")
                    add("Your rental listings and CV")
                }
                for (line in lines) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.padding(top = 9.dp).size(6.dp).background(Hafi.Danger, CircleShape))
                        Text(line, style = HafiType.Body, color = Hafi.Fg)
                    }
                }
            }
            Text(
                "Anything still going is cancelled, and the people on the other side are told.",
                style = HafiType.Caption,
                color = Hafi.FgMuted,
            )

            HafiField(
                label = "Type ${ProfileRules.DELETE_WORD} to confirm",
                value = confirm,
                onValueChange = { confirm = it },
                enabled = !deleting,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    autoCorrectEnabled = false,
                    imeAction = if (me.hasPassword) ImeAction.Next else ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { submit() }),
            )
            if (me.hasPassword) {
                HafiField(
                    label = "Your password",
                    value = password,
                    onValueChange = { password = it },
                    enabled = !deleting,
                    password = true,
                    autofill = ContentType.Password,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                )
            }
            FormAlert(error)

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                HafiButton(
                    text = "Delete for good",
                    onClick = submit,
                    modifier = Modifier.fillMaxWidth(),
                    kind = ButtonKind.Destructive,
                    icon = R.drawable.ic_trash_2,
                    enabled = ready,
                    loading = deleting,
                    loadingText = "Deleting…",
                )
                HafiButton(
                    text = "Keep my account",
                    onClick = onClose,
                    modifier = Modifier.fillMaxWidth(),
                    kind = ButtonKind.Outline,
                    enabled = !deleting,
                )
            }
        }
    }
}
