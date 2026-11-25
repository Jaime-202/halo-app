package com.lifedashboard.data.local.dao

import androidx.room.*
import com.lifedashboard.data.local.entity.MoodEntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MoodDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: MoodEntryEntity)

    @Query("SELECT * FROM mood_entries WHERE dateMillis = :dateMillis LIMIT 1")
    fun observeForDay(dateMillis: Long): Flow<MoodEntryEntity?>

    @Query("SELECT * FROM mood_entries WHERE dateMillis >= :fromMillis AND dateMillis <= :toMillis")
    fun observeRange(fromMillis: Long, toMillis: Long): Flow<List<MoodEntryEntity>>

    @Query("SELECT * FROM mood_entries ORDER BY dateMillis ASC")
    suspend fun getAll(): List<MoodEntryEntity>
}
