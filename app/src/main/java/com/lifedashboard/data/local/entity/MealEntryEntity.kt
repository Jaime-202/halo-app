package com.lifedashboard.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "meals",
    indices = [
        Index(value = ["dateMillis"]),
        Index(value = ["mealType"])
    ]
)
data class MealEntryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val dateMillis: Long,              // Timestamp de la fecha del día
    val mealType: String,              // "DESAYUNO", "COMIDA", "MERIENDA", "CENA", "SNACK"
    val name: String,                  // Nombre del plato o alimento
    val brand: String = "",            // Supermercado / Marca (ej. "Hacendado", "Carrefour", "Casero")
    val grams: Float = 100f,           // Cantidad en gramos o ml
    val calories: Float,               // Calorías calculadas
    val protein: Float,                // Proteína en gramos
    val carbs: Float,                  // Carbohidratos en gramos
    val fat: Float,                    // Grasas en gramos
    val notes: String = "",            // Observaciones o notas adicionales
    val imageUrl: String? = null,      // Foto del producto o plato
    @ColumnInfo(defaultValue = "0")
    val fiber: Float = 0f,             // Fibra (g)
    @ColumnInfo(defaultValue = "0")
    val sugar: Float = 0f,             // Azúcares (g)
    @ColumnInfo(defaultValue = "0")
    val salt: Float = 0f,              // Sal (g)
    val createdAt: Long = System.currentTimeMillis()
)
