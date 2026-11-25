package com.lifedashboard.data.local.dao

import androidx.room.*
import com.lifedashboard.data.local.entity.WaterIntakeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WaterIntakeDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: WaterIntakeEntity)

    @Query("""
        SELECT SUM(amountMl) FROM water_intake
        WHERE dateMillis = :dateMillis
    """)
    fun getTotalForDay(dateMillis: Long): Flow<Int?>

    @Query("""
        SELECT * FROM water_intake
        WHERE dateMillis >= :fromMillis AND dateMillis <= :toMillis
        ORDER BY timestampMillis DESC
    """)
    fun getEntriesInRange(fromMillis: Long, toMillis: Long): Flow<List<WaterIntakeEntity>>

    @Delete
    suspend fun delete(entry: WaterIntakeEntity)

    @Query("DELETE FROM water_intake WHERE dateMillis < :cutoffMillis")
    suspend fun deleteOlderThan(cutoffMillis: Long)
}
