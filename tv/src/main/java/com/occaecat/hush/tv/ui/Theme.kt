package com.occaecat.hush.tv.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

/*
 * TV is dark-only and has no wallpaper colours, so this is the phone app's Sony night palette
 * (Sound Connect 13.3.0 res/values-night/colors.xml) mapped onto tv-material's colour roles.
 * tv-material has no surfaceContainer* roles: surfaceVariant is the resting card colour and focused
 * surfaces invert to inverseSurface, which is how the TV focus system signals "this one".
 */
private val SonyNight = darkColorScheme(
    primary = Color(0xFFC5D4FF),
    onPrimary = Color(0xFF00277F),
    primaryContainer = Color(0xFF1B378F),
    onPrimaryContainer = Color(0xFFDCE1FF),
    inversePrimary = Color(0xFF364FA8),
    secondary = Color(0xFFC2C5DD),
    onSecondary = Color(0xFF2B3042),
    secondaryContainer = Color(0xFF424659),
    onSecondaryContainer = Color(0xFFDEE1F9),
    tertiary = Color(0xFF7AD7BC),
    onTertiary = Color(0xFF00382C),
    tertiaryContainer = Color(0xFF005141),
    onTertiaryContainer = Color(0xFF97F4D7),
    background = Color(0xFF0E1010),
    onBackground = Color(0xFFE1E3E3),
    surface = Color(0xFF0E1010),
    onSurface = Color(0xFFE1E3E3),
    surfaceVariant = Color(0xFF1D2021),
    onSurfaceVariant = Color(0xFFC4C7C7),
    inverseSurface = Color(0xFFE1E3E3),
    inverseOnSurface = Color(0xFF191C1D),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    border = Color(0xFF8E9192),
    borderVariant = Color(0xFF444748),
)

@Composable
fun HushTvTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = SonyNight, content = content)
}

/**
 * The TV layout grid on the 960×540 dp canvas (design/ui/tv/guides/styles/layouts):
 * 48 dp side / 27 dp top-bottom overscan-safe margins, 20 dp gutters.
 */
object Tv {
    val SideMargin = 48.dp
    val TopMargin = 27.dp
    val Gutter = 20.dp
    val Gap = 12.dp
    val Small = 8.dp
}
