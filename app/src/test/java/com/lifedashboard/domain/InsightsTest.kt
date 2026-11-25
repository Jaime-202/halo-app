package com.lifedashboard.domain

import com.lifedashboard.domain.history.DaySummary
import com.lifedashboard.domain.insights.InsightEngine
import com.lifedashboard.domain.insights.WeeklySummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class InsightsTest {
    private val start = LocalDate.of(2026, 10, 5) // lunes

    @Test fun findsSleepAfterTraining() {
        // Días pares entreno; la noche siguiente se duerme 8 h, si no 7 h
        val days = (0 until 28).associate { i ->
            val d = start.plusDays(i.toLong())
            val prevTrained = (i - 1) % 2 == 0 && i > 0
            d to DaySummary(d, trained = i % 2 == 0, sleepHours = if (prevTrained) 8f else 7f, steps = 5000)
        }
        val insights = InsightEngine.analyze(days, sleepTarget = 8f, stepsTarget = 10000)
        assertTrue(insights.any { it.text.startsWith("Duermes 60 min más") })
    }

    @Test fun noInsightWithFewDays() {
        val days = (0 until 3).associate { i -> start.plusDays(i.toLong()).let { it to DaySummary(it, trained = true, mood = 5) } }
        assertTrue(InsightEngine.analyze(days, 8f, 10000).isEmpty())
    }

    @Test fun weeklySummaryComparesWithPreviousWeek() {
        val days = (0 until 14).associate { i ->
            val d = start.plusDays(i.toLong())
            d to DaySummary(d, dayScore = if (i < 7) 60 else 70, trained = i in setOf(8, 10, 12), workoutMinutes = if (i in setOf(8, 10, 12)) 50 else 0)
        }
        val w = WeeklySummary.build(days, start.plusWeeks(1))
        assertEquals(70, w.avgHalo)
        assertEquals(10, w.haloDelta)
        assertEquals(3, w.trainedDays)
        assertEquals(150, w.workoutMinutes)
    }
}
