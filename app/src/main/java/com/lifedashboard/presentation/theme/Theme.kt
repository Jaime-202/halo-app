package com.lifedashboard.presentation.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Halo: tema principal, casi negro con acento Ion. */
private val HaloColorScheme = darkColorScheme(
    primary              = Ion,
    onPrimary            = Obsidian,
    primaryContainer     = PanelHigh,
    onPrimaryContainer   = Ion,

    secondary            = Flux,
    onSecondary          = Obsidian,
    secondaryContainer   = PanelHigh,
    onSecondaryContainer = Flux,

    tertiary             = Teal400,
    onTertiary           = Obsidian,
    tertiaryContainer    = PanelHigh,
    onTertiaryContainer  = Teal400,

    background           = Obsidian,
    onBackground         = TextHigh,

    surface              = Panel,
    onSurface            = TextHigh,
    surfaceVariant       = PanelHigh,
    onSurfaceVariant     = Gray300,
    surfaceContainer     = Panel,
    surfaceContainerHigh = PanelHigh,

    outline              = LineStrong,
    outlineVariant       = Line,

    error                = Rose400,
    onError              = Obsidian,

    inverseSurface       = TextHigh,
    inverseOnSurface     = Obsidian,
    inversePrimary       = PanelHigh,
)

/** Variante AMOLED: negro puro para pantallas OLED. */
private val AmoledColorScheme = HaloColorScheme.copy(
    background       = Color.Black,
    surface          = Color(0xFF0A0C0F),
    surfaceContainer = Color(0xFF0A0C0F),
)

@Composable
fun LifeDashboardTheme(
    themeMode: String = "OSCURO",
    content: @Composable () -> Unit
) {
    val colorScheme = when (themeMode) {
        "AMOLED" -> AmoledColorScheme
        else     -> HaloColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = AppTypography,
        content     = content
    )
}
