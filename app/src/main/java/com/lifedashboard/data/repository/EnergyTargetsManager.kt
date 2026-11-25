package com.lifedashboard.data.repository

import com.lifedashboard.data.local.dao.MealDao
import com.lifedashboard.data.local.dao.WeightDao
import com.lifedashboard.domain.energy.ActivityLevel
import com.lifedashboard.domain.energy.EnergyCalculator
import com.lifedashboard.domain.energy.EnergyProfile
import com.lifedashboard.domain.energy.EnergyTargets
import com.lifedashboard.domain.energy.Sex
import com.lifedashboard.domain.energy.WeightGoal
import kotlinx.coroutines.flow.first
import java.util.Calendar
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mantiene los objetivos de calorías y macros al día.
 * Se llama al terminar la bienvenida, al editar el perfil, al registrar un peso
 * y al abrir la app (para refrescar el TDEE adaptativo).
 */
@Singleton
class EnergyTargetsManager @Inject constructor(
    private val prefs: UserPreferencesRepository,
    private val weightDao: WeightDao,
    private val mealDao: MealDao,
) {

    fun profileOf(goals: UserGoals): EnergyProfile = EnergyProfile(
        sex = Sex.from(goals.sex),
        ageYears = goals.ageYears,
        heightCm = goals.heightCm,
        weightKg = goals.weightKg,
        activity = ActivityLevel.from(goals.activityLevel),
        goal = WeightGoal.from(goals.weightGoal),
    )

    /** Objetivos actuales sin escribir nada (para mostrar en pantalla). */
    fun targetsFor(goals: UserGoals): EnergyTargets =
        EnergyCalculator.targets(profileOf(goals), goals.adaptiveTdeeKcal.takeIf { it > 0f })

    /** Recalcula TDEE adaptativo + objetivos y los guarda. Devuelve los objetivos nuevos. */
    suspend fun recalculate(): EnergyTargets {
        val adaptive = computeAdaptiveTdee()
        prefs.updateAdaptiveTdee(adaptive ?: 0f)
        val goals = prefs.userGoals.first()
        val targets = targetsFor(goals)
        prefs.updateCalorieAndMacroTargets(
            calories = targets.caloriesKcal,
            protein = targets.proteinG,
            carbs = targets.carbsG,
            fat = targets.fatG,
        )
        return targets
    }

    private suspend fun computeAdaptiveTdee(): Float? {
        val now = System.currentTimeMillis()
        val since = now - TimeUnit.DAYS.toMillis(28)
        val weights = weightDao.getWeightsSince(since).map { dayIndex(it.dateMillis) to it.weightKg }
        // Hoy se excluye: el día aún no ha terminado
        val todayStart = startOfDay(now)
        val intakes = mealDao.getMealsBetween(since, todayStart - 1)
            .groupBy { dayIndex(it.dateMillis) }
            .map { (_, meals) -> meals.sumOf { it.calories.toDouble() }.toFloat() }
        return EnergyCalculator.adaptiveTdee(weights, intakes)
    }

    private fun dayIndex(millis: Long): Long = TimeUnit.MILLISECONDS.toDays(startOfDay(millis) + 12 * 3600_000L)

    private fun startOfDay(millis: Long): Long = Calendar.getInstance().apply {
        timeInMillis = millis
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}
