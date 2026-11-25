package com.lifedashboard.domain.history

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.IsoFields

/** Resumen de un día para el calendario. Se calcula a partir de los datos guardados. */
data class DaySummary(
    val date: LocalDate,
    val steps: Int = 0,
    val sleepHours: Float = 0f,
    val intakeKcal: Float = 0f,
    val expenditureKcal: Float = 0f,
    val trained: Boolean = false,
    val workoutMinutes: Int = 0,
    val remindersDone: Int = 0,
    val remindersTotal: Int = 0,
    val weightKg: Float? = null,
    val dayScore: Int? = null,
    val mood: Int? = null,          // 1..5
    val energy: Int? = null,        // 1..5
) {
    val balanceKcal: Float? get() = if (intakeKcal > 0f) intakeKcal - expenditureKcal else null
    val hasData: Boolean get() = dayScore != null || trained || weightKg != null || mood != null
}

/** Filtros del calendario. */
enum class CalendarMetric(val label: String) {
    HALO("Halo"),
    KCAL("Kcal"),
    PASOS("Pasos"),
    SUENO("Sueño"),
    ACTIVIDAD("Actividad"),
    RECORDATORIOS("Recordatorios"),
    ANIMO("Ánimo"),
}

data class StreakInfo(val current: Int, val longest: Int, val wildcardUsedThisWeek: Boolean)

object HistoryMath {

    /** Intensidad 0..1 del día para el filtro elegido; null = sin datos. */
    fun intensity(d: DaySummary, metric: CalendarMetric, stepsTarget: Int, sleepTarget: Float, intakeTarget: Float): Float? =
        when (metric) {
            CalendarMetric.HALO -> d.dayScore?.let { it / 100f }
            CalendarMetric.PASOS -> if (d.steps > 0 && stepsTarget > 0) (d.steps.toFloat() / stepsTarget).coerceIn(0f, 1f) else null
            CalendarMetric.SUENO -> if (d.sleepHours > 0f && sleepTarget > 0f) (d.sleepHours / sleepTarget).coerceIn(0f, 1f) else null
            CalendarMetric.ACTIVIDAD -> if (d.trained) 1f else null
            CalendarMetric.ANIMO -> d.mood?.let { (it - 1) / 4f }
            CalendarMetric.RECORDATORIOS -> if (d.remindersTotal > 0) d.remindersDone.toFloat() / d.remindersTotal else null
            CalendarMetric.KCAL -> if (d.intakeKcal > 0f && intakeTarget > 0f) {
                val dev = kotlin.math.abs(d.intakeKcal - intakeTarget) / intakeTarget
                1f - ((dev - 0.10f) / 0.40f).coerceIn(0f, 1f)
            } else null
        }

    /** Un día "cuenta" para la racha si su Halo Score llega a 60. */
    fun isGoodDay(d: DaySummary?): Boolean = (d?.dayScore ?: 0) >= 60

    /**
     * Racha con comodín: se permite un día flojo por semana (lunes a domingo) sin romperla.
     * El día de hoy solo suma si ya cumple; si no, no rompe la racha todavía.
     */
    fun streak(days: Map<LocalDate, DaySummary>, today: LocalDate): StreakInfo {
        fun weekKey(d: LocalDate) = d.get(IsoFields.WEEK_BASED_YEAR) * 100 + d.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)

        // Racha actual hacia atrás
        var current = 0
        val usedWeeks = mutableSetOf<Int>()
        var d = if (isGoodDay(days[today])) today else today.minusDays(1)
        while (true) {
            if (isGoodDay(days[d])) {
                current++
            } else {
                val wk = weekKey(d)
                if (wk in usedWeeks || days[d] == null && current == 0) break
                usedWeeks += wk
            }
            d = d.minusDays(1)
            if (current > 3660) break
        }

        // Racha más larga en el periodo disponible
        var longest = 0
        var run = 0
        val runWeeks = mutableSetOf<Int>()
        val first = days.keys.minOrNull() ?: today
        var x = first
        while (!x.isAfter(today)) {
            if (isGoodDay(days[x])) {
                run++
            } else if (x != today) {
                val wk = weekKey(x)
                if (wk in runWeeks) { run = 0; runWeeks.clear() } else runWeeks += wk
            }
            longest = maxOf(longest, run)
            x = x.plusDays(1)
        }
        return StreakInfo(current, maxOf(longest, current), weekKey(today) in usedWeeks)
    }

    fun mondayOf(d: LocalDate): LocalDate = d.with(DayOfWeek.MONDAY)
}
