package com.lifedashboard.domain

import com.lifedashboard.domain.energy.ActivityLevel
import com.lifedashboard.domain.energy.EnergyCalculator
import com.lifedashboard.domain.energy.EnergyProfile
import com.lifedashboard.domain.energy.Sex
import com.lifedashboard.domain.energy.WeightGoal
import com.lifedashboard.domain.food.NutritionLabelParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class HaloCalculationsTest {

    @Test fun bmrMifflinStJeor() {
        // 10·80 + 6.25·180 − 5·30 + 5 = 1780
        assertEquals(1780f, EnergyCalculator.bmr(Sex.HOMBRE, 80f, 180, 30), 0.01f)
        // 10·60 + 6.25·165 − 5·30 − 161 = 1320.25
        assertEquals(1320.25f, EnergyCalculator.bmr(Sex.MUJER, 60f, 165, 30), 0.01f)
    }

    @Test fun targetsApplyWeeklyDeficitAndFloor() {
        val profile = EnergyProfile(Sex.HOMBRE, 30, 180, 80f, ActivityLevel.MODERADO, WeightGoal.PERDER_RAPIDO)
        val t = EnergyCalculator.targets(profile)
        // TDEE 1780·1.55 = 2759; −550 → 2209 → redondeo a 2210
        assertEquals(2210f, t.caloriesKcal, 0.01f)
        assertEquals(160f, t.proteinG, 0.01f)

        val tiny = EnergyCalculator.targets(profile.copy(activity = ActivityLevel.SEDENTARIO), adaptiveTdeeKcal = null)
        assert(tiny.caloriesKcal >= tiny.bmrKcal - 10f)
    }

    @Test fun adaptiveTdeeFromWeightTrend() {
        // Comiendo 2000 kcal y bajando 0,5 kg/semana → gasto ≈ 2550
        val weights = (0..21 step 3).map { d -> d.toLong() to (80f - d * 0.5f / 7f) }
        val intakes = List(20) { 2000f }
        val tdee = EnergyCalculator.adaptiveTdee(weights, intakes)
        assertNotNull(tdee)
        assertEquals(2550f, tdee!!, 5f)
    }

    @Test fun adaptiveTdeeNeedsEnoughData() {
        assertNull(EnergyCalculator.adaptiveTdee(listOf(0L to 80f, 1L to 80f), List(20) { 2000f }))
        assertNull(EnergyCalculator.adaptiveTdee(listOf(0L to 80f, 7L to 79.6f, 14L to 79.2f), List(5) { 2000f }))
    }

    @Test fun parsesSpanishNutritionLabel() {
        val text = """
            Información nutricional por 100 g
            Valor energético 1555 kJ / 370 kcal
            Grasas 7,0 g
            de las cuales saturadas 1,2 g
            Hidratos de carbono 58,7 g
            de los cuales azúcares 1,1 g
            Fibra alimentaria 10 g
            Proteínas 13,5 g
            Sal 0,02 g
        """.trimIndent()
        val v = NutritionLabelParser.parse(text)
        assertEquals(370f, v.kcal!!, 0.01f)
        assertEquals(7.0f, v.fat!!, 0.01f)
        assertEquals(58.7f, v.carbs!!, 0.01f)
        assertEquals(1.1f, v.sugar!!, 0.01f)
        assertEquals(10f, v.fiber!!, 0.01f)
        assertEquals(13.5f, v.protein!!, 0.01f)
        assertEquals(0.02f, v.salt!!, 0.001f)
    }
}
