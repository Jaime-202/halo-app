package com.lifedashboard.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "water_intake")
data class WaterIntakeEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val dateMillis: Long,           // Día al que pertenece
    val timestampMillis: Long,      // Momento exacto del registro
    val amountMl: Int               // Cantidad en mililitros
)
