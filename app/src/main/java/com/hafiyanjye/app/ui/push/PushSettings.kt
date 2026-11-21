package com.hafiyanjye.app.ui.push

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hafiyanjye.app.data.push.PushManager
import com.hafiyanjye.app.data.push.PushPermission
import com.hafiyanjye.app.data.push.PushState
import com.hafiyanjye.app.ui.LocalContainer
import com.hafiyanjye.app.ui.kit.ButtonKind
import com.hafiyanjye.app.ui.kit.FormAlert
import com.hafiyanjye.app.ui.kit.HafiButton
import com.hafiyanjye.app.ui.kit.HafiSwitchRow

/** The line under the switch, in the web's words adapted to a phone. */
internal fun pushDescription(state: PushState): String = when {
    !state.configured -> "Push notifications are not set up in this build of the app. " +
        "Your notifications still show in the app."
    state.permission == PushPermission.Blocked -> "Notifications are blocked for Hafi Yanjye. " +
        "Allow them in Android settings, then come back here."
    state.busy -> "Updating this phone…"
    state.on -> "On: you'll get alerts for new matches, messages and nearby jobs on this phone."
    else -> "Off: turn on to get alerts even when Hafi Yanjye is closed."
}

/**
 * The profile's push switch for this phone. Asks Android's permission when
 * needed, sends the person to settings when it is blocked, and shows the
 * engine's error with a retry.
 */
@Composable
fun PushSettings(modifier: Modifier = Modifier) {
    val container = LocalContainer.current
    val context = LocalContext.current
    val push = remember(container) { PushManager.get(container) }
    val state by push.state.collectAsStateWithLifecycle()
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        push.onPermissionResult(granted)
    }

    // Coming back from Android settings may have changed the permission.
    LifecycleResumeEffect(push) {
        push.refresh()
        onPauseOrDispose { }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        HafiSwitchRow(
            title = "Push notifications",
            checked = if (state.busy) state.wanted else state.on,
            enabled = state.configured && !state.busy,
            description = pushDescription(state),
            onCheckedChange = { turnOn ->
                val mustAsk = turnOn &&
                    state.permission != PushPermission.Granted &&
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                if (mustAsk) {
                    askPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    push.setWanted(turnOn)
                }
            },
        )
        if (state.configured && state.permission == PushPermission.Blocked) {
            HafiButton(
                text = "Open notification settings",
                onClick = {
                    val settings = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    context.startActivity(settings)
                },
                kind = ButtonKind.Outline,
            )
        }
        if (state.error != null) {
            FormAlert(state.error)
            HafiButton(
                text = "Try again",
                onClick = push::refresh,
                kind = ButtonKind.Outline,
                loading = state.busy,
                loadingText = "Trying again…",
            )
        }
    }
}
