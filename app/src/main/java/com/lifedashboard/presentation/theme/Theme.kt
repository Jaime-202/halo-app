package com.lifedashboard.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember

/** Elige la paleta según la preferencia: "CLARO", "SISTEMA", "AMOLED" o oscuro (por defecto). */
@Composable
fun paletteFor(themeMode: String): HaloPalette = when (themeMode) {
    "CLARO" -> LightPalette
    "SISTEMA" -> if (isSystemInDarkTheme()) DarkPalette else LightPalette
    "AMOLED" -> AmoledPalette
    else -> DarkPalette
}

private fun schemeFor(p: HaloPalette): ColorScheme {
    val base = if (p.isLight) lightColorScheme() else darkColorScheme()
    return base.copy(
        primary = p.ion, onPrimary = p.onAccent,
        primaryContainer = p.panelHigh, onPrimaryContainer = p.ion,
        secondary = p.flux, onSecondary = p.onAccent,
        secondaryContainer = p.panelHigh, onSecondaryContainer = p.flux,
        tertiary = p.sleep, onTertiary = p.onAccent,
        tertiaryContainer = p.panelHigh, onTertiaryContainer = p.sleep,
        background = p.bg, onBackground = p.textHigh,
        surface = p.panel, onSurface = p.textHigh,
        surfaceVariant = p.panelHigh, onSurfaceVariant = p.gray300,
        surfaceContainer = p.panel, surfaceContainerHigh = p.panelHigh,
        surfaceContainerLow = p.panel, surfaceContainerLowest = p.bg, surfaceContainerHighest = p.panelHigh,
        outline = p.lineStrong, outlineVariant = p.line,
        error = p.danger, onError = p.onAccent,
        inverseSurface = p.textHigh, inverseOnSurface = p.bg, inversePrimary = p.panelHigh,
    )
}

@Composable
fun LifeDashboardTheme(
    themeMode: String = "OSCURO",
    content: @Composable () -> Unit
) {
    val palette = paletteFor(themeMode)
    // Los tokens de color leen esta paleta; al cambiar, se redibuja todo lo que los usa
    SideEffect { if (HaloColors.palette != palette) HaloColors.palette = palette }
    val scheme = remember(palette) { schemeFor(palette) }

    MaterialTheme(
        colorScheme = scheme,
        typography  = AppTypography,
        content     = content
    )
}
