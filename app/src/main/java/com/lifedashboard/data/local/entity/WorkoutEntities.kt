package com.lifedashboard.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Sesión de entrenamiento (un día en el gym).
 * Una sesión contiene múltiples ExerciseSetEntity.
 */
@Entity(tableName = "workout_sessions")
data class WorkoutSessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val dateMillis: Long,
    val type: String,                       // "STRENGTH" | "CARDIO" | "REST"
    val durationMinutes: Int = 0,
    val notes: String? = null,
    val sourceFile: String? = null,         // "health_connect" | "symmetry_csv" | "manual"
    val muscleGroupAllocation: String? = null, // Ej. "Pecho:60,Tríceps:40"
    @ColumnInfo(defaultValue = "0")
    val startMillis: Long = 0,              // Hora de inicio real (Health Connect)
    @ColumnInfo(defaultValue = "0")
    val endMillis: Long = 0,
    @ColumnInfo(defaultValue = "0")
    val activeKcal: Float = 0f,             // Calorías activas durante la sesión
    @ColumnInfo(defaultValue = "0")
    val avgHeartRate: Int = 0,              // Pulso medio (lpm), 0 = sin dato
    @ColumnInfo(defaultValue = "0")
    val maxHeartRate: Int = 0
)

/**
 * Serie individual de un ejercicio dentro de una sesión.
 */
@Entity(
    tableName = "exercise_sets",
    foreignKeys = [ForeignKey(
        entity = WorkoutSessionEntity::class,
        parentColumns = ["id"],
        childColumns = ["sessionId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("sessionId")]
)
data class ExerciseSetEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sessionId: Long,
    val exerciseName: String,       // "Sentadilla en Péndulo", "Peso Muerto Rumano", etc.
    val setNumber: Int,
    val reps: Int,
    val weightKg: Float,
    val volumeKg: Float = weightKg * reps,  // Volumen = peso × reps
    val rpe: Float? = null          // Rating of Perceived Exertion (opcional)
)
