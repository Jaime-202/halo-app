package com.lifedashboard.domain.energy

import kotlin.math.roundToInt

/** Nivel de actividad diaria (sin contar la dieta). */
enum class ActivityLevel(val label: String, val description: String, val factor: Float) {
    SEDENTARIO("Sedentario", "Trabajo sentado, poco movimiento", 1.2f),
    LIGERO("Ligero", "Entreno 1–3 días por semana", 1.375f),
    MODERADO("Moderado", "Entreno 3–5 días por semana", 1.55f),
    ALTO("Alto", "Entreno 6–7 días por semana", 1.725f),
    MUY_ALTO("Muy alto", "Trabajo físico + entreno diario", 1.9f);

    companion object {
        fun from(value: String?): ActivityLevel =
            entries.firstOrNull { it.name == value } ?: MODERADO
    }
}

/** Objetivo de peso expresado como cambio semanal. */
enum class WeightGoal(val label: String, val weeklyKg: Float) {
    PERDER_RAPIDO("Perder 0,5 kg/semana", -0.5f),
    PERDER("Perder 0,25 kg/semana", -0.25f),
    MANTENER("Mantener", 0f),
    GANAR("Ganar músculo (+0,25 kg/semana)", 0.25f);

    companion object {
        fun from(value: String?): WeightGoal =
            entries.firstOrNull { it.name == value } ?: MANTENER
    }
}

enum class Sex { HOMBRE, MUJER;
    companion object { fun from(value: String?) = if (value == "MUJER") MUJER else HOMBRE }
}

data class EnergyProfile(
    val sex: Sex,
    val ageYears: Int,
    val heightCm: Int,
    val weightKg: Float,
    val activity: ActivityLevel,
    val goal: WeightGoal,
)

data class EnergyTargets(
    val bmrKcal: Float,
    val tdeeKcal: Float,          // Gasto diario usado (fórmula o adaptativo)
    val formulaTdeeKcal: Float,   // Gasto según fórmula
    val isAdaptive: Boolean,
    val caloriesKcal: Float,      // Objetivo de ingesta
    val proteinG: Float,
    val carbsG: Float,
    val fatG: Float,
)

enum class BalanceState { DEFICIT, MANTENIMIENTO, SUPERAVIT }

/**
 * Cálculos de energía de Halo.
 *
 * - Metabolismo basal: Mifflin-St Jeor.
 * - Gasto diario (TDEE): basal × factor de actividad, o el TDEE adaptativo si hay
 *   suficientes datos reales de peso e ingesta.
 * - Objetivo: TDEE + (cambio semanal × 7.700 kcal/kg ÷ 7), nunca por debajo del basal.
 */
object EnergyCalculator {

    const val KCAL_PER_KG = 7700f

    fun bmr(sex: Sex, weightKg: Float, heightCm: Int, ageYears: Int): Float {
        val base = 10f * weightKg + 6.25f * heightCm - 5f * ageYears
        return base + if (sex == Sex.HOMBRE) 5f else -161f
    }

    fun targets(profile: EnergyProfile, adaptiveTdeeKcal: Float? = null): EnergyTargets {
        val w = profile.weightKg.coerceIn(30f, 300f)
        val h = profile.heightCm.coerceIn(120, 230)
        val age = profile.ageYears.coerceIn(14, 100)
        val bmr = bmr(profile.sex, w, h, age)
        val formulaTdee = bmr * profile.activity.factor
        val useAdaptive = adaptiveTdeeKcal != null && adaptiveTdeeKcal > bmr
        val tdee = if (useAdaptive) adaptiveTdeeKcal!! else formulaTdee

        val dailyDelta = profile.goal.weeklyKg * KCAL_PER_KG / 7f
        val calories = (tdee + dailyDelta).coerceAtLeast(bmr).roundTo(10)

        val proteinPerKg = when {
            profile.goal.weeklyKg < 0f -> 2.0f
            profile.goal.weeklyKg > 0f -> 1.8f
            else -> 1.6f
        }
        val protein = (w * proteinPerKg).roundTo(5)
        val fat = (w * 0.8f).roundTo(5)
        val carbs = ((calories - protein * 4f - fat * 9f) / 4f).coerceAtLeast(50f).roundTo(5)

        return EnergyTargets(
            bmrKcal = bmr,
            tdeeKcal = tdee,
            formulaTdeeKcal = formulaTdee,
            isAdaptive = useAdaptive,
            caloriesKcal = calories,
            proteinG = protein,
            carbsG = carbs,
            fatG = fat,
        )
    }

    /**
     * Gasto real del día: basal (proporcional a la hora si es hoy) + calorías activas
     * de Health Connect. Si no hay datos de actividad, se usa el TDEE estimado.
     */
    fun expenditureForDay(bmrKcal: Float, tdeeKcal: Float, activeKcal: Float, dayFraction: Float = 1f): Float {
        val fraction = dayFraction.coerceIn(0f, 1f)
        return if (activeKcal > 0f) bmrKcal * fraction + activeKcal else tdeeKcal * fraction
    }

    /** Balance = ingesta − gasto. Negativo = déficit. */
    fun balance(intakeKcal: Float, expenditureKcal: Float): Float = intakeKcal - expenditureKcal

    fun balanceState(balanceKcal: Float): BalanceState = when {
        balanceKcal <= -150f -> BalanceState.DEFICIT
        balanceKcal >= 150f -> BalanceState.SUPERAVIT
        else -> BalanceState.MANTENIMIENTO
    }

    /**
     * TDEE adaptativo (estilo MacroFactor): con ≥14 días de ingesta registrada y ≥3 pesajes
     * en las últimas 4 semanas, el gasto real es la ingesta media menos lo que explica el
     * cambio de peso (pendiente de la regresión lineal, en kg/día × 7.700).
     *
     * @param weights pares (día epoch, kg)
     * @param dailyIntakes ingesta total de cada día con registros (kcal)
     * @return TDEE estimado, o null si no hay datos suficientes
     */
    fun adaptiveTdee(weights: List<Pair<Long, Float>>, dailyIntakes: List<Float>): Float? {
        val loggedDays = dailyIntakes.filter { it > 500f }
        if (loggedDays.size < 14 || weights.size < 3) return null
        val spanDays = (weights.maxOf { it.first } - weights.minOf { it.first }).toFloat()
        if (spanDays < 10f) return null

        val xs = weights.map { it.first.toFloat() }
        val ys = weights.map { it.second }
        val mx = xs.average().toFloat()
        val my = ys.average().toFloat()
        var num = 0f
        var den = 0f
        for (i in xs.indices) {
            num += (xs[i] - mx) * (ys[i] - my)
            den += (xs[i] - mx) * (xs[i] - mx)
        }
        if (den == 0f) return null
        val kgPerDay = num / den
        val avgIntake = loggedDays.average().toFloat()
        return avgIntake - kgPerDay * KCAL_PER_KG
    }

    private fun Float.roundTo(step: Int): Float = ((this / step).roundToInt() * step).toFloat()
}
