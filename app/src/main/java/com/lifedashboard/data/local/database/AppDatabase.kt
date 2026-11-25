package com.lifedashboard.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.lifedashboard.data.local.dao.*
import com.lifedashboard.data.local.entity.*

@Database(
    entities = [
        HealthDataEntity::class,
        WorkoutSessionEntity::class,
        ExerciseSetEntity::class,
        CalendarEventEntity::class,
        WaterIntakeEntity::class,
        WeightEntryEntity::class,
        MealEntryEntity::class,
        ReminderEntity::class,
        ReminderLogEntity::class,
        FavoriteFoodEntity::class,
        SavedMealEntity::class,
        MoodEntryEntity::class
    ],
    version = 13,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun healthDao(): HealthDao
    abstract fun workoutDao(): WorkoutDao
    abstract fun calendarEventDao(): CalendarEventDao
    abstract fun waterIntakeDao(): WaterIntakeDao
    abstract fun weightDao(): WeightDao
    abstract fun mealDao(): MealDao
    abstract fun reminderDao(): ReminderDao
    abstract fun foodLibraryDao(): FoodLibraryDao
    abstract fun moodDao(): MoodDao
}
