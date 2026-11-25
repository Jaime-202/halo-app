package com.lifedashboard.presentation.theme

import androidx.compose.ui.graphics.Color

// ─── Halo · tokens de color ─────────────────────────────────────────────────
// Casi negro + un solo acento (Ion). Flux para datos secundarios.

val Obsidian       = Color(0xFF06080B)   // Fondo
val Panel      = Color(0xFF0D1116)   // Superficies
val PanelHigh  = Color(0xFF121821)   // Superficie elevada / indicador activo
val Line       = Color(0xFF1B222B)   // Bordes 1dp, pistas de anillo
val LineStrong = Color(0xFF26303B)   // Bordes destacados

val Ion        = Color(0xFF7CF5D8)   // Acento principal: progreso
val IonSoft    = Color(0xFFA6F9E6)
val IonPale    = Color(0xFFD2FCF2)
val Flux       = Color(0xFF8AA7FF)   // Secundario: datos
val Ember      = Color(0xFFFF9F7A)   // Grasa / alerta suave
val TextHigh   = Color(0xFFE9EEF2)
val TextMute   = Color(0xFF7D8995)
val TextDim    = Color(0xFF4A5560)

// ─── Alias heredados ─────────────────────────────────────────────────────────
// Las pantallas existentes usan estos nombres; ahora apuntan a la paleta Halo.
// Se irán sustituyendo por los tokens de arriba pantalla a pantalla.

val Indigo900    = Obsidian
val Indigo800    = Panel
val Indigo700    = PanelHigh
val Indigo600    = LineStrong

val Violet400    = Ion
val Violet300    = IonSoft
val Violet200    = IonPale

val Cyan400      = Flux
val Teal400      = Color(0xFFB4C6FF)   // Sueño: Flux claro
val Emerald400   = Color(0xFF5EE6B0)   // Éxito
val Amber400     = Color(0xFFFFC46B)   // Avisos / rachas
val Rose400      = Color(0xFFFF8A8A)   // Errores

// ─── Neutros (con ligero tinte frío hacia el acento) ─────────────────────────
val White        = Color(0xFFFFFFFF)
val Gray100      = TextHigh
val Gray200      = Color(0xFFD5DCE2)
val Gray300      = Color(0xFFB3BDC6)
val Gray400      = Color(0xFF95A1AC)
val Gray500      = TextMute
val Gray600      = Color(0xFF5E6A76)
val Gray700      = TextDim
val Gray800      = LineStrong
val Gray900      = Panel

// ─── Anillos de progreso ──────────────────────────────────────────────────────
val RingWorkout  = Ion
val RingSteps    = Flux
val RingSleep    = Teal400
val RingWater    = Emerald400
val RingTrack    = Line

// ─── Gradientes ───────────────────────────────────────────────────────────────
val GradientStart = Obsidian
val GradientEnd   = Obsidian
