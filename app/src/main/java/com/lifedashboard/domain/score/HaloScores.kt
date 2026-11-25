package com.lifedashboard.domain.score

import kotlin.math.abs
import kotlin.math.roundToInt

/** Datos del día para calcular el Halo Score. Null = sin datos (no cuenta). */
data class DayInputs(
    val steps: Int?,
    val stepsTarget: Int,
    val sleepHours: Float?,
    val sleepTarget: Float,
    val intakeKcal: Float?,
    val intakeTargetKcal: Float,
    val remindersDone: Int,
    val remindersTotal: Int,
)

data class RecoveryInputs(
    val sleepHours: Float?,
    val sleepTarget: Float,
    val restingHr: Int?,
    val restingHrBaseline: Float?,   // media de días anteriores
    val hrvMs: Float?,
    val hrvBaseline: Float?,
)

enum class RecoveryLevel(val label: String, val advice: String) {
    ALTA("Alta", "Buen día para entrenar fuerte"),
    MEDIA("Normal", "Día normal: entrena como tenías previsto"),
    BAJA("Baja", "Mejor un día suave o de descanso"),
}

object HaloScores {

    /**
     * Halo Score (0–100): media ponderada de lo que haya ese día.
     * Pasos, sueño, comida (cerca del objetivo) y recordatorios valen lo mismo;
     * lo que no tenga datos no cuenta, así que un día sin registrar comida no baja la nota.
     */
    fun dayScore(d: DayInputs): Int? {
        val parts = mutableListOf<Float>()
        if (d.steps != null && d.steps > 0 && d.stepsTarget > 0) parts += (d.steps.toFloat() / d.stepsTarget).coerceAtMost(1f)
        if (d.sleepHours != null && d.sleepHours > 0f && d.sleepTarget > 0f) parts += (d.sleepHours / d.sleepTarget).coerceAtMost(1f)
        if (d.intakeKcal != null && d.intakeKcal > 0f && d.intakeTargetKcal > 0f) {
            // 100 % si estás dentro de ±10 % del objetivo; baja de forma lineal hasta 0 al ±50 %
            val dev = abs(d.intakeKcal - d.intakeTargetKcal) / d.intakeTargetKcal
            parts += (1f - ((dev - 0.10f) / 0.40f).coerceIn(0f, 1f))
        }
        if (d.remindersTotal > 0) parts += d.remindersDone.toFloat() / d.remindersTotal
        parts.removeAll { it.isNaN() || it.isInfinite() }
        if (parts.isEmpty()) return null
        return (parts.average() * 100).roundToInt().coerceIn(0, 100)
    }

    /**
     * Recuperación (0–100) con sueño (40 %), variabilidad cardiaca (35 %) y pulso en reposo (25 %),
     * comparando HRV y pulso con tu propia media. Si falta algún dato, se reparte su peso.
     */
    fun recovery(r: RecoveryInputs): Int? {
        val parts = mutableListOf<Pair<Float, Float>>() // (valor 0..1, peso)
        if (r.sleepHours != null && r.sleepHours > 0f && r.sleepTarget > 0f) {
            parts += (r.sleepHours / r.sleepTarget).coerceIn(0f, 1f) to 0.40f
        }
        if (r.hrvMs != null && r.hrvMs > 0f && r.hrvBaseline != null && r.hrvBaseline > 0f) {
            // ±20 % sobre tu media = extremo de la escala
            val rel = ((r.hrvMs / r.hrvBaseline - 1f) / 0.20f).coerceIn(-1f, 1f)
            parts += (0.5f + 0.5f * rel) to 0.35f
        }
        if (r.restingHr != null && r.restingHr > 0 && r.restingHrBaseline != null && r.restingHrBaseline > 0f) {
            // Pulso un 8 % por debajo de tu media = máximo; un 8 % por encima = mínimo
            val rel = ((r.restingHrBaseline - r.restingHr) / (r.restingHrBaseline * 0.08f)).coerceIn(-1f, 1f)
            parts += (0.5f + 0.5f * rel) to 0.25f
        }
        parts.removeAll { it.first.isNaN() || it.first.isInfinite() }
        if (parts.isEmpty()) return null
        val total = parts.sumOf { it.second.toDouble() }
        val value = parts.sumOf { (it.first * it.second).toDouble() } / total
        return (value * 100).roundToInt().coerceIn(0, 100)
    }

    fun recoveryLevel(score: Int): RecoveryLevel = when {
        score >= 75 -> RecoveryLevel.ALTA
        score >= 50 -> RecoveryLevel.MEDIA
        else -> RecoveryLevel.BAJA
    }
}
