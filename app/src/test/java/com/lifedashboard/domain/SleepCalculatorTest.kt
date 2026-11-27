package com.lifedashboard.domain

import com.lifedashboard.domain.health.SleepCalculator
import com.lifedashboard.domain.health.SleepCalculator.Session
import com.lifedashboard.domain.health.SleepCalculator.Stage
import org.junit.Assert.assertEquals
import org.junit.Test

class SleepCalculatorTest {
    private val h = 3_600_000L
    private val m = 60_000L
    private val w0 = 0L
    private val w1 = 24 * h

    @Test fun dosFuentesMismaNocheNoSeSuman() {
        val n = SleepCalculator.night(listOf(
            Session("reloj", 1 * h, 9 * h),
            Session("movil", 1 * h, 9 * h),
        ), w0, w1)
        assertEquals(8 * 60L, n.asleepMinutes)
    }

    @Test fun despertarConSesionesSolapadasCuentaUnaVez() {
        val n = SleepCalculator.night(listOf(
            Session("reloj", 1 * h, 4 * h),
            Session("reloj", 3 * h, 9 * h),
        ), w0, w1)
        assertEquals(8 * 60L, n.asleepMinutes)
    }

    @Test fun elTiempoDespiertoNoCuenta() {
        val n = SleepCalculator.night(listOf(
            Session("reloj", 1 * h, 9 * h, listOf(
                Stage(1 * h, 4 * h, SleepCalculator.STAGE_LIGHT),
                Stage(4 * h, 4 * h + 30 * m, SleepCalculator.STAGE_AWAKE),
                Stage(4 * h + 30 * m, 6 * h, SleepCalculator.STAGE_DEEP),
                Stage(6 * h, 9 * h, SleepCalculator.STAGE_REM),
            )),
        ), w0, w1)
        assertEquals(7 * 60L + 30, n.asleepMinutes)
        assertEquals(90L, n.deepMinutes)
        assertEquals(180L, n.remMinutes)
    }

    @Test fun preFiereLaFuenteConFases() {
        val n = SleepCalculator.night(listOf(
            Session("movil", 0, 10 * h),
            Session("reloj", 1 * h, 9 * h, listOf(Stage(1 * h, 8 * h, SleepCalculator.STAGE_LIGHT))),
        ), w0, w1)
        assertEquals("reloj", n.source)
        assertEquals(7 * 60L, n.asleepMinutes)
    }
}
