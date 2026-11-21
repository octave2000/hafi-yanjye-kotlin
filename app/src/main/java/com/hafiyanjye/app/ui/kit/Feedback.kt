package com.hafiyanjye.app.ui.kit

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hafiyanjye.app.R
import com.hafiyanjye.app.core.Tone
import com.hafiyanjye.app.ui.theme.Hafi
import com.hafiyanjye.app.ui.theme.HafiType
import com.hafiyanjye.app.ui.theme.rememberReducedMotion

/** A red box above a form for a failure that isn't about one field. Announced when it appears. */
@Composable
fun FormAlert(message: String?, modifier: Modifier = Modifier) {
    if (message == null) return
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Hafi.DangerSoft)
            .border(1.dp, Hafi.Danger.copy(alpha = 0.3f))
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .semantics { liveRegion = LiveRegionMode.Assertive },
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        HIcon(R.drawable.ic_triangle_alert, size = 18.dp, tint = Hafi.Danger, modifier = Modifier.padding(top = 2.dp))
        Text(message, style = HafiType.Label, color = Hafi.Danger)
    }
}

/** A loading failure with a way out. */
@Composable
fun ErrorPanel(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Hafi.DangerSoft)
            .padding(16.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HIcon(
                R.drawable.ic_triangle_alert,
                size = 20.dp,
                tint = Hafi.Danger,
                modifier = Modifier.padding(top = 1.dp),
            )
            Text(message, style = HafiType.Label, color = Hafi.Danger)
        }
        HafiButton("Try again", onRetry, kind = ButtonKind.Outline, icon = R.drawable.ic_refresh_cw)
    }
}

/** Small upper-case status chip. The word carries the meaning; colour only supports it. */
@Composable
fun Badge(text: String, tone: Tone, modifier: Modifier = Modifier, live: Boolean = false) {
    val (fill, ink) = when (tone) {
        Tone.Accent -> Hafi.AccentSoft to Hafi.AccentStrong
        Tone.Info -> Hafi.InfoSoft to Hafi.Info
        Tone.Warn -> Hafi.WarnSoft to Hafi.Warn
        Tone.Danger -> Hafi.DangerSoft to Hafi.Danger
        Tone.Neutral -> Hafi.Surface2 to Hafi.FgMuted
    }
    Row(
        modifier = modifier
            .background(fill)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (live) PulseDot(ink)
        Text(text.uppercase(), style = HafiType.Micro, color = ink, maxLines = 1)
    }
}

/** A dot that breathes while something is still happening. Still when animations are off. */
@Composable
fun PulseDot(color: Color, modifier: Modifier = Modifier) {
    val alpha = if (rememberReducedMotion()) {
        1f
    } else {
        val transition = rememberInfiniteTransition(label = "pulse")
        val value by transition.animateFloat(
            initialValue = 1f,
            targetValue = 0.35f,
            animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
            label = "pulse alpha",
        )
        value
    }
    Box(modifier.size(6.dp).alpha(alpha).background(color, CircleShape))
}

/** Placeholder block while content loads. */
@Composable
fun Skeleton(modifier: Modifier = Modifier) {
    val alpha = if (rememberReducedMotion()) {
        1f
    } else {
        val transition = rememberInfiniteTransition(label = "skeleton")
        val value by transition.animateFloat(
            initialValue = 1f,
            targetValue = 0.5f,
            animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
            label = "skeleton alpha",
        )
        value
    }
    Box(modifier.alpha(alpha).background(Hafi.Surface3))
}

/** Dashed panel for "nothing here yet", with one clear next step. */
@Composable
fun EmptyState(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Hafi.Surface.copy(alpha = 0.6f))
            .drawBehind {
                drawRect(
                    color = Hafi.LineStrong,
                    style = Stroke(
                        width = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx())),
                    ),
                    topLeft = Offset.Zero,
                )
            }
            .padding(horizontal = 24.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (icon != null) {
            HIcon(icon, size = 36.dp, tint = Hafi.AccentStrong)
            Spacer(Modifier.height(16.dp))
        }
        Text(title, style = HafiType.Heading, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(
            description,
            style = HafiType.Body,
            color = Hafi.FgMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 340.dp),
        )
        if (action != null) {
            Spacer(Modifier.height(20.dp))
            action()
        }
    }
}

/** Centred spinner with words, for a whole screen that is waiting. */
@Composable
fun FullScreenLoading(label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().semantics(mergeDescendants = true) { contentDescription = label },
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(28.dp),
            color = Hafi.Accent,
            trackColor = Hafi.Surface3,
            strokeWidth = 3.dp,
        )
        Spacer(Modifier.height(14.dp))
        Text(label, style = HafiType.Label, color = Hafi.FgMuted)
    }
}

/** Eyebrow, title and one line of context at the top of a screen. */
@Composable
fun PageHeader(
    title: String,
    modifier: Modifier = Modifier,
    eyebrow: String? = null,
    description: String? = null,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (eyebrow != null) {
            Text(eyebrow.uppercase(), style = HafiType.Micro, color = Hafi.AccentStrong)
            Spacer(Modifier.height(6.dp))
        }
        Text(title, style = HafiType.Title)
        if (description != null) {
            Spacer(Modifier.height(6.dp))
            Text(description, style = HafiType.Body, color = Hafi.FgMuted)
        }
    }
}

/** A tinted note inside a page: something to know, not something that went wrong with a form. */
@Composable
fun InfoBanner(
    text: String,
    modifier: Modifier = Modifier,
    tone: Tone = Tone.Info,
    @DrawableRes icon: Int? = null,
    action: (@Composable () -> Unit)? = null,
) {
    val (fill, ink) = when (tone) {
        Tone.Accent -> Hafi.AccentSoft to Hafi.AccentStrong
        Tone.Info -> Hafi.InfoSoft to Hafi.Info
        Tone.Warn -> Hafi.WarnSoft to Hafi.Warn
        Tone.Danger -> Hafi.DangerSoft to Hafi.Danger
        Tone.Neutral -> Hafi.Surface2 to Hafi.FgMuted
    }
    // Each tone has its own shape of icon, so the tint is never the only signal.
    val shownIcon = icon ?: when (tone) {
        Tone.Accent -> R.drawable.ic_circle_check
        Tone.Warn, Tone.Danger -> R.drawable.ic_triangle_alert
        Tone.Info, Tone.Neutral -> R.drawable.ic_info
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(fill)
            .border(1.dp, ink.copy(alpha = 0.25f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HIcon(shownIcon, size = 18.dp, tint = ink, modifier = Modifier.padding(top = 2.dp))
            Text(text, style = HafiType.Label, color = if (tone == Tone.Neutral) Hafi.Fg else ink)
        }
        action?.invoke()
    }
}
