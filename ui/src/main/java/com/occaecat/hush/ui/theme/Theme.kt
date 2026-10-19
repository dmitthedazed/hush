package com.occaecat.hush.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/*
 * Green fallback scheme (used when the device has no wallpaper colours, i.e. before Android 12).
 * The dark roles are the redesign's spec values; light roles come from the same tonal palettes.
 */
private val GreenDark = darkColorScheme(
    primary = Color(0xFF96D5A9),
    onPrimary = Color(0xFF00391C),
    primaryContainer = Color(0xFF12512E),
    onPrimaryContainer = Color(0xFFB1F1C5),
    inversePrimary = Color(0xFF2E6A45),
    secondary = Color(0xFFB7CBBD),
    onSecondary = Color(0xFF223529),
    secondaryContainer = Color(0xFF3A4B3F),
    onSecondaryContainer = Color(0xFFD3E8D8),
    tertiary = Color(0xFFA2CED6),
    onTertiary = Color(0xFF02363C),
    tertiaryContainer = Color(0xFF1E4D54),
    onTertiaryContainer = Color(0xFFBBEBF2),
    background = Color(0xFF101411),
    onBackground = Color(0xFFDEE4E0),
    surface = Color(0xFF101411),
    onSurface = Color(0xFFDEE4E0),
    surfaceVariant = Color(0xFF3E4941),
    onSurfaceVariant = Color(0xFFBDCAC0),
    outline = Color(0xFF87948B),
    outlineVariant = Color(0xFF3E4941),
    inverseSurface = Color(0xFFDEE4E0),
    inverseOnSurface = Color(0xFF2D312E),
    surfaceContainerLowest = Color(0xFF0B0F0C),
    surfaceContainerLow = Color(0xFF181C1A),
    surfaceContainer = Color(0xFF1C211E),
    surfaceContainerHigh = Color(0xFF272B28),
    surfaceContainerHighest = Color(0xFF313633),
    surfaceDim = Color(0xFF101411),
    surfaceBright = Color(0xFF363A37),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
)

private val GreenLight = lightColorScheme(
    primary = Color(0xFF2E6A45),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFB1F1C5),
    onPrimaryContainer = Color(0xFF12512E),
    inversePrimary = Color(0xFF96D5A9),
    secondary = Color(0xFF4F6354),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD3E8D8),
    onSecondaryContainer = Color(0xFF3A4B3F),
    tertiary = Color(0xFF3A656C),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFBBEBF2),
    onTertiaryContainer = Color(0xFF1E4D54),
    background = Color(0xFFF6FBF5),
    onBackground = Color(0xFF171D19),
    surface = Color(0xFFF6FBF5),
    onSurface = Color(0xFF171D19),
    surfaceVariant = Color(0xFFDCE5DC),
    onSurfaceVariant = Color(0xFF414942),
    outline = Color(0xFF717971),
    outlineVariant = Color(0xFFC0C9C0),
    inverseSurface = Color(0xFF2D312E),
    inverseOnSurface = Color(0xFFEDF2EC),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF0F5EF),
    surfaceContainer = Color(0xFFEAEFE9),
    surfaceContainerHigh = Color(0xFFE5EAE4),
    surfaceContainerHighest = Color(0xFFDFE4DE),
    surfaceDim = Color(0xFFD6DBD6),
    surfaceBright = Color(0xFFF6FBF5),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HushTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> GreenDark
        else -> GreenLight
    }
    // Expressive: spring-based component motion; shapes and type come from the M3 defaults.
    MaterialExpressiveTheme(colorScheme = colorScheme, motionScheme = MotionScheme.expressive(), content = content)
}
