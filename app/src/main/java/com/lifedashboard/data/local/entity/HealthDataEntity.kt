package com.lifedashboard.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "health_data")
data class HealthDataEntity(
    @PrimaryKey
    val dateMillis: Long,               // Clave: día en epoch millis (a medianoche local)
    val steps: Int = 0,
    val distanceMeters: Float = 0f,
    val activeCalories: Float = 0f,
    val sleepHours: Float = 0f,         // Horas totales de sueño ese día
    val sleepDeepHours: Float = 0f,
    val sleepRemHours: Float = 0f,
    @ColumnInfo(defaultValue = "0")
    val restingHeartRate: Int = 0,      // Pulso en reposo (lpm), 0 = sin dato
    @ColumnInfo(defaultValue = "0")
    val hrvMs: Float = 0f,              // Variabilidad cardiaca RMSSD (ms), 0 = sin dato
    val syncedAt: Long = System.currentTimeMillis()
)
