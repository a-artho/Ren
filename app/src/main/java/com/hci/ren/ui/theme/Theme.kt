package com.hci.ren.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = RenGreen,
    onPrimary = Color(0xFF0D321F),
    primaryContainer = RenGreenDark,
    onPrimaryContainer = RenGreenContainer,
    secondary = RenSageLight,
    onSecondary = Color(0xFF183426),
    secondaryContainer = RenSageContainerDark,
    onSecondaryContainer = Color(0xFFD8E8DC),
    tertiary = RenTaupeLight,
    onTertiary = Color(0xFF372F26),
    tertiaryContainer = RenTaupeContainerDark,
    onTertiaryContainer = Color(0xFFF0E2D2),
    background = Color(0xFF111411),
    onBackground = Color(0xFFE2E4DF),
    surface = Color(0xFF171A17),
    onSurface = Color(0xFFE2E4DF),
    surfaceVariant = Color(0xFF252925),
    onSurfaceVariant = Color(0xFFC2C8C0),
    surfaceTint = RenGreen,
    inverseSurface = Color(0xFFE2E4DF),
    inverseOnSurface = Color(0xFF2D312D),
    inversePrimary = RenGreenDark,
    surfaceDim = Color(0xFF111411),
    surfaceBright = Color(0xFF373A37),
    surfaceContainerLowest = Color(0xFF0C0F0C),
    surfaceContainerLow = Color(0xFF171A17),
    surfaceContainer = Color(0xFF1B1E1B),
    surfaceContainerHigh = Color(0xFF252925),
    surfaceContainerHighest = Color(0xFF303430),
    outline = Color(0xFF8C938A),
    outlineVariant = Color(0xFF3F453F)
)

private val LightColorScheme = lightColorScheme(
    primary = RenGreenDark,
    onPrimary = RenSurface,
    primaryContainer = RenGreenContainer,
    onPrimaryContainer = RenText,
    secondary = RenSage,
    onSecondary = RenSurface,
    secondaryContainer = RenSageContainer,
    onSecondaryContainer = RenText,
    tertiary = RenTaupe,
    onTertiary = RenSurface,
    tertiaryContainer = RenTaupeContainer,
    onTertiaryContainer = RenText,
    background = RenBackground,
    onBackground = RenText,
    surface = RenSurface,
    onSurface = RenText,
    surfaceVariant = RenSurfaceMuted,
    onSurfaceVariant = RenText,
    surfaceTint = RenGreenDark,
    inverseSurface = Color(0xFF2E312D),
    inverseOnSurface = Color(0xFFF1F2EE),
    inversePrimary = RenGreen,
    surfaceDim = Color(0xFFDCDDD9),
    surfaceBright = RenSurface,
    surfaceContainerLowest = RenSurface,
    surfaceContainerLow = RenBackground,
    surfaceContainer = RenSurfaceMuted,
    surfaceContainerHigh = Color(0xFFEFF1ED),
    surfaceContainerHighest = Color(0xFFE8EAE6),
    outline = RenTextMuted,
    outlineVariant = RenBorder
)

@Composable
fun RenTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
