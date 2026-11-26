package com.lifedashboard.presentation.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/**
 * Paleta de Halo. Cada token tiene valor oscuro y claro; el tema activo se guarda en
 * [HaloColors] como estado observable, así cualquier pantalla que lea un token se
 * redibuja sola al cambiar de modo.
 */
data class HaloPalette(
    val isLight: Boolean,
    val bg: Color,
    val panel: Color,
    val panelHigh: Color,
    val line: Color,
    val lineStrong: Color,
    val ion: Color,
    val ionSoft: Color,
    val ionPale: Color,
    val flux: Color,
    val ember: Color,
    val textHigh: Color,
    val textMute: Color,
    val textDim: Color,
    val gray200: Color,
    val gray300: Color,
    val gray400: Color,
    val gray600: Color,
    val sleep: Color,
    val success: Color,
    val warning: Color,
    val danger: Color,
    val onAccent: Color,   // texto/icono sobre el acento
)

val DarkPalette = HaloPalette(
    isLight = false,
    bg = Color(0xFF06080B), panel = Color(0xFF0D1116), panelHigh = Color(0xFF121821),
    line = Color(0xFF1B222B), lineStrong = Color(0xFF26303B),
    ion = Color(0xFF7CF5D8), ionSoft = Color(0xFFA6F9E6), ionPale = Color(0xFFD2FCF2),
    flux = Color(0xFF8AA7FF), ember = Color(0xFFFF9F7A),
    textHigh = Color(0xFFE9EEF2), textMute = Color(0xFF7D8995), textDim = Color(0xFF6B7784),
    gray200 = Color(0xFFD5DCE2), gray300 = Color(0xFFB3BDC6), gray400 = Color(0xFF95A1AC), gray600 = Color(0xFF5E6A76),
    sleep = Color(0xFFB4C6FF), success = Color(0xFF5EE6B0), warning = Color(0xFFFFC46B), danger = Color(0xFFFF8A8A),
    onAccent = Color(0xFF06080B),
)

val AmoledPalette = DarkPalette.copy(bg = Color.Black, panel = Color(0xFF0A0C0F))

/** Modo claro: fondo gris muy suave, superficies blancas y el acento oscurecido para leerse bien. */
val LightPalette = HaloPalette(
    isLight = true,
    bg = Color(0xFFF4F6F8), panel = Color(0xFFFFFFFF), panelHigh = Color(0xFFEAF0F3),
    line = Color(0xFFE1E6EB), lineStrong = Color(0xFFCBD3DB),
    ion = Color(0xFF087F67), ionSoft = Color(0xFF0C7F68), ionPale = Color(0xFF0B6B58),
    flux = Color(0xFF3F62E0), ember = Color(0xFFD9592E),
    textHigh = Color(0xFF0E141A), textMute = Color(0xFF5A6672), textDim = Color(0xFF78838E),
    gray200 = Color(0xFF26303A), gray300 = Color(0xFF3A4550), gray400 = Color(0xFF55616D), gray600 = Color(0xFF8A95A0),
    sleep = Color(0xFF5570DA), success = Color(0xFF0E9A6C), warning = Color(0xFFB7790A), danger = Color(0xFFD23F55),
    onAccent = Color(0xFFFFFFFF),
)

object HaloColors {
    var palette by mutableStateOf(DarkPalette)
}

private val p get() = HaloColors.palette

// ─── Tokens de Halo ─────────────────────────────────────────────────────────

/** Fondo de la app. */
val Obsidian: Color get() = p.bg
val Panel: Color get() = p.panel
val PanelHigh: Color get() = p.panelHigh
val Line: Color get() = p.line
val LineStrong: Color get() = p.lineStrong
val Ion: Color get() = p.ion
val IonSoft: Color get() = p.ionSoft
val IonPale: Color get() = p.ionPale
val Flux: Color get() = p.flux
val Ember: Color get() = p.ember
val TextHigh: Color get() = p.textHigh
val TextMute: Color get() = p.textMute
val TextDim: Color get() = p.textDim
/** Texto o icono encima de un fondo de acento (Ion). */
val OnAccent: Color get() = p.onAccent

// ─── Alias heredados (pantallas antiguas) ───────────────────────────────────

val Indigo900: Color get() = Obsidian
val Indigo800: Color get() = Panel
val Indigo700: Color get() = PanelHigh
val Indigo600: Color get() = LineStrong

val Violet400: Color get() = Ion
val Violet300: Color get() = IonSoft
val Violet200: Color get() = IonPale

val Cyan400: Color get() = Flux
val Teal400: Color get() = p.sleep
val Emerald400: Color get() = p.success
val Amber400: Color get() = p.warning
val Rose400: Color get() = p.danger

/** Texto principal (en oscuro es blanco; en claro, casi negro). */
val White: Color get() = p.textHigh
val Gray100: Color get() = p.textHigh
val Gray200: Color get() = p.gray200
val Gray300: Color get() = p.gray300
val Gray400: Color get() = p.gray400
val Gray500: Color get() = p.textMute
val Gray600: Color get() = p.gray600
val Gray700: Color get() = p.textDim
val Gray800: Color get() = p.lineStrong
val Gray900: Color get() = p.panel

val RingWorkout: Color get() = Ion
val RingSteps: Color get() = Flux
val RingSleep: Color get() = Teal400
val RingWater: Color get() = Emerald400
val RingTrack: Color get() = Line

val GradientStart: Color get() = Obsidian
val GradientEnd: Color get() = Obsidian
