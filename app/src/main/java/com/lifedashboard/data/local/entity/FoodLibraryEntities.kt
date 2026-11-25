package com.lifedashboard.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Alimento marcado como favorito (valores por 100 g). */
@Entity(tableName = "favorite_foods")
data class FavoriteFoodEntity(
    @PrimaryKey val key: String,          // nombre|marca normalizados
    val name: String,
    val brand: String,
    val caloriesPer100g: Float,
    val proteinPer100g: Float,
    val carbsPer100g: Float,
    val fatPer100g: Float,
    val fiberPer100g: Float = 0f,
    val sugarPer100g: Float = 0f,
    val saltPer100g: Float = 0f,
    val servingSizeGrams: Float = 100f,
    val servingDescription: String = "100 g",
    val imageUrl: String? = null,
    val barcode: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

/** Comida guardada ("Mi desayuno"): varios alimentos que se añaden de una vez. */
@Entity(tableName = "saved_meals")
data class SavedMealEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val itemsJson: String,                // lista de alimentos con gramos y macros
    val totalCalories: Float,
    val createdAt: Long = System.currentTimeMillis(),
)
