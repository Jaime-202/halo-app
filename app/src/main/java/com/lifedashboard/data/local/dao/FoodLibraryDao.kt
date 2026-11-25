package com.lifedashboard.data.local.dao

import androidx.room.*
import com.lifedashboard.data.local.entity.FavoriteFoodEntity
import com.lifedashboard.data.local.entity.MealEntryEntity
import com.lifedashboard.data.local.entity.SavedMealEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FoodLibraryDao {

    // Favoritos
    @Query("SELECT * FROM favorite_foods ORDER BY createdAt DESC")
    fun observeFavorites(): Flow<List<FavoriteFoodEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFavorite(food: FavoriteFoodEntity)

    @Query("DELETE FROM favorite_foods WHERE `key` = :key")
    suspend fun deleteFavorite(key: String)

    // Comidas guardadas
    @Query("SELECT * FROM saved_meals ORDER BY createdAt DESC")
    fun observeSavedMeals(): Flow<List<SavedMealEntity>>

    @Insert
    suspend fun insertSavedMeal(meal: SavedMealEntity): Long

    @Query("DELETE FROM saved_meals WHERE id = :id")
    suspend fun deleteSavedMeal(id: Long)

    // Recientes: últimos alimentos registrados (se agrupan por nombre y marca en el ViewModel)
    @Query("SELECT * FROM meals WHERE grams > 0 ORDER BY createdAt DESC LIMIT 120")
    fun observeRecentEntries(): Flow<List<MealEntryEntity>>
}
