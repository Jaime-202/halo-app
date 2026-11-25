package com.lifedashboard.domain.insights

import com.lifedashboard.domain.history.DaySummary
import com.lifedashboard.domain.history.HistoryMath
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** Una conclusión sacada de tus datos. `strength` ordena de más a menos clara. */
data class Insight(val text: String, val strength: Float)

/**
 * Conclusiones automáticas cruzando sueño, comida, entreno, pasos y ánimo.
 * Compara dos grupos de días y solo habla cuando hay suficientes días en cada uno
 * y la diferencia es apreciable. Todo se calcula en el móvil.
 */
object InsightEngine {

    private const val MIN_DAYS = 4
    private val es = Locale("es", "ES")

    fun analyze(days: Map<LocalDate, DaySummary>, sleepTarget: Float, stepsTarget: Int): List<Insight> {
        val list = days.values.sortedBy { it.date }
        val out = mutableListOf<Insight>()

        // 1. Entrenar y el sueño de esa noche (el sueño se guarda en el día en que te despiertas)
        compare(
            list.filter { it.trained }.mapNotNull { days[it.date.plusDays(1)]?.sleepHours?.takeIf { s -> s > 0f } },
            list.filter { !it.trained && days[it.date.plusDays(1)] != null }.mapNotNull { days[it.date.plusDays(1)]?.sleepHours?.takeIf { s -> s > 0f } },
            minDiff = 0.33f,
        )?.let { (a, b) ->
            val min = ((a - b) * 60).roundToInt()
            out += Insight(
                if (min > 0) "Duermes $min min más las noches de los días que entrenas."
                else "Duermes ${-min} min menos las noches de los días que entrenas.",
                abs(a - b) / 1f,
            )
        }

        // 2. Dormir bien y el ánimo
        compare(
            list.filter { it.sleepHours >= sleepTarget - 0.25f }.mapNotNull { it.mood?.toFloat() },
            list.filter { it.sleepHours in 0.1f..(sleepTarget - 1f) }.mapNotNull { it.mood?.toFloat() },
            minDiff = 0.5f,
        )?.let { (a, b) ->
            out += Insight("Tu ánimo es ${fmt(a)} sobre 5 cuando duermes lo suficiente y ${fmt(b)} cuando duermes poco.", abs(a - b) / 2f)
        }

        // 3. Pasos y energía
        compare(
            list.filter { it.steps >= stepsTarget }.mapNotNull { it.energy?.toFloat() },
            list.filter { it.steps in 1 until (stepsTarget * 0.6f).toInt() }.mapNotNull { it.energy?.toFloat() },
            minDiff = 0.5f,
        )?.let { (a, b) ->
            out += Insight(
                if (a > b) "Te notas con más energía los días que llegas a ${"%,d".format(es, stepsTarget)} pasos (${fmt(a)} frente a ${fmt(b)})."
                else "Los días con muchos pasos acabas con menos energía (${fmt(a)} frente a ${fmt(b)}).",
                abs(a - b) / 2f,
            )
        }

        // 4. Entreno y ánimo
        compare(
            list.filter { it.trained }.mapNotNull { it.mood?.toFloat() },
            list.filter { !it.trained }.mapNotNull { it.mood?.toFloat() },
            minDiff = 0.4f,
        )?.let { (a, b) ->
            out += Insight(
                if (a > b) "Los días que entrenas tu ánimo sube a ${fmt(a)} sobre 5 (${fmt(b)} cuando no)."
                else "Los días de descanso te sientes algo mejor (${fmt(b)} frente a ${fmt(a)}).",
                abs(a - b) / 2f,
            )
        }

        // 5. Fin de semana y calorías
        val weekend = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
        compare(
            list.filter { it.date.dayOfWeek in weekend }.mapNotNull { it.balanceKcal },
            list.filter { it.date.dayOfWeek !in weekend }.mapNotNull { it.balanceKcal },
            minDiff = 200f,
            minA = 3,
        )?.let { (a, b) ->
            val diff = (a - b).roundToInt()
            out += Insight(
                if (diff > 0) "El fin de semana comes de media $diff kcal más respecto a lo que gastas que entre semana."
                else "El fin de semana tu balance es ${-diff} kcal más bajo que entre semana.",
                abs(a - b) / 600f,
            )
        }

        // 6. Dormir poco y comer más al día siguiente
        compare(
            list.filter { it.sleepHours in 0.1f..(sleepTarget - 1f) }.mapNotNull { it.intakeKcal.takeIf { k -> k > 0f } },
            list.filter { it.sleepHours >= sleepTarget - 0.25f }.mapNotNull { it.intakeKcal.takeIf { k -> k > 0f } },
            minDiff = 150f,
        )?.let { (a, b) ->
            if (a > b) out += Insight("Cuando duermes poco comes unas ${(a - b).roundToInt()} kcal más ese día.", (a - b) / 500f)
        }

        return out.sortedByDescending { it.strength }
    }

