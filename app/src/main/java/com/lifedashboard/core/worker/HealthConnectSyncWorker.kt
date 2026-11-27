package com.lifedashboard.core.worker

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.lifedashboard.core.util.DateUtils.toStartOfDayMillis
import com.lifedashboard.data.local.dao.HealthDao
import com.lifedashboard.data.local.dao.WorkoutDao
import com.lifedashboard.data.local.entity.HealthDataEntity
import com.lifedashboard.data.local.entity.WorkoutSessionEntity
import com.lifedashboard.presentation.widget.LifeDashboardWidgetReceiver
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit

private const val TAG = "HealthConnectWorker"

/**
 * ─────────────────────────────────────────────────────────────────────────────
 * HealthConnectSyncWorker
 *
 * Lee pasos, distancia, calorías, sueño y sesiones de ejercicio de Health Connect
 * y los persiste en Room. Se ejecuta periódicamente o al pulsar Sincronizar.
 * ─────────────────────────────────────────────────────────────────────────────
 */
@HiltWorker
class HealthConnectSyncWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val healthDao: HealthDao,
    private val workoutDao: WorkoutDao,
    private val userPreferences: com.lifedashboard.data.repository.UserPreferencesRepository,
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        return@withContext try {
            val status = try {
                HealthConnectClient.getSdkStatus(context)
            } catch (e: Throwable) {
                HealthConnectClient.SDK_UNAVAILABLE
            }

            if (status != HealthConnectClient.SDK_AVAILABLE) {
                Log.w(TAG, "Health Connect no disponible en este dispositivo (status=$status)")
                return@withContext Result.failure()
            }

            val client = HealthConnectClient.getOrCreate(context)
            workoutDao.deduplicateSessions()
            syncRecentDays(client)
            workoutDao.deduplicateSessions()
            updateWidget()
            Result.success()
        } catch (e: SecurityException) {
            Log.w(TAG, "Permiso Health Connect revocado", e)
            Result.failure()
        } catch (e: Throwable) {
            Log.e(TAG, "Error sincronizando Health Connect", e)
            Result.failure()
        }
    }

    private suspend fun syncRecentDays(client: HealthConnectClient) {
        val today = LocalDate.now()
        val results = mutableListOf<HealthDataEntity>()

        for (daysAgo in 0..13) {
            val date = today.minusDays(daysAgo.toLong())
            val entity = buildEntityForDay(client, date)
            results.add(entity)
        }

        healthDao.upsertAll(results)
        Log.d(TAG, "Health Connect sync: ${results.size} días actualizados")
    }

    private suspend fun buildEntityForDay(
        client: HealthConnectClient,
        date: LocalDate
    ): HealthDataEntity {
        val zone = ZoneId.systemDefault()
        val startInstant = date.atStartOfDay(zone).toInstant()
        val endInstant = date.plusDays(1).atStartOfDay(zone).toInstant()
        val timeRange = TimeRangeFilter.between(startInstant, endInstant)

        // Pasos, distancia y calorías sin duplicar datos de varias apps
        val totals = com.lifedashboard.core.health.HealthTotals(client)
        val steps = totals.steps(timeRange).value.toInt()
        val distanceMeters = totals.distanceMeters(timeRange).value.toFloat()

        // Calorías activas (solo el movimiento, sin el metabolismo basal)
        var calories = totals.activeKcal(timeRange).value.toFloat()
        if (calories <= 0f) {
            // Samsung Health y otras apps a menudo solo guardan el gasto total (basal + movimiento):
            // se resta el basal de tu perfil, proporcional a la parte del día transcurrida.
            val total = totals.totalKcal(timeRange).value.toFloat()
            if (total > 0f) {
                val goals = userPreferences.userGoals.first()
                val bmr = com.lifedashboard.domain.energy.EnergyCalculator.bmr(
                    com.lifedashboard.domain.energy.Sex.from(goals.sex), goals.weightKg, goals.heightCm, goals.ageYears
                )
                val now = Instant.now()
                val fraction = if (now.isBefore(endInstant)) Duration.between(startInstant, now).toMinutes() / 1440f else 1f
                calories = (total - bmr * fraction).coerceAtLeast(0f)
            }
        }
        // Sin datos de calorías pero con pasos: estimación (≈0,04 kcal por paso)
        if (calories <= 0f && steps > 0) calories = steps * 0.04f

        // Sueño de la noche anterior: de las 18:00 del día previo a las 14:00 de hoy.
        val sleepStart = date.minusDays(1).atTime(18, 0).atZone(zone).toInstant()
        val sleepEnd = date.atTime(14, 0).atZone(zone).toInstant()
        val sleepRange = TimeRangeFilter.between(sleepStart, sleepEnd)
        val sleepResp = client.readRecords(ReadRecordsRequest(SleepSessionRecord::class, sleepRange))
        val night = com.lifedashboard.domain.health.SleepCalculator.night(
            sleepResp.records.map { s ->
                com.lifedashboard.domain.health.SleepCalculator.Session(
                    source = s.metadata.dataOrigin.packageName,
                    start = s.startTime.toEpochMilli(),
                    end = s.endTime.toEpochMilli(),
                    stages = s.stages.map { st ->
                        com.lifedashboard.domain.health.SleepCalculator.Stage(st.startTime.toEpochMilli(), st.endTime.toEpochMilli(), st.stage)
                    },
                )
            },
            sleepStart.toEpochMilli(), sleepEnd.toEpochMilli(),
        )
        val totalSleepMinutes = night.asleepMinutes
        val deepMinutes = night.deepMinutes
        val remMinutes = night.remMinutes

        // Sesiones de ejercicio (Health Connect / Entrenamientos registrados)
        try {
            val exResp = client.readRecords(
                ReadRecordsRequest(ExerciseSessionRecord::class, timeRange)
            )
            workoutDao.deleteHealthConnectSessionsForDay(date.toStartOfDayMillis())
            exResp.records.forEach { exSession ->
                val durationMin = Duration.between(exSession.startTime, exSession.endTime).toMinutes().toInt()
                val sessionRange = TimeRangeFilter.between(exSession.startTime, exSession.endTime)
                val typeStr = when (exSession.exerciseType) {
                    ExerciseSessionRecord.EXERCISE_TYPE_RUNNING,
                    ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL,
                    ExerciseSessionRecord.EXERCISE_TYPE_BIKING,
                    ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY,
                    ExerciseSessionRecord.EXERCISE_TYPE_WALKING,
                    ExerciseSessionRecord.EXERCISE_TYPE_HIKING,
                    ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL,
                    ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER,
                    ExerciseSessionRecord.EXERCISE_TYPE_ROWING_MACHINE,
                    ExerciseSessionRecord.EXERCISE_TYPE_ELLIPTICAL,
                    ExerciseSessionRecord.EXERCISE_TYPE_STAIR_CLIMBING_MACHINE -> "CARDIO"
                    else -> "STRENGTH" // Symmetry registra fuerza; el resto se trata igual
                }
                // Calorías activas dentro de la sesión
                val sessionKcal = try {
                    client.aggregate(androidx.health.connect.client.request.AggregateRequest(setOf(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL), sessionRange))[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]
                        ?.inKilocalories?.toFloat() ?: 0f
                } catch (e: Exception) { 0f }
                // Pulso medio y máximo (permiso opcional)
                var avgHr = 0
                var maxHr = 0
                try {
                    val samples = client.readRecords(ReadRecordsRequest(HeartRateRecord::class, sessionRange))
                        .records.flatMap { it.samples }.map { it.beatsPerMinute }
                    if (samples.isNotEmpty()) {
                        avgHr = samples.average().toInt()
                        maxHr = samples.max().toInt()
                    }
                } catch (e: Exception) { /* sin permiso o sin reloj */ }

                workoutDao.upsertSession(
                    WorkoutSessionEntity(
                        dateMillis      = date.toStartOfDayMillis(),
                        type            = typeStr,
                        durationMinutes = durationMin.coerceAtLeast(1),
                        notes           = exSession.title ?: exSession.notes,
                        sourceFile      = "health_connect",
                        startMillis     = exSession.startTime.toEpochMilli(),
                        endMillis       = exSession.endTime.toEpochMilli(),
                        activeKcal      = sessionKcal,
                        avgHeartRate    = avgHr,
                        maxHeartRate    = maxHr
                    )
                )
            }
        } catch (e: Exception) {
            // Ignorar si no hay registro o permiso
        }

        // Pulso en reposo y variabilidad cardiaca (permisos opcionales)
        var restingHr = 0
        try {
            val rhr = client.readRecords(ReadRecordsRequest(RestingHeartRateRecord::class, timeRange)).records
            if (rhr.isNotEmpty()) restingHr = rhr.map { it.beatsPerMinute }.average().toInt()
        } catch (e: Exception) {
            Log.w(TAG, "Sin pulso en reposo: ${e.message}")
        }
        var hrv = 0f
        try {
            // La HRV se mide sobre todo de noche: misma ventana que el sueño
            val hrvRecords = client.readRecords(ReadRecordsRequest(HeartRateVariabilityRmssdRecord::class, sleepRange)).records
            if (hrvRecords.isNotEmpty()) hrv = hrvRecords.map { it.heartRateVariabilityMillis }.average().toFloat()
        } catch (e: Exception) {
            Log.w(TAG, "Sin variabilidad cardiaca: ${e.message}")
        }

        return HealthDataEntity(
            dateMillis      = date.toStartOfDayMillis(),
            steps           = steps,
            distanceMeters  = distanceMeters,
            activeCalories  = calories,
            sleepHours      = totalSleepMinutes / 60f,
            sleepDeepHours  = deepMinutes / 60f,
            restingHeartRate = restingHr,
            hrvMs           = hrv,
            sleepRemHours   = remMinutes / 60f
        )
    }

    private suspend fun updateWidget() {
        WidgetUpdateWorker.updateAllWidgets(context)
    }

    companion object {
        const val WORK_NAME = "health_connect_sync"

        fun buildRequest(intervalMinutes: Long = 30): PeriodicWorkRequest =
            PeriodicWorkRequestBuilder<HealthConnectSyncWorker>(intervalMinutes.coerceAtLeast(15), TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                        .setRequiresBatteryNotLow(false)
                        .build()
                )
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    15, TimeUnit.MINUTES
                )
                .build()
    }
}

private fun LocalDate.toStartOfDayMillis(): Long =
    atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
