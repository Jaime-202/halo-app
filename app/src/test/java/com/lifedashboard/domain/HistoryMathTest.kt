package com.lifedashboard.domain

import com.lifedashboard.domain.history.DaySummary
import com.lifedashboard.domain.history.HistoryMath
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class HistoryMathTest {
    private val today = LocalDate.of(2026, 11, 26) // jueves

    private fun days(vararg scores: Pair<Int, Int>) =
        scores.associate { (ago, score) -> today.minusDays(ago.toLong()).let { it to DaySummary(it, dayScore = score) } }

    @Test fun oneBadDayPerWeekKeepsStreak() {
        // Lun-mié buenos, martes flojo (comodín), y semana anterior completa
        val map = days(0 to 80, 1 to 70, 2 to 30, 3 to 90, 4 to 90, 5 to 90, 6 to 90, 7 to 90, 8 to 90, 9 to 90, 10 to 90)
        val s = HistoryMath.streak(map, today)
        assertEquals(10, s.current)
    }

    @Test fun twoBadDaysSameWeekBreakStreak() {
        val map = days(0 to 80, 1 to 30, 2 to 30, 3 to 90)
        assertEquals(1, HistoryMath.streak(map, today).current)
    }

    @Test fun todayNotYetGoodDoesNotBreak() {
        val map = days(0 to 20, 1 to 80, 2 to 80)
        assertEquals(2, HistoryMath.streak(map, today).current)
    }
}
