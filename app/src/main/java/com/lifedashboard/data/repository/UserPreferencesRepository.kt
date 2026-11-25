package com.lifedashboard.data.repository

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "user_preferences")

data class UserGoals(
    val stepsTarget: Int = 10000,
    val sleepTargetHours: Float = 8.0f,
    val caloriesTarget: Float = 2200f, // Meta de ingesta calórica de comida
    val activeCaloriesTarget: Float = 500f, // Meta de calorías activas quemadas (diario / ejercicio)
    val workoutDaysTarget: Int = 4,
    val workoutMinutesTarget: Int = 180,
    val userName: String = "Jaime",
    val fitnessLevel: String = "INTERMEDIO",       // "PRINCIPIANTE" | "INTERMEDIO" | "AVANZADO" | "ATLETA"
    val primaryGoal: String = "SALUD_GENERAL",     // "GRASA" | "MUSCULO" | "CARDIO" | "SALUD_GENERAL"
    val weightKg: Float = 75.0f,
    val heightCm: Int = 175,
    val showHeroRings: Boolean = true,
    val showAiInsight: Boolean = true,
    val showUpcomingEvents: Boolean = true,
    val dailyReminders: Boolean = true,
    val hydrationReminder: Boolean = false,
    val inactivityAlerts: Boolean = false,
    val eveningReview: Boolean = true,
    val aiStyle: String = "CIENTIFICO",            // "CIENTIFICO" | "MOTIVADOR" | "DIRECTO"
    val syncFrequency: String = "30_MIN",          // "15_MIN" | "30_MIN" | "60_MIN" | "MANUAL"
    val themeMode: String = "OSCURO",
    val geminiApiKey: String = "",
    val geminiModel: String = "gemini-3.8-flash",
    val bedtimeReminderEnabled: Boolean = true,
    val wakeUpHour: Int = 8,
    val wakeUpMinute: Int = 30,
    val bedtimeReminderMinutesBefore: Int = 30,
    val proteinTargetGrams: Float = 140f,
    val carbsTargetGrams: Float = 250f,
    val fatTargetGrams: Float = 70f,
    val waterTargetMl: Int = 2500,
    // Perfil energético (Halo)
    val sex: String = "HOMBRE",                    // "HOMBRE" | "MUJER"
    val ageYears: Int = 30,
    val activityLevel: String = "MODERADO",        // ver ActivityLevel
    val weightGoal: String = "MANTENER",           // ver WeightGoal
    val adaptiveTdeeKcal: Float = 0f,              // 0 = sin datos suficientes
    val onboardingCompleted: Boolean = false
)

