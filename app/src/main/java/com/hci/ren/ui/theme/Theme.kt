package com.hci.ren.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = RenGreen,
    onPrimary = RenDarkOnPrimary,
    primaryContainer = RenGreenDark,
    onPrimaryContainer = RenGreenContainer,
    secondary = RenSageLight,
    onSecondary = RenDarkOnSecondary,
    secondaryContainer = RenSageContainerDark,
    onSecondaryContainer = RenDarkOnSecondaryContainer,
    tertiary = RenTaupeLight,
    onTertiary = RenDarkOnTertiary,
    tertiaryContainer = RenTaupeContainerDark,
    onTertiaryContainer = RenDarkOnTertiaryContainer,
    background = RenDarkBackground,
    onBackground = RenDarkOnBackground,
    surface = RenDarkSurface,
    onSurface = RenDarkOnBackground,
    surfaceVariant = RenDarkSurfaceVariant,
    onSurfaceVariant = RenDarkOnSurfaceVariant,
    surfaceTint = RenGreen,
    inverseSurface = RenDarkOnBackground,
    inverseOnSurface = RenDarkInverseOnSurface,
    inversePrimary = RenGreenDark,
    surfaceDim = RenDarkBackground,
    surfaceBright = RenDarkSurfaceBright,
    surfaceContainerLowest = RenDarkSurfaceContainerLowest,
    surfaceContainerLow = RenDarkSurface,
    surfaceContainer = RenDarkSurfaceContainer,
    surfaceContainerHigh = RenDarkSurfaceVariant,
    surfaceContainerHighest = RenDarkSurfaceContainerHighest,
    outline = RenDarkOutline,
    outlineVariant = RenDarkOutlineVariant
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
    inverseSurface = RenLightInverseSurface,
    inverseOnSurface = RenLightInverseOnSurface,
    inversePrimary = RenGreen,
    surfaceDim = RenLightSurfaceDim,
    surfaceBright = RenSurface,
    surfaceContainerLowest = RenSurface,
    surfaceContainerLow = RenBackground,
    surfaceContainer = RenSurfaceMuted,
    surfaceContainerHigh = RenLightSurfaceContainerHigh,
    surfaceContainerHighest = RenLightSurfaceContainerHighest,
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
