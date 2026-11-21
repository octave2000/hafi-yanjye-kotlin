package com.hafiyanjye.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.hafiyanjye.app.R

val Poppins = FontFamily(
    Font(R.font.poppins_regular, FontWeight.Normal),
    Font(R.font.poppins_medium, FontWeight.Medium),
    Font(R.font.poppins_semibold, FontWeight.SemiBold),
    Font(R.font.poppins_bold, FontWeight.Bold),
)

private val Centered = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

private fun style(size: Int, line: Int, weight: FontWeight, tracking: Double = 0.0) = TextStyle(
    fontFamily = Poppins,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = tracking.em,
    lineHeightStyle = Centered,
    color = Hafi.Fg,
)

/** The web's type scale (display / title / heading / body / caption / micro). */
object HafiType {
    val Display = style(34, 40, FontWeight.SemiBold, -0.025)
    val Title = style(28, 34, FontWeight.SemiBold, -0.015)
    val Heading = style(20, 26, FontWeight.SemiBold, -0.012)
    val Subheading = style(16, 22, FontWeight.SemiBold)
    val Body = style(15, 23, FontWeight.Normal)
    val BodyStrong = style(15, 23, FontWeight.SemiBold)
    val Label = style(14, 20, FontWeight.Medium)
    val Caption = style(13, 18, FontWeight.Normal)
    /** Upper-case eyebrows and badges. */
    val Micro = style(11, 14, FontWeight.Bold, 0.06)
    val Button = style(15, 20, FontWeight.SemiBold)
}

internal val MaterialType = Typography(
    displaySmall = HafiType.Display,
    headlineMedium = HafiType.Title,
    titleLarge = HafiType.Heading,
    titleMedium = HafiType.Subheading,
    bodyLarge = HafiType.Body,
    bodyMedium = HafiType.Body,
    bodySmall = HafiType.Caption,
    labelLarge = HafiType.Button,
    labelMedium = HafiType.Label,
    labelSmall = HafiType.Micro,
)