@Singleton
class UserPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val STEPS_TARGET           = intPreferencesKey("steps_target")
        val SLEEP_TARGET           = floatPreferencesKey("sleep_target")
        val CALORIES_TARGET        = floatPreferencesKey("calories_target")
        val ACTIVE_CALORIES_TARGET = floatPreferencesKey("active_calories_target")
        val WORKOUT_DAYS_TARGET    = intPreferencesKey("workout_days_target")
        val WORKOUT_MINUTES_TARGET = intPreferencesKey("workout_minutes_target")
        val USER_NAME              = stringPreferencesKey("user_name")
        val FITNESS_LEVEL          = stringPreferencesKey("fitness_level")
        val PRIMARY_GOAL           = stringPreferencesKey("primary_goal")
        val WEIGHT_KG              = floatPreferencesKey("weight_kg")
        val HEIGHT_CM              = intPreferencesKey("height_cm")
        val SHOW_HERO_RINGS        = booleanPreferencesKey("show_hero_rings")
        val SHOW_AI_INSIGHT        = booleanPreferencesKey("show_ai_insight")
        val SHOW_UPCOMING_EVENTS   = booleanPreferencesKey("show_upcoming_events")
        val DAILY_REMINDERS        = booleanPreferencesKey("daily_reminders")
        val HYDRATION_REMINDER     = booleanPreferencesKey("hydration_reminder")
        val INACTIVITY_ALERTS      = booleanPreferencesKey("inactivity_alerts")
        val EVENING_REVIEW         = booleanPreferencesKey("evening_review")
        val AI_STYLE               = stringPreferencesKey("ai_style")
        val SYNC_FREQUENCY         = stringPreferencesKey("sync_frequency")
        val THEME_MODE             = stringPreferencesKey("theme_mode")
        val GEMINI_API_KEY         = stringPreferencesKey("gemini_api_key")
        val GEMINI_MODEL           = stringPreferencesKey("gemini_model")
        val BEDTIME_REMINDER       = booleanPreferencesKey("bedtime_reminder_enabled")
        val WAKE_UP_HOUR           = intPreferencesKey("wake_up_hour")
        val WAKE_UP_MINUTE         = intPreferencesKey("wake_up_minute")
        val BEDTIME_MINUTES_BEFORE = intPreferencesKey("bedtime_minutes_before")
        val PROTEIN_TARGET         = floatPreferencesKey("protein_target")
        val CARBS_TARGET           = floatPreferencesKey("carbs_target")
        val FAT_TARGET             = floatPreferencesKey("fat_target")
        val WATER_TARGET_ML        = intPreferencesKey("water_target_ml")
        val SEX                    = stringPreferencesKey("sex")
        val AGE_YEARS              = intPreferencesKey("age_years")
        val ACTIVITY_LEVEL         = stringPreferencesKey("activity_level")
        val WEIGHT_GOAL            = stringPreferencesKey("weight_goal")
        val ADAPTIVE_TDEE          = floatPreferencesKey("adaptive_tdee")
        val ONBOARDING_DONE        = booleanPreferencesKey("onboarding_done")
    }

    val userGoals: Flow<UserGoals> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { prefs ->
            UserGoals(
                stepsTarget          = prefs[Keys.STEPS_TARGET] ?: 10000,
                sleepTargetHours     = prefs[Keys.SLEEP_TARGET] ?: 8.0f,
                caloriesTarget       = prefs[Keys.CALORIES_TARGET] ?: 2200f,
                activeCaloriesTarget = prefs[Keys.ACTIVE_CALORIES_TARGET] ?: 500f,
                workoutDaysTarget    = prefs[Keys.WORKOUT_DAYS_TARGET] ?: 4,
                workoutMinutesTarget = prefs[Keys.WORKOUT_MINUTES_TARGET] ?: 180,
                userName             = prefs[Keys.USER_NAME] ?: "Jaime",
                fitnessLevel         = prefs[Keys.FITNESS_LEVEL] ?: "INTERMEDIO",
                primaryGoal          = prefs[Keys.PRIMARY_GOAL] ?: "SALUD_GENERAL",
                weightKg             = prefs[Keys.WEIGHT_KG] ?: 75.0f,
                heightCm             = prefs[Keys.HEIGHT_CM] ?: 175,
                showHeroRings        = prefs[Keys.SHOW_HERO_RINGS] ?: true,
                showAiInsight        = prefs[Keys.SHOW_AI_INSIGHT] ?: true,
                showUpcomingEvents   = prefs[Keys.SHOW_UPCOMING_EVENTS] ?: true,
                dailyReminders       = prefs[Keys.DAILY_REMINDERS] ?: true,
                hydrationReminder    = prefs[Keys.HYDRATION_REMINDER] ?: false,
                inactivityAlerts     = prefs[Keys.INACTIVITY_ALERTS] ?: false,
                eveningReview        = prefs[Keys.EVENING_REVIEW] ?: true,
                aiStyle              = prefs[Keys.AI_STYLE] ?: "CIENTIFICO",
                syncFrequency        = prefs[Keys.SYNC_FREQUENCY] ?: "30_MIN",
                themeMode            = prefs[Keys.THEME_MODE] ?: "OSCURO",
                geminiApiKey         = prefs[Keys.GEMINI_API_KEY] ?: "",
                geminiModel          = prefs[Keys.GEMINI_MODEL]?.takeIf { it.isNotBlank() } ?: "gemini-3.8-flash",
                bedtimeReminderEnabled = prefs[Keys.BEDTIME_REMINDER] ?: true,
                wakeUpHour           = prefs[Keys.WAKE_UP_HOUR] ?: 8,
                wakeUpMinute         = prefs[Keys.WAKE_UP_MINUTE] ?: 30,
                bedtimeReminderMinutesBefore = prefs[Keys.BEDTIME_MINUTES_BEFORE] ?: 30,
                proteinTargetGrams   = prefs[Keys.PROTEIN_TARGET] ?: 140f,
                carbsTargetGrams     = prefs[Keys.CARBS_TARGET] ?: 250f,
                fatTargetGrams       = prefs[Keys.FAT_TARGET] ?: 70f,
                waterTargetMl        = prefs[Keys.WATER_TARGET_ML] ?: 2500,
                sex                  = prefs[Keys.SEX] ?: "HOMBRE",
                ageYears             = prefs[Keys.AGE_YEARS] ?: 30,
                activityLevel        = prefs[Keys.ACTIVITY_LEVEL] ?: "MODERADO",
                weightGoal           = prefs[Keys.WEIGHT_GOAL] ?: "MANTENER",
                adaptiveTdeeKcal     = prefs[Keys.ADAPTIVE_TDEE] ?: 0f,
                onboardingCompleted  = prefs[Keys.ONBOARDING_DONE] ?: false
            )
        }

    suspend fun updateStepsTarget(target: Int) {
        context.dataStore.edit { prefs -> prefs[Keys.STEPS_TARGET] = target }
    }

    suspend fun updateSleepTarget(targetHours: Float) {
        context.dataStore.edit { prefs -> prefs[Keys.SLEEP_TARGET] = targetHours }
    }

    suspend fun updateCaloriesTarget(targetCalories: Float) {
        context.dataStore.edit { prefs -> prefs[Keys.CALORIES_TARGET] = targetCalories }
    }

    suspend fun updateActiveCaloriesTarget(targetCalories: Float) {
        context.dataStore.edit { prefs -> prefs[Keys.ACTIVE_CALORIES_TARGET] = targetCalories }
    }

    suspend fun updateWorkoutDaysTarget(days: Int) {
        context.dataStore.edit { prefs -> prefs[Keys.WORKOUT_DAYS_TARGET] = days }
    }

    suspend fun updateWorkoutMinutesTarget(minutes: Int) {
        context.dataStore.edit { prefs -> prefs[Keys.WORKOUT_MINUTES_TARGET] = minutes }
    }

    suspend fun updateUserName(name: String) {
        context.dataStore.edit { prefs -> prefs[Keys.USER_NAME] = name }
    }

    suspend fun updateFitnessProfile(level: String, goal: String, weight: Float, height: Int) {
        context.dataStore.edit { prefs ->
            prefs[Keys.FITNESS_LEVEL] = level
            // Si cambia el objetivo desde la pantalla antigua, traducirlo al objetivo de peso
            if (prefs[Keys.PRIMARY_GOAL] != goal) {
                prefs[Keys.WEIGHT_GOAL] = when (goal) {
                    "GRASA", "PERDIDA_PESO" -> "PERDER_RAPIDO"
                    "MUSCULO", "VOLUMEN" -> "GANAR"
                    else -> "MANTENER"
                }
            }
            prefs[Keys.PRIMARY_GOAL] = goal
            prefs[Keys.WEIGHT_KG] = weight
            prefs[Keys.HEIGHT_CM] = height
        }
    }

    suspend fun updateShowHeroRings(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[Keys.SHOW_HERO_RINGS] = enabled }
    }

    suspend fun updateShowAiInsight(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[Keys.SHOW_AI_INSIGHT] = enabled }
    }

    suspend fun updateShowUpcomingEvents(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[Keys.SHOW_UPCOMING_EVENTS] = enabled }
    }

    suspend fun updateDailyReminders(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[Keys.DAILY_REMINDERS] = enabled }
    }

    suspend fun updateHydrationReminder(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[Keys.HYDRATION_REMINDER] = enabled }
    }

    suspend fun updateInactivityAlerts(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[Keys.INACTIVITY_ALERTS] = enabled }
    }

    suspend fun updateEveningReview(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[Keys.EVENING_REVIEW] = enabled }
    }

    suspend fun updateAiStyle(style: String) {
        context.dataStore.edit { prefs -> prefs[Keys.AI_STYLE] = style }
    }

    suspend fun updateSyncFrequency(freq: String) {
        context.dataStore.edit { prefs -> prefs[Keys.SYNC_FREQUENCY] = freq }
    }

    suspend fun updateThemeMode(mode: String) {
        context.dataStore.edit { prefs -> prefs[Keys.THEME_MODE] = mode }
    }

    suspend fun updateGeminiApiKey(apiKey: String) {
        context.dataStore.edit { prefs -> prefs[Keys.GEMINI_API_KEY] = apiKey.trim() }
    }

    suspend fun updateGeminiModel(model: String) {
        context.dataStore.edit { prefs -> prefs[Keys.GEMINI_MODEL] = model.trim() }
    }

    suspend fun updateBedtimeReminder(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[Keys.BEDTIME_REMINDER] = enabled }
    }

    suspend fun updateWakeUpTime(hour: Int, minute: Int) {
        context.dataStore.edit { prefs ->
            prefs[Keys.WAKE_UP_HOUR] = hour
            prefs[Keys.WAKE_UP_MINUTE] = minute
        }
    }

    suspend fun updateBedtimeMinutesBefore(minutes: Int) {
        context.dataStore.edit { prefs -> prefs[Keys.BEDTIME_MINUTES_BEFORE] = minutes }
    }

    suspend fun updateMacroTargets(protein: Float, carbs: Float, fat: Float) {
        context.dataStore.edit { prefs ->
            prefs[Keys.PROTEIN_TARGET] = protein
            prefs[Keys.CARBS_TARGET] = carbs
            prefs[Keys.FAT_TARGET] = fat
        }
    }

    suspend fun updateCalorieAndMacroTargets(calories: Float, protein: Float, carbs: Float, fat: Float) {
        context.dataStore.edit { prefs ->
            prefs[Keys.CALORIES_TARGET] = calories
            prefs[Keys.PROTEIN_TARGET] = protein
            prefs[Keys.CARBS_TARGET] = carbs
            prefs[Keys.FAT_TARGET] = fat
        }
    }

    suspend fun updateWeight(weightKg: Float) {
        context.dataStore.edit { prefs ->
            prefs[Keys.WEIGHT_KG] = weightKg
        }
    }

    /** Guarda el perfil energético completo (bienvenida o edición desde Ajustes). */
    suspend fun updateEnergyProfile(
        name: String,
        sex: String,
        ageYears: Int,
        heightCm: Int,
        weightKg: Float,
        activityLevel: String,
        weightGoal: String,
    ) {
        context.dataStore.edit { prefs ->
            if (name.isNotBlank()) prefs[Keys.USER_NAME] = name.trim()
            prefs[Keys.SEX] = sex
            prefs[Keys.AGE_YEARS] = ageYears
            prefs[Keys.HEIGHT_CM] = heightCm
            prefs[Keys.WEIGHT_KG] = weightKg
            prefs[Keys.ACTIVITY_LEVEL] = activityLevel
            prefs[Keys.WEIGHT_GOAL] = weightGoal
            // Mantener el objetivo antiguo en sintonía para las pantallas que aún lo leen
            prefs[Keys.PRIMARY_GOAL] = when (weightGoal) {
                "PERDER", "PERDER_RAPIDO" -> "GRASA"
                "GANAR" -> "MUSCULO"
                else -> "SALUD_GENERAL"
            }
        }
    }

    suspend fun updateAdaptiveTdee(kcal: Float) {
        context.dataStore.edit { prefs -> prefs[Keys.ADAPTIVE_TDEE] = kcal }
    }

    suspend fun setOnboardingCompleted(done: Boolean) {
        context.dataStore.edit { prefs -> prefs[Keys.ONBOARDING_DONE] = done }
    }

    suspend fun updateWaterTarget(targetMl: Int) {
        context.dataStore.edit { prefs ->
            prefs[Keys.WATER_TARGET_ML] = targetMl
        }
    }

    suspend fun resetToDefaults() {
        context.dataStore.edit { prefs ->
            prefs[Keys.STEPS_TARGET]           = 10000
            prefs[Keys.SLEEP_TARGET]           = 8.0f
            prefs[Keys.CALORIES_TARGET]        = 2200f
            prefs[Keys.ACTIVE_CALORIES_TARGET] = 500f
            prefs[Keys.WORKOUT_DAYS_TARGET]    = 4
            prefs[Keys.WORKOUT_MINUTES_TARGET] = 180
            prefs[Keys.FITNESS_LEVEL]          = "INTERMEDIO"
            prefs[Keys.PRIMARY_GOAL]           = "SALUD_GENERAL"
            prefs[Keys.SHOW_HERO_RINGS]        = true
            prefs[Keys.SHOW_AI_INSIGHT]        = true
            prefs[Keys.SHOW_UPCOMING_EVENTS]   = true
            prefs[Keys.DAILY_REMINDERS]        = true
            prefs[Keys.HYDRATION_REMINDER]     = false
            prefs[Keys.INACTIVITY_ALERTS]      = false
            prefs[Keys.EVENING_REVIEW]         = true
            prefs[Keys.AI_STYLE]               = "CIENTIFICO"
            prefs[Keys.SYNC_FREQUENCY]         = "30_MIN"
            prefs[Keys.THEME_MODE]             = "OSCURO"
            prefs[Keys.BEDTIME_REMINDER]       = true
            prefs[Keys.WAKE_UP_HOUR]           = 8
            prefs[Keys.WAKE_UP_MINUTE]         = 30
            prefs[Keys.BEDTIME_MINUTES_BEFORE] = 30
            prefs[Keys.PROTEIN_TARGET]         = 140f
            prefs[Keys.CARBS_TARGET]           = 250f
            prefs[Keys.FAT_TARGET]             = 70f
            prefs[Keys.WATER_TARGET_ML]        = 2500
        }
    }
}
