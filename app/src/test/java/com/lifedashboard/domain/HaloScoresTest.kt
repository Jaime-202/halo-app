package com.lifedashboard.domain

import com.lifedashboard.domain.score.DayInputs
import com.lifedashboard.domain.score.HaloScores
import com.lifedashboard.domain.score.RecoveryInputs
import com.lifedashboard.domain.score.RecoveryLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HaloScoresTest {

    @Test fun dayScoreIgnoresMissingData() {
        val d = DayInputs(steps = 5000, stepsTarget = 10000, sleepHours = 8f, sleepTarget = 8f,
            intakeKcal = null, intakeTargetKcal = 2200f, remindersDone = 0, remindersTotal = 0)
        assertEquals(75, HaloScores.dayScore(d))
    }

    @Test fun dayScoreNutritionTolerance() {
        val base = DayInputs(null, 10000, null, 8f, 2200f, 2200f, 0, 0)
        assertEquals(100, HaloScores.dayScore(base.copy(intakeKcal = 2400f)))   // +9 %
        assertEquals(0, HaloScores.dayScore(base.copy(intakeKcal = 3400f)))     // +55 %
    }

    @Test fun dayScoreNullWithoutData() {
        assertNull(HaloScores.dayScore(DayInputs(0, 10000, 0f, 8f, 0f, 2200f, 0, 0)))
    }

    @Test fun recoveryUsesPersonalBaseline() {
        val good = RecoveryInputs(8f, 8f, restingHr = 51, restingHrBaseline = 56f, hrvMs = 66f, hrvBaseline = 55f)
        assertEquals(100, HaloScores.recovery(good))
        val bad = RecoveryInputs(5f, 8f, restingHr = 62, restingHrBaseline = 56f, hrvMs = 42f, hrvBaseline = 55f)
        assertEquals(RecoveryLevel.BAJA, HaloScores.recoveryLevel(HaloScores.recovery(bad)!!))
    }

    @Test fun recoverySleepOnly() {
        assertEquals(75, HaloScores.recovery(RecoveryInputs(6f, 8f, null, null, null, null)))
    }
}
