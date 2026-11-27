package com.lifedashboard.core.health

import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.aggregate.AggregateMetric
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import kotlin.reflect.KClass

/**
 * Lee totales diarios de Health Connect sin duplicar datos de varias apps.
 *
 * 1. Primero la agregación de Health Connect (respeta la prioridad de apps y no duplica).
 * 2. Si falla o da 0, lee los registros y se queda con la app que más registra
 *    (nunca suma apps distintas, que es lo que duplicaba antes).
 */
class HealthTotals(private val client: HealthConnectClient) {

    data class Reading(
        val value: Double,
        val method: String,              // "agregado", "registros", "sin permiso", "sin datos"
        val bySource: Map<String, Double> = emptyMap(),
        val error: String? = null,
    )

    suspend fun steps(range: TimeRangeFilter) = read(range, StepsRecord.COUNT_TOTAL, { it.toDouble() }, StepsRecord::class) { (it as StepsRecord).count.toDouble() }
    suspend fun distanceMeters(range: TimeRangeFilter) = read(range, DistanceRecord.DISTANCE_TOTAL, { it.inMeters }, DistanceRecord::class) { (it as DistanceRecord).distance.inMeters }
    suspend fun activeKcal(range: TimeRangeFilter) = read(range, ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL, { it.inKilocalories }, ActiveCaloriesBurnedRecord::class) { (it as ActiveCaloriesBurnedRecord).energy.inKilocalories }
    suspend fun totalKcal(range: TimeRangeFilter) = read(range, TotalCaloriesBurnedRecord.ENERGY_TOTAL, { it.inKilocalories }, TotalCaloriesBurnedRecord::class) { (it as TotalCaloriesBurnedRecord).energy.inKilocalories }

    private suspend fun <T : Any> read(
        range: TimeRangeFilter,
        metric: AggregateMetric<T>,
        toDouble: (T) -> Double,
        type: KClass<out Record>,
        recordValue: (Record) -> Double,
    ): Reading {
        var aggError: String? = null
        val agg = try {
            client.aggregate(AggregateRequest(setOf(metric), range))[metric]?.let(toDouble)
        } catch (e: Exception) {
            aggError = e.message ?: e.javaClass.simpleName
            Log.w("HealthTotals", "Agregado fallido (${type.simpleName}): $aggError")
            null
        }
        val bySource = try {
            client.readRecords(ReadRecordsRequest(type, range)).records
                .groupBy { it.metadata.dataOrigin.packageName }
                .mapValues { (_, list) -> list.sumOf(recordValue) }
        } catch (e: SecurityException) {
            return Reading(agg ?: 0.0, if (agg != null && agg > 0) "agregado" else "sin permiso", error = e.message ?: aggError)
        } catch (e: Exception) {
            return Reading(agg ?: 0.0, if (agg != null && agg > 0) "agregado" else "sin datos", error = e.message ?: aggError)
        }
        if (agg != null && agg > 0) return Reading(agg, "agregado", bySource)
        val best = bySource.values.maxOrNull() ?: 0.0
        return Reading(best, if (best > 0) "registros" else "sin datos", bySource, aggError)
    }
}
