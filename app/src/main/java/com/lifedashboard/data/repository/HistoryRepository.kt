package com.lifedashboard.data.repository

import com.lifedashboard.data.local.dao.HealthDao
import com.lifedashboard.data.local.dao.MealDao
import com.lifedashboard.data.local.dao.ReminderDao
import com.lifedashboard.data.local.dao.WeightDao
import com.lifedashboard.data.local.dao.WorkoutDao
import com.lifedashboard.data.local.entity.ReminderType
import com.lifedashboard.domain.energy.EnergyCalculator
import com.lifedashboard.domain.energy.Sex
import com.lifedashboard.domain.history.DaySummary
import com.lifedashboard.domain.score.DayInputs
import com.lifedashboard.domain.score.HaloScores
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Historial día a día para el calendario: se calcula al vuelo a partir de las tablas
 * (salud, comidas, entrenos, recordatorios y pesos), así siempre coincide con los datos.
 */
@Singleton
class HistoryRepository @Inject constructor(
    private val healthDao: HealthDao,
    private val mealDao: MealDao,
    private val workoutDao: WorkoutDao,
    private val reminderDao: ReminderDao,
    private val weightDao: WeightDao,
    private val prefs: UserPreferencesRepository,
) {
    private val zone = ZoneId.systemDefault()
    private fun millis(d: LocalDate) = d.atStartOfDay(zone).toInstant().toEpochMilli()
    private fun date(m: Long) = Instant.ofEpochMilli(m).atZone(zone).toLocalDate()

    fun observe(from: LocalDate, to: LocalDate): Flow<Map<LocalDate, DaySummary>> {
        val fromMs = millis(from)
        val toMs = millis(to.plusDays(1)) - 1
        val base = combine(
            healthDao.getHealthRange(fromMs, toMs),
            mealDao.observeMealsForDay(fromMs, toMs),
            workoutDao.getSessionsInRange(fromMs, toMs),
            weightDao.observeAllWeights(),
        ) { health, meals, sessions, weights -> Quad(health, meals, sessions, weights) }

        return combine(
            base,
            reminderDao.observeLogsInRange(fromMs, toMs),
            reminderDao.observeEnabled(),
            prefs.userGoals,
        ) { q, logs, reminders, goals ->
            val healthBy = q.a.associateBy { date(it.dateMillis) }
            val mealsBy = q.b.groupBy { date(it.dateMillis) }
            val sessionsBy = q.c.filter { it.type != "REST" }.groupBy { date(it.dateMillis) }
            val weightBy = q.d.groupBy { date(it.dateMillis) }.mapValues { (_, l) -> l.maxBy { it.dateMillis }.weightKg }
            val doneBy = logs.filter { it.isCompleted }.groupBy { date(it.dateMillis) }.mapValues { it.value.size }
            val bmr = EnergyCalculator.bmr(Sex.from(goals.sex), goals.weightKg, goals.heightCm, goals.ageYears)
            val tdee = energyTdee(goals, bmr)
            val today = LocalDate.now()

            generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(minOf(to, today)) }.associateWith { d ->
                val h = healthBy[d]
                val intake = mealsBy[d].orEmpty().sumOf { it.calories.toDouble() }.toFloat()
                val active = h?.activeCalories ?: 0f
                val fraction = if (d == today) (java.time.LocalTime.now().toSecondOfDay() / 86_400f).coerceIn(0.05f, 1f) else 1f
                val expenditure = EnergyCalculator.expenditureForDay(bmr, tdee, active, fraction)
                val total = reminders.count { r ->
                    !date(r.createdAt).isAfter(d) && when (r.type) {
                        ReminderType.SEMANAL -> r.daysOfWeek.split(",").mapNotNull { it.trim().toIntOrNull() }
                            .let { it.isEmpty() || d.dayOfWeek.value in it }
                        else -> true
                    }
                }
                val done = (doneBy[d] ?: 0).coerceAtMost(total)
                val sessions = sessionsBy[d].orEmpty()
                DaySummary(
                    date = d,
                    steps = h?.steps ?: 0,
                    sleepHours = h?.sleepHours ?: 0f,
                    intakeKcal = intake,
                    expenditureKcal = expenditure,
                    trained = sessions.isNotEmpty(),
                    workoutMinutes = sessions.sumOf { it.durationMinutes },
                    remindersDone = done,
                    remindersTotal = total,
                    weightKg = weightBy[d],
                    dayScore = HaloScores.dayScore(
                        DayInputs(
                            steps = h?.steps, stepsTarget = goals.stepsTarget,
                            sleepHours = h?.sleepHours, sleepTarget = goals.sleepTargetHours,
                            intakeKcal = intake.takeIf { it > 0f }, intakeTargetKcal = goals.caloriesTarget,
                            remindersDone = done, remindersTotal = total,
                        )
                    ),
                )
            }
        }
    }

    private fun energyTdee(goals: UserGoals, bmr: Float): Float =
        goals.adaptiveTdeeKcal.takeIf { it > bmr }
            ?: bmr * com.lifedashboard.domain.energy.ActivityLevel.from(goals.activityLevel).factor

    private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
}
