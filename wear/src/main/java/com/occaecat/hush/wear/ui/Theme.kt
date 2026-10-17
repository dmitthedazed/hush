package com.occaecat.hush.wear.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.MotionScheme
import androidx.wear.compose.material3.dynamicColorScheme

/** Hush's violet, used when the watch has no dynamic (watch face) colors. */
private val Fallback = ColorScheme(
    primary = Color(0xFFD0BCFF),
    primaryDim = Color(0xFFB8A3EC),
    primaryContainer = Color(0xFF4F378B),
    onPrimary = Color(0xFF381E72),
    onPrimaryContainer = Color(0xFFEADDFF),
    secondary = Color(0xFFCCC2DC),
    secondaryContainer = Color(0xFF4A4458),
    onSecondaryContainer = Color(0xFFE8DEF8),
    tertiary = Color(0xFFEFB8C8),
    tertiaryContainer = Color(0xFF633B48),
    onTertiaryContainer = Color(0xFFFFD8E4),
)

@Composable
fun HushWearTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    // Wear OS 6+ derives these from the watch face; follow it like the phone app follows the wallpaper.
    val colors = remember(context) { dynamicColorScheme(context) ?: Fallback }
    MaterialTheme(colorScheme = colors, motionScheme = MotionScheme.expressive(), content = content)
}
