package com.lifedashboard.core.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.lifedashboard.core.notification.NotificationHelper
import com.lifedashboard.data.repository.HistoryRepository
import com.lifedashboard.data.repository.UserPreferencesRepository
import com.lifedashboard.domain.history.HistoryMath
import com.lifedashboard.domain.insights.InsightEngine
import com.lifedashboard.domain.insights.WeeklySummary
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.time.LocalDate

/** Envía el resumen de la semana los domingos por la noche y se vuelve a programar. */
@HiltWorker
class WeeklySummaryWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val prefs: UserPreferencesRepository,
    private val history: HistoryRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = try {
        val g = prefs.userGoals.first()
        if (g.weeklySummary) {
            val today = LocalDate.now()
            val days = history.observe(today.minusDays(70), today).first()
            val summary = WeeklySummary.build(days, HistoryMath.mondayOf(today))
            val insight = InsightEngine.analyze(days, g.sleepTargetHours, g.stepsTarget).firstOrNull()?.text
            NotificationHelper.showWeeklySummary(
                context,
                "Tu semana en Halo",
                summary.notificationText(insight),
            )
            WorkManagerScheduler(context).scheduleWeeklySummary()
        }
        Result.success()
    } catch (e: Exception) {
        Log.e("WeeklySummaryWorker", "Error en el resumen semanal", e)
        Result.failure()
    }

    companion object {
        const val WORK_NAME = "halo_weekly_summary"
    }
}
