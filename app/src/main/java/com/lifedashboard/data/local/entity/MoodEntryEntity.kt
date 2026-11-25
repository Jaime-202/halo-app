package com.lifedashboard.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Ánimo y energía del día (1 = muy bajo, 5 = muy alto). Uno por día. */
@Entity(tableName = "mood_entries")
data class MoodEntryEntity(
    @PrimaryKey val dateMillis: Long,
    val mood: Int,
    val energy: Int,
    val note: String = "",
    val updatedAt: Long = System.currentTimeMillis(),
)