    private fun compare(a: List<Float>, b: List<Float>, minDiff: Float, minA: Int = MIN_DAYS): Pair<Float, Float>? {
        if (a.size < minA || b.size < MIN_DAYS) return null
        val ma = a.average().toFloat()
        val mb = b.average().toFloat()
        return if (abs(ma - mb) >= minDiff) ma to mb else null
    }

    private fun fmt(v: Float) = "%.1f".format(es, v)
}

/** Resumen de una semana (lunes a domingo) comparado con la anterior. */
data class WeeklySummary(
    val monday: LocalDate,
    val avgHalo: Int?,
    val avgBalance: Int?,
    val trainedDays: Int,
    val workoutMinutes: Int,
    val avgSleep: Float?,
    val avgSteps: Int?,
    val weightChange: Float?,
    val goodDays: Int,
    val haloDelta: Int?,           // frente a la semana anterior
) {
    fun notificationText(topInsight: String?): String = buildList {
        avgHalo?.let { add("Halo medio $it" + (haloDelta?.let { d -> if (d >= 0) " (+$d)" else " ($d)" } ?: "")) }
        avgBalance?.let { add("balance ${if (it > 0) "+" else ""}$it kcal/día") }
        add("$trainedDays entrenos ($workoutMinutes min)")
        weightChange?.let { add("peso ${if (it > 0) "+" else ""}${"%.1f".format(Locale("es", "ES"), it)} kg") }
        topInsight?.let { add(it) }
    }.joinToString(" · ")

    companion object {
        fun build(days: Map<LocalDate, DaySummary>, monday: LocalDate): WeeklySummary {
            fun week(m: LocalDate) = (0..6).mapNotNull { days[m.plusDays(it.toLong())] }
            val w = week(monday)
            val prev = week(monday.minusWeeks(1))
            val halo = w.mapNotNull { it.dayScore }.takeIf { it.isNotEmpty() }?.average()?.roundToInt()
            val prevHalo = prev.mapNotNull { it.dayScore }.takeIf { it.isNotEmpty() }?.average()?.roundToInt()
            val weights = w.mapNotNull { d -> d.weightKg?.let { d.date to it } }.sortedBy { it.first }
            return WeeklySummary(
                monday = monday,
                avgHalo = halo,
                avgBalance = w.mapNotNull { it.balanceKcal }.takeIf { it.isNotEmpty() }?.average()?.roundToInt(),
                trainedDays = w.count { it.trained },
                workoutMinutes = w.sumOf { it.workoutMinutes },
                avgSleep = w.map { it.sleepHours }.filter { it > 0f }.takeIf { it.isNotEmpty() }?.average()?.toFloat(),
                avgSteps = w.map { it.steps }.filter { it > 0 }.takeIf { it.isNotEmpty() }?.average()?.roundToInt(),
                weightChange = if (weights.size >= 2) weights.last().second - weights.first().second else null,
                goodDays = w.count { HistoryMath.isGoodDay(it) },
                haloDelta = if (halo != null && prevHalo != null) halo - prevHalo else null,
            )
        }
    }
}
