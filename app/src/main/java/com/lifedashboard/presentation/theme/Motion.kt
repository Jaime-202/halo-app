package com.lifedashboard.presentation.theme

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/**
 * Movimiento de Halo: mismas duraciones y curvas en toda la app.
 * - Rápido: respuestas a un toque (chips, iconos).
 * - Medio: cambios de pantalla y apariciones.
 * - Lento: números y anillos que se llenan.
 */
object HaloMotion {
    const val FAST = 150
    const val MEDIUM = 280
    const val SLOW = 900

    /** Curva "enfatizada" de Material 3: arranca rápido y frena suave. */
    val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    fun <T> enter(): FiniteAnimationSpec<T> = tween(MEDIUM, easing = EmphasizedDecelerate)
    fun <T> exit(): FiniteAnimationSpec<T> = tween(FAST, easing = EmphasizedAccelerate)
    fun <T> fill(): FiniteAnimationSpec<T> = tween(SLOW, easing = Emphasized)
    fun <T> bouncy(): FiniteAnimationSpec<T> = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)
}

/**
 * Vibración de Halo: siempre la misma para la misma acción.
 * tick = cambiar de pestaña o elegir algo; confirm = añadir o completar;
 * success = llegar a un objetivo; reject = borrar.
 */
class HaloHaptics(private val view: View) {
    fun tick() { view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) }
    fun confirm() {
        view.performHapticFeedback(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY)
    }
    fun success() {
        view.performHapticFeedback(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.LONG_PRESS)
        view.postDelayed({ view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) }, 90)
    }
    fun reject() {
        view.performHapticFeedback(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS)
    }
}

@Composable
fun rememberHaloHaptics(): HaloHaptics {
    val view = LocalView.current
    return remember(view) { HaloHaptics(view) }
}
