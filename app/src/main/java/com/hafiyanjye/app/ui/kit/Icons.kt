package com.hafiyanjye.app.ui.kit

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hafiyanjye.app.R

/**
 * One of the app's line icons (res/drawable/ic_*.xml, generated from Lucide
 * by tools/icons.mjs). Pass [label] only when the icon carries meaning that
 * no text next to it says; decorative icons stay silent for screen readers.
 */
@Composable
fun HIcon(
    @DrawableRes id: Int,
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
    tint: Color = LocalContentColor.current,
    label: String? = null,
) {
    Icon(
        painter = painterResource(id),
        contentDescription = label,
        modifier = modifier.size(size),
        tint = tint,
    )
}

/** Lucide names services refer to (web: components/search/category-icon.tsx). Unknown names → wrench. */
@DrawableRes
fun categoryIcon(name: String?): Int = when (name) {
    "Baby" -> R.drawable.ic_baby
    "Bike" -> R.drawable.ic_bike
    "BrickWall" -> R.drawable.ic_brick_wall
    "Briefcase" -> R.drawable.ic_briefcase
    "Bug" -> R.drawable.ic_bug
    "Cake" -> R.drawable.ic_cake
    "Calculator" -> R.drawable.ic_calculator
    "Camera" -> R.drawable.ic_camera
    "Car" -> R.drawable.ic_car
    "ChefHat" -> R.drawable.ic_chef_hat
    "Dog" -> R.drawable.ic_dog
    "Droplets" -> R.drawable.ic_droplets
    "Dumbbell" -> R.drawable.ic_dumbbell
    "Flame" -> R.drawable.ic_flame
    "GraduationCap" -> R.drawable.ic_graduation_cap
    "Hammer" -> R.drawable.ic_hammer
    "HeartPulse" -> R.drawable.ic_heart_pulse
    "KeyRound" -> R.drawable.ic_key_round
    "Languages" -> R.drawable.ic_languages
    "Laptop" -> R.drawable.ic_laptop
    "LayoutGrid" -> R.drawable.ic_layout_grid
    "Music" -> R.drawable.ic_music
    "Package" -> R.drawable.ic_package
    "Paintbrush" -> R.drawable.ic_paintbrush
    "Palette" -> R.drawable.ic_palette
    "Printer" -> R.drawable.ic_printer
    "Scale" -> R.drawable.ic_scale
    "Scissors" -> R.drawable.ic_scissors
    "Shield" -> R.drawable.ic_shield
    "Shirt" -> R.drawable.ic_shirt
    "Smartphone" -> R.drawable.ic_smartphone
    "Snowflake" -> R.drawable.ic_snowflake
    "Sofa" -> R.drawable.ic_sofa
    "Sparkles" -> R.drawable.ic_sparkles
    "Sprout" -> R.drawable.ic_sprout
    "Truck" -> R.drawable.ic_truck
    "Tv" -> R.drawable.ic_tv
    "Waves" -> R.drawable.ic_waves
    "Zap" -> R.drawable.ic_zap
    else -> R.drawable.ic_wrench
}
