package com.hafiyanjye.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Hafi Yanjye: bright white + confident green (web/src/index.css). Screens use
 * these names and never raw values. Light only; there is no dark mode.
 */
object Hafi {
    val Canvas = Color(0xFFF4F8F5)
    val Surface = Color(0xFFFFFFFF)
    val Surface2 = Color(0xFFEDF3EF)
    val Surface3 = Color(0xFFE1EBE4)
    val Line = Color(0xFFDFE8E2)
    val LineStrong = Color(0xFFC3D1C8)

    val Fg = Color(0xFF0D1F15)
    val FgMuted = Color(0xFF4F6357)
    /** Darker than the web's #7F9186 so small text on white still passes AA. */
    val FgSubtle = Color(0xFF64776B)

    /** Brand green. White text on it passes AA (5.4:1). */
    val Accent = Color(0xFF0B7A40)
    val AccentStrong = Color(0xFF075F31)
    val AccentFg = Color(0xFFFFFFFF)
    val AccentSoft = Color(0xFFE2F4E9)

    /** Deep green-black for inverted bands. */
    val Ink = Color(0xFF0A1A11)
    val InkFg = Color(0xFFF0F7F2)
    val InkMuted = Color(0xFF93AB9C)
    val HeroGreen = Color(0xFF115E38)
    val Lime = Color(0xFFC6F45C)

    val Info = Color(0xFF2563C9)
    val InfoSoft = Color(0xFFE4EDFC)
    val Warn = Color(0xFF8F5600)
    val WarnSoft = Color(0xFFFDF1DC)
    val Danger = Color(0xFFC42B2F)
    val DangerStrong = Color(0xFFB02A2E)
    val DangerSoft = Color(0xFFFDE8E8)

    val Star = Color(0xFFFBBC04)
}
