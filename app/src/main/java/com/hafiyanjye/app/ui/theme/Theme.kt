package com.hafiyanjye.app.ui.theme

import android.provider.Settings
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val Colors = lightColorScheme(
    primary = Hafi.Accent,
    onPrimary = Hafi.AccentFg,
    primaryContainer = Hafi.AccentSoft,
    onPrimaryContainer = Hafi.AccentStrong,
    secondary = Hafi.AccentStrong,
    onSecondary = Hafi.AccentFg,
    secondaryContainer = Hafi.AccentSoft,
    onSecondaryContainer = Hafi.AccentStrong,
    tertiary = Hafi.Info,
    background = Hafi.Canvas,
    onBackground = Hafi.Fg,
    surface = Hafi.Surface,
    onSurface = Hafi.Fg,
    surfaceVariant = Hafi.Surface2,
    onSurfaceVariant = Hafi.FgMuted,
    surfaceContainerLowest = Hafi.Surface,
    surfaceContainerLow = Hafi.Surface,
    surfaceContainer = Hafi.Surface,
    surfaceContainerHigh = Hafi.Surface2,
    surfaceContainerHighest = Hafi.Surface3,
    outline = Hafi.LineStrong,
    outlineVariant = Hafi.Line,
    error = Hafi.Danger,
    onError = Hafi.AccentFg,
    errorContainer = Hafi.DangerSoft,
    onErrorContainer = Hafi.Danger,
    scrim = Hafi.Ink,
)

/** Square design: every corner is sharp, as on the web. */
private val Square = RoundedCornerShape(0.dp)
private val SquareShapes = Shapes(Square, Square, Square, Square, Square)

@Composable
fun HafiTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = Colors,
        typography = MaterialType,
        shapes = SquareShapes,
        content = content,
    )
}

/** True when the person turned animations off in the phone's settings. */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}
