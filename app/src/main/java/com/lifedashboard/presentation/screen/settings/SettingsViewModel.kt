package com.lifedashboard.presentation.screen.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lifedashboard.core.notification.NotificationHelper
import com.lifedashboard.core.notification.ReminderScheduler
import com.lifedashboard.core.worker.WorkManagerScheduler
import com.lifedashboard.data.local.dao.ReminderDao
import com.lifedashboard.data.local.dao.WeightDao
import com.lifedashboard.data.local.entity.ReminderEntity
import com.lifedashboard.data.local.entity.ReminderType
import com.lifedashboard.data.local.entity.WeightEntryEntity
import com.lifedashboard.data.repository.UserPreferencesRepository
import com.lifedashboard.domain.repository.CalendarRepository
import com.lifedashboard.presentation.common.components.WeightEvolutionChart
import com.lifedashboard.presentation.theme.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

// Estado y lógica de Ajustes.

enum class ThemeMode { OSCURO, CLARO, SISTEMA, AMOLED, MIDNIGHT }

// ─── ViewModel ────────────────────────────────────────────────────────────────

data class SettingsState(
    val userName: String = "Jaime",
    val fitnessLevel: String = "INTERMEDIO",
    val primaryGoal: String = "SALUD_GENERAL",
    val weightKg: Float = 75.0f,
    val heightCm: Int = 175,
    val themeMode: ThemeMode = ThemeMode.OSCURO,
    val showHeroRings: Boolean = true,
    val showAiInsight: Boolean = true,
    val showUpcomingEvents: Boolean = true,
    val dailyReminders: Boolean = true,
    val hydrationReminder: Boolean = false,
    val inactivityAlerts: Boolean = false,
    val eveningReview: Boolean = true,
    val aiStyle: String = "CIENTIFICO",
    val syncFrequency: String = "30_MIN",
    val geminiApiKey: String = "",
    val geminiModel: String = "gemini-3.8-flash",
    val stepsTarget: Int = 10000,
    val sleepTargetHours: Float = 8.0f,
    val caloriesTarget: Float = 500f, // Meta de calorías activas quemadas
    val workoutDaysTarget: Int = 4,
    val workoutMinutesTarget: Int = 180,
    val healthConnectGranted: Boolean = false,
    val calendarGranted: Boolean = false,
    val weightHistory: List<WeightEntryEntity> = emptyList(),
    val bedtimeReminderEnabled: Boolean = true,
    val wakeUpHour: Int = 8,
    val wakeUpMinute: Int = 30,
    val bedtimeReminderMinutesBefore: Int = 30,
    val proteinTargetGrams: Float = 140f,
    val carbsTargetGrams: Float = 250f,
    val fatTargetGrams: Float = 70f,
    val waterTargetMl: Int = 2500,
    val bmrKcal: Float = 1750f,
    val tdeeKcal: Float = 2350f,
    val reminders: List<ReminderEntity> = emptyList(),
    val weeklySummary: Boolean = true
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val scheduler: WorkManagerScheduler,
    private val calendarRepository: CalendarRepository,
    private val userPreferences: UserPreferencesRepository,
    private val weightDao: WeightDao,
    private val reminderDao: ReminderDao,
    private val foodAiService: com.lifedashboard.data.remote.food.FoodAiService,
    private val energyTargets: com.lifedashboard.data.repository.EnergyTargetsManager,
    private val backupManager: com.lifedashboard.data.backup.BackupManager,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context
) : ViewModel() {
    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    init {
        userPreferences.userGoals
            .onEach { goals ->
                val currentTheme = when (goals.themeMode) {
                    "AMOLED"  -> ThemeMode.AMOLED
                    "CLARO"   -> ThemeMode.CLARO
                    "SISTEMA" -> ThemeMode.SISTEMA
                    else      -> ThemeMode.OSCURO
                }
                val targets = energyTargets.targetsFor(goals)
                val bmr = targets.bmrKcal
                val tdee = targets.tdeeKcal

                _state.value = _state.value.copy(
                    userName             = goals.userName,
                    fitnessLevel         = goals.fitnessLevel,
                    primaryGoal          = goals.primaryGoal,
                    weightKg             = goals.weightKg,
                    heightCm             = goals.heightCm,
                    themeMode            = currentTheme,
                    showHeroRings        = goals.showHeroRings,
                    showAiInsight        = goals.showAiInsight,
                    showUpcomingEvents   = goals.showUpcomingEvents,
                    dailyReminders       = goals.dailyReminders,
                    hydrationReminder    = goals.hydrationReminder,
                    inactivityAlerts     = goals.inactivityAlerts,
                    eveningReview        = goals.eveningReview,
                    aiStyle              = goals.aiStyle,
                    syncFrequency        = goals.syncFrequency,
                    geminiApiKey         = goals.geminiApiKey,
                    geminiModel          = goals.geminiModel,
                    stepsTarget          = goals.stepsTarget,
                    sleepTargetHours     = goals.sleepTargetHours,
                    caloriesTarget       = goals.activeCaloriesTarget,
                    workoutDaysTarget    = goals.workoutDaysTarget,
                    workoutMinutesTarget = goals.workoutMinutesTarget,
                    bedtimeReminderEnabled = goals.bedtimeReminderEnabled,
                    wakeUpHour           = goals.wakeUpHour,
                    wakeUpMinute         = goals.wakeUpMinute,
                    bedtimeReminderMinutesBefore = goals.bedtimeReminderMinutesBefore,
                    proteinTargetGrams   = goals.proteinTargetGrams,
                    carbsTargetGrams     = goals.carbsTargetGrams,
                    fatTargetGrams       = goals.fatTargetGrams,
                    waterTargetMl        = goals.waterTargetMl,
                    weeklySummary        = goals.weeklySummary,
                    bmrKcal              = bmr,
                    tdeeKcal             = tdee
                )
            }
            .launchIn(viewModelScope)

        weightDao.observeAllWeights()
            .onEach { weights ->
                _state.value = _state.value.copy(weightHistory = weights)
            }
            .launchIn(viewModelScope)

        reminderDao.observeAll()
            .onEach { list ->
                _state.value = _state.value.copy(reminders = list)
            }
            .launchIn(viewModelScope)
    }

    fun toggleBedtimeReminder(enabled: Boolean) {
        viewModelScope.launch {
            userPreferences.updateBedtimeReminder(enabled)
            if (enabled) {
                scheduler.scheduleBedtimeReminder(
                    wakeUpHour = _state.value.wakeUpHour,
                    wakeUpMinute = _state.value.wakeUpMinute,
                    sleepTargetHours = _state.value.sleepTargetHours,
                    minutesBefore = _state.value.bedtimeReminderMinutesBefore
                )
            } else {
                scheduler.cancelBedtimeReminder()
            }
        }
    }

    fun updateWakeUpTime(hour: Int, minute: Int) {
        viewModelScope.launch {
            userPreferences.updateWakeUpTime(hour, minute)
            if (_state.value.bedtimeReminderEnabled) {
                scheduler.scheduleBedtimeReminder(
                    wakeUpHour = hour,
                    wakeUpMinute = minute,
                    sleepTargetHours = _state.value.sleepTargetHours,
                    minutesBefore = _state.value.bedtimeReminderMinutesBefore
                )
            }
        }
    }

    fun logWeight(weight: Float, note: String?) {
        if (weight <= 0f) return
        viewModelScope.launch {
            val entry = WeightEntryEntity(
                dateMillis = System.currentTimeMillis(),
                weightKg = weight,
                note = note?.takeIf { it.isNotBlank() }
            )
            weightDao.upsert(entry)
            val currentGoal = _state.value.primaryGoal
            val currentHeight = _state.value.heightCm
            userPreferences.updateFitnessProfile(
                level = _state.value.fitnessLevel,
                goal = currentGoal,
                weight = weight,
                height = currentHeight
            )

            // Recalcular calorías y macros (incluye TDEE adaptativo con el nuevo pesaje)
            energyTargets.recalculate()
            com.lifedashboard.core.worker.WidgetUpdateWorker.updateAllWidgets(context)
        }
    }

    fun deleteWeightEntry(id: Long) {
        viewModelScope.launch {
            weightDao.deleteById(id)
        }
    }

    fun updateProfile(name: String, level: String, goal: String, weight: Float, height: Int) {
        viewModelScope.launch {
            if (name.isNotBlank()) userPreferences.updateUserName(name.trim())
            userPreferences.updateFitnessProfile(level, goal, weight, height)

            energyTargets.recalculate()
            com.lifedashboard.core.worker.WidgetUpdateWorker.updateAllWidgets(context)
        }
    }

    fun updateGeminiApiKey(apiKey: String) {
        viewModelScope.launch { userPreferences.updateGeminiApiKey(apiKey.trim()) }
    }

    fun updateGeminiModel(model: String) {
        viewModelScope.launch { userPreferences.updateGeminiModel(model.trim()) }
    }

    suspend fun testApiKey(apiKey: String, preferredModel: String? = null): Result<String> {
        return foodAiService.testApiKeyConnection(apiKey, preferredModel ?: _state.value.geminiModel)
    }

    fun updateStepsTarget(target: Int) {
        if (target > 0) {
            viewModelScope.launch {
                userPreferences.updateStepsTarget(target)
                com.lifedashboard.core.worker.WidgetUpdateWorker.updateAllWidgets(context)
            }
        }
    }

    fun updateSleepTarget(target: Float) {
        if (target > 0f) {
            viewModelScope.launch {
                userPreferences.updateSleepTarget(target)
                com.lifedashboard.core.worker.WidgetUpdateWorker.updateAllWidgets(context)
            }
        }
    }

    fun updateActiveCaloriesTarget(target: Float) {
        if (target > 0f) {
            viewModelScope.launch {
                userPreferences.updateActiveCaloriesTarget(target)
                com.lifedashboard.core.worker.WidgetUpdateWorker.updateAllWidgets(context)
            }
        }
    }

    fun updateCaloriesTarget(target: Float) {
        updateActiveCaloriesTarget(target)
    }

    fun updateWorkoutTargets(days: Int, minutes: Int) {
        viewModelScope.launch {
            if (days > 0) userPreferences.updateWorkoutDaysTarget(days)
            if (minutes > 0) userPreferences.updateWorkoutMinutesTarget(minutes)
            com.lifedashboard.core.worker.WidgetUpdateWorker.updateAllWidgets(context)
        }
    }

    fun updateMacroTargets(protein: Float, carbs: Float, fat: Float) {
        viewModelScope.launch {
            userPreferences.updateMacroTargets(protein, carbs, fat)
            com.lifedashboard.core.worker.WidgetUpdateWorker.updateAllWidgets(context)
        }
    }

    fun checkPermissions(context: Context) {
        val calendarPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED

        val healthStatus = try {
            HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE
        } catch (e: Exception) {
            false
        }

        _state.value = _state.value.copy(
            calendarGranted = calendarPermission,
            healthConnectGranted = healthStatus
        )
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            userPreferences.updateThemeMode(mode.name)
        }
    }

    fun toggleShowHeroRings(enabled: Boolean) {
        viewModelScope.launch { userPreferences.updateShowHeroRings(enabled) }
    }

    fun toggleShowAiInsight(enabled: Boolean) {
        viewModelScope.launch { userPreferences.updateShowAiInsight(enabled) }
    }

    fun toggleShowUpcomingEvents(enabled: Boolean) {
        viewModelScope.launch { userPreferences.updateShowUpcomingEvents(enabled) }
    }

    fun toggleDailyReminders(enabled: Boolean) {
        viewModelScope.launch {
            userPreferences.updateDailyReminders(enabled)
            if (enabled) {
                scheduler.scheduleDailySummaryReminder(_state.value.wakeUpHour, _state.value.wakeUpMinute)
            } else {
                scheduler.cancelDailySummaryReminder()
            }
        }
    }

    fun toggleHydrationReminder(enabled: Boolean) {
        viewModelScope.launch {
            userPreferences.updateHydrationReminder(enabled)
            if (enabled) {
                scheduler.scheduleHydrationReminder()
            } else {
                scheduler.cancelHydrationReminder()
            }
        }
    }

    fun sendTestNotification() {
        NotificationHelper.showTestReminderNotification(context)
        Toast.makeText(context, "Notificación de prueba enviada con sonido", Toast.LENGTH_SHORT).show()
    }

    fun toggleInactivityAlerts(enabled: Boolean) {
        viewModelScope.launch { userPreferences.updateInactivityAlerts(enabled) }
    }

    fun toggleWeeklySummary(enabled: Boolean) {
        viewModelScope.launch {
            userPreferences.updateWeeklySummary(enabled)
            if (enabled) scheduler.scheduleWeeklySummary() else scheduler.cancelWeeklySummary()
        }
    }

    fun toggleEveningReview(enabled: Boolean) {
        viewModelScope.launch {
            userPreferences.updateEveningReview(enabled)
            if (enabled) {
                scheduler.scheduleEveningReviewReminder()
            } else {
                scheduler.cancelEveningReviewReminder()
            }
        }
    }

    fun setAiStyle(style: String) {
        viewModelScope.launch { userPreferences.updateAiStyle(style) }
    }

    fun setSyncFrequency(freq: String) {
        viewModelScope.launch {
            userPreferences.updateSyncFrequency(freq)
            scheduler.scheduleHealthConnectSync(freq)
        }
    }

    fun updateWaterTarget(targetMl: Int) {
        if (targetMl in 500..10000) {
            viewModelScope.launch {
                userPreferences.updateWaterTarget(targetMl)
                com.lifedashboard.core.worker.WidgetUpdateWorker.updateAllWidgets(context)
            }
        }
    }

    fun resetToDefaults() {
        viewModelScope.launch {
            userPreferences.resetToDefaults()
            scheduler.scheduleAll()
            com.lifedashboard.core.worker.WidgetUpdateWorker.updateAllWidgets(context)
            Toast.makeText(context, "Ajustes restablecidos a los valores por defecto", Toast.LENGTH_SHORT).show()
        }
    }

    fun onHealthConnectConnect(context: Context) {
        _state.value = _state.value.copy(healthConnectGranted = true)
        scheduler.runHealthSyncNow()
        Toast.makeText(context, "Sincronizando con Health Connect", Toast.LENGTH_SHORT).show()
    }

    fun onCalendarPermissionGranted(context: Context) {
        _state.value = _state.value.copy(calendarGranted = true)
        CoroutineScope(Dispatchers.IO).launch {
            calendarRepository.syncCalendarEvents()
        }
        Toast.makeText(context, "Calendario sincronizado", Toast.LENGTH_SHORT).show()
    }

    fun forceSyncAll(context: Context) {
        scheduler.runHealthSyncNow()
        CoroutineScope(Dispatchers.IO).launch {
            calendarRepository.syncCalendarEvents()
        }
        Toast.makeText(context, "Sincronizando fuentes de datos…", Toast.LENGTH_SHORT).show()
    }

    // ─── Copia de seguridad y exportación ─────────────────────────────────────

    fun backupFileName() = backupManager.suggestedBackupName()
    fun csvFileName() = backupManager.suggestedCsvName()

    fun createBackup(uri: android.net.Uri, password: String) {
        viewModelScope.launch {
            val msg = try {
                backupManager.createBackup(uri, password.toCharArray())
                "Copia cifrada guardada"
            } catch (e: Exception) {
                "No se pudo guardar la copia: ${e.message}"
            }
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
        }
    }

    fun restoreBackup(uri: android.net.Uri, password: String) {
        viewModelScope.launch {
            try {
                backupManager.restoreBackup(uri, password.toCharArray())
                Toast.makeText(context, "Copia restaurada. Reiniciando Halo…", Toast.LENGTH_SHORT).show()
                kotlinx.coroutines.delay(800)
                backupManager.restartApp()
            } catch (e: Exception) {
                Toast.makeText(context, e.message ?: "No se pudo restaurar la copia", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun exportCsv(uri: android.net.Uri) {
        viewModelScope.launch {
            val msg = try {
                backupManager.exportCsv(uri)
                "Datos exportados en CSV"
            } catch (e: Exception) {
                "No se pudo exportar: ${e.message}"
            }
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
        }
    }

    /** Vuelve a mostrar la bienvenida para cambiar sexo, edad, actividad u objetivo. */
    fun redoEnergyProfile() {
        viewModelScope.launch { userPreferences.setOnboardingCompleted(false) }
    }

    fun exportData(context: Context) {
        val summary = """
            {
              "user": "${_state.value.userName}",
              "fitness_level": "${_state.value.fitnessLevel}",
              "primary_goal": "${_state.value.primaryGoal}",
              "weight_kg": ${_state.value.weightKg},
              "height_cm": ${_state.value.heightCm},
              "targets": {
                "steps": ${_state.value.stepsTarget},
                "sleep_hours": ${_state.value.sleepTargetHours},
                "calories": ${_state.value.caloriesTarget},
                "workout_days": ${_state.value.workoutDaysTarget}
              }
            }
        """.trimIndent()
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, summary)
            type = "application/json"
        }
        context.startActivity(Intent.createChooser(sendIntent, "Exportar Datos de Life Dashboard"))
    }

    fun addReminder(
        title: String,
        type: String,
        daysOfWeek: String,
        timeOfDay: String,
        iconEmoji: String
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val entity = ReminderEntity(
                title = title.trim(),
                type = type,
                daysOfWeek = daysOfWeek,
                timeOfDay = timeOfDay,
                iconEmoji = iconEmoji,
                isEnabled = true
            )
            val newId = reminderDao.insert(entity)
            val savedReminder = entity.copy(id = newId)
            ReminderScheduler.scheduleReminder(context, savedReminder)
        }
    }

    fun updateReminder(reminder: ReminderEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            reminderDao.update(reminder)
            ReminderScheduler.scheduleReminder(context, reminder)
        }
    }

    fun toggleReminderEnabled(id: Long, enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            reminderDao.setEnabled(id, enabled)
            val reminder = _state.value.reminders.find { it.id == id }?.copy(isEnabled = enabled)
            if (reminder != null) {
                if (enabled) {
                    ReminderScheduler.scheduleReminder(context, reminder)
                } else {
                    ReminderScheduler.cancelReminder(context, id)
                }
            }
        }
    }

    fun deleteReminder(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            reminderDao.deleteReminderAndLogs(id)
            ReminderScheduler.cancelReminder(context, id)
        }
    }
}

// ─── Screen ───────────────────────────────────────────────────────────────────
