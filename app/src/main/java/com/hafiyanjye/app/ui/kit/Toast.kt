package com.hafiyanjye.app.ui.kit

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.hafiyanjye.app.R
import com.hafiyanjye.app.core.Tone
import com.hafiyanjye.app.ui.theme.Hafi
import com.hafiyanjye.app.ui.theme.HafiType
import com.hafiyanjye.app.ui.theme.rememberReducedMotion
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.delay

internal data class ToastMessage(val id: Long, val message: String, val tone: Tone)

/**
 * Short confirmations and failures that need no answer ("Saved", "Could not
 * send"). One at a time; a new one replaces the one showing.
 */
class Toaster {
    private val ids = AtomicLong()

    // Snapshot state may be written from any thread; the host recomposes on the main one.
    internal var current by mutableStateOf<ToastMessage?>(null)
        private set

    fun show(message: String, tone: Tone = Tone.Neutral) {
        current = ToastMessage(ids.incrementAndGet(), message, tone)
    }

    /** Only clears the toast it was asked about, so a late timer cannot close a newer one. */
    internal fun dismiss(id: Long) {
        if (current?.id == id) current = null
    }
}

val LocalToaster = staticCompositionLocalOf<Toaster> { error("Toaster not provided") }

/** Draws the current toast. Mounted once by the app shell, above the content, where it should appear. */
@Composable
fun ToastHost(toaster: Toaster, modifier: Modifier = Modifier) {
    val reduced = rememberReducedMotion()
    val current = toaster.current
    // Keeps the words on screen while the bar animates out.
    var last by remember { mutableStateOf<ToastMessage?>(null) }
    if (current != null) last = current

    LaunchedEffect(current?.id) {
        if (current != null) {
            delay(if (current.tone == Tone.Danger) 6_000 else 4_000)
            toaster.dismiss(current.id)
        }
    }

    Box(modifier = modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }) {
        AnimatedVisibility(
            visible = current != null,
            enter = if (reduced) {
                EnterTransition.None
            } else {
                fadeIn(tween(200)) + slideInVertically(tween(200)) { it / 2 }
            },
            exit = if (reduced) {
                ExitTransition.None
            } else {
                fadeOut(tween(150)) + slideOutVertically(tween(150)) { it / 2 }
            },
        ) {
            val toast = last ?: return@AnimatedVisibility
            val (icon, tint) = when (toast.tone) {
                Tone.Accent -> R.drawable.ic_circle_check to Hafi.Lime
                Tone.Danger -> R.drawable.ic_circle_alert to Hafi.DangerSoft
                Tone.Warn -> R.drawable.ic_triangle_alert to Hafi.WarnSoft
                Tone.Info, Tone.Neutral -> R.drawable.ic_info to Hafi.InkMuted
            }
            Row(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .background(Hafi.Ink)
                    .clickable(onClickLabel = "Dismiss") { toaster.dismiss(toast.id) }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HIcon(icon, size = 20.dp, tint = tint)
                Text(
                    text = toast.message,
                    style = HafiType.Label,
                    color = Hafi.InkFg,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
