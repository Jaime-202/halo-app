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

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    var openDialog by remember { mutableStateOf<SettingsDialog?>(null) }
    var reminderToEdit by remember { mutableStateOf<ReminderEntity?>(null) }

    // Copia de seguridad: primero se pide la contraseña y después dónde guardar / qué abrir
    var backupPasswordMode by remember { mutableStateOf<String?>(null) } // "CREAR" | "RESTAURAR"
    var pendingPassword by remember { mutableStateOf("") }
    val createBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri -> if (uri != null) viewModel.createBackup(uri, pendingPassword); pendingPassword = "" }
    val restoreBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) viewModel.restoreBackup(uri, pendingPassword); pendingPassword = "" }
    val exportCsvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri -> if (uri != null) viewModel.exportCsv(uri) }

    val healthPermissions = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(DistanceRecord::class),
        HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),
        // Para la recuperación (opcionales: si no se conceden, se usa solo el sueño)
        HealthPermission.getReadPermission(androidx.health.connect.client.records.RestingHeartRateRecord::class),
        HealthPermission.getReadPermission(androidx.health.connect.client.records.HeartRateRecord::class),
        HealthPermission.getReadPermission(androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord::class),
    )
    val coreHealthPermissions = healthPermissions.take(5).toSet()

    val healthPermissionLauncher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract()
    ) { grantedPermissions ->
        if (grantedPermissions.containsAll(coreHealthPermissions)) {
            viewModel.onHealthConnectConnect(context)
        } else {
            Toast.makeText(context, "Permisos de salud parciales o denegados", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.checkPermissions(context)
    }

    val calendarPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.onCalendarPermissionGranted(context)
        } else {
            Toast.makeText(context, "Permiso de calendario denegado", Toast.LENGTH_SHORT).show()
        }
    }

    val actions = remember(viewModel) {
        SettingsActions(
            open = { openDialog = it },
            editReminder = { reminderToEdit = it; openDialog = SettingsDialog.AddEditReminder },
            askBackupPassword = { backupPasswordMode = it },
            connectHealth = { healthPermissionLauncher.launch(healthPermissions) },
            requestCalendar = { calendarPermissionLauncher.launch(Manifest.permission.READ_CALENDAR) },
            exportCsv = { exportCsvLauncher.launch(viewModel.csvFileName()) },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Obsidian)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 56.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text(
            text       = "Ajustes",
            style      = MaterialTheme.typography.headlineSmall,
            color      = White,
            fontWeight = FontWeight.Bold
        )
        ProfileSection(state, viewModel, actions)
        GoalsSection(state, viewModel, actions)
        AutoRemindersSection(state, viewModel, actions)
        MyRemindersSection(state, viewModel, actions)
        GeminiSection(state, viewModel, actions)
        HomeScreenSection(state, viewModel, actions)
        ConnectionsSection(state, viewModel, actions)
        ThemeSection(state, viewModel, actions)
        CaloriesSection(state, viewModel, actions)
        DataSection(state, viewModel, actions)
        InfoSection(state, viewModel, actions)
    }

    // ─── Modales ──────────────────────────────────────────────────────────

    backupPasswordMode?.let { mode ->
        BackupPasswordDialog(
            creating  = mode == "CREAR",
            onDismiss = { backupPasswordMode = null },
            onConfirm = { password ->
                pendingPassword = password
                backupPasswordMode = null
                if (mode == "CREAR") createBackupLauncher.launch(viewModel.backupFileName())
                else restoreBackupLauncher.launch(arrayOf("*/*"))
            }
        )
    }

    if (openDialog == SettingsDialog.Profile) {
        EnhancedProfileModal(
            currentName   = state.userName,
            currentLevel  = state.fitnessLevel,
            currentGoal   = state.primaryGoal,
            currentWeight = state.weightKg,
            currentHeight = state.heightCm,
            onDismiss     = { openDialog = null },
            onSave        = { name, level, goal, weight, height ->
                viewModel.updateProfile(name, level, goal, weight, height)
                openDialog = null
                Toast.makeText(context, "Perfil biométrico actualizado", Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (openDialog == SettingsDialog.ApiKey) {
        EditApiKeyModal(
            currentKey = state.geminiApiKey,
            currentModel = state.geminiModel,
            onDismiss  = { openDialog = null },
            onTest     = { key, model -> viewModel.testApiKey(key, model) },
            onSave     = { newKey, newModel ->
                viewModel.updateGeminiApiKey(newKey)
                viewModel.updateGeminiModel(newModel)
                openDialog = null
                Toast.makeText(context, "Clave y modelo ($newModel) guardados", Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (openDialog == SettingsDialog.Steps) {
        EditStepsGoalModal(
            currentValue = state.stepsTarget,
            onDismiss    = { openDialog = null },
            onSave       = { newSteps ->
                viewModel.updateStepsTarget(newSteps)
                openDialog = null
                Toast.makeText(context, "Objetivo de pasos actualizado", Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (openDialog == SettingsDialog.Sleep) {
        EditSleepGoalModal(
            currentValue = state.sleepTargetHours,
            onDismiss    = { openDialog = null },
            onSave       = { newSleep ->
                viewModel.updateSleepTarget(newSleep)
                openDialog = null
                Toast.makeText(context, "Objetivo de sueño actualizado", Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (openDialog == SettingsDialog.Calories) {
        EditCaloriesGoalModal(
            currentValue = state.caloriesTarget,
            onDismiss    = { openDialog = null },
            onSave       = { newCal ->
                viewModel.updateActiveCaloriesTarget(newCal)
                openDialog = null
                Toast.makeText(context, "Objetivo de calorías activas actualizado", Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (openDialog == SettingsDialog.Workout) {
        EditWorkoutGoalModal(
            currentDays    = state.workoutDaysTarget,
            currentMinutes = state.workoutMinutesTarget,
            onDismiss      = { openDialog = null },
            onSave         = { days, minutes ->
                viewModel.updateWorkoutTargets(days, minutes)
                openDialog = null
                Toast.makeText(context, "Meta de entrenamiento actualizada", Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (openDialog == SettingsDialog.AddWeight) {
        AddWeightEntryModal(
            currentWeight = state.weightHistory.firstOrNull()?.weightKg ?: state.weightKg,
            onDismiss     = { openDialog = null },
            onSave        = { weight, note ->
                viewModel.logWeight(weight, note)
                openDialog = null
                Toast.makeText(context, "Pesaje registrado con éxito", Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (openDialog == SettingsDialog.WeightHistory) {
        WeightHistoryModal(
            history   = state.weightHistory,
            onDismiss = { openDialog = null },
            onDelete  = { id ->
                viewModel.deleteWeightEntry(id)
                Toast.makeText(context, "Registro eliminado", Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (openDialog == SettingsDialog.WakeUpTime) {
        EditWakeUpTimeModal(
            currentHour   = state.wakeUpHour,
            currentMinute = state.wakeUpMinute,
            onDismiss     = { openDialog = null },
            onSave        = { h, m ->
                viewModel.updateWakeUpTime(h, m)
                openDialog = null
                Toast.makeText(context, "Hora para levantarse actualizada", Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (openDialog == SettingsDialog.Macros) {
        EditMacrosGoalModal(
            currentProtein = state.proteinTargetGrams,
            currentCarbs   = state.carbsTargetGrams,
            currentFat     = state.fatTargetGrams,
            onDismiss      = { openDialog = null },
            onSave         = { p, c, f ->
                viewModel.updateMacroTargets(p, c, f)
                openDialog = null
                Toast.makeText(context, "Objetivos de macronutrientes actualizados", Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (openDialog == SettingsDialog.AddEditReminder) {
        AddEditReminderModal(
            reminder = reminderToEdit,
            onDismiss = {
                openDialog = null
                reminderToEdit = null
            },
            onSave = { title, type, daysOfWeek, timeOfDay, iconEmoji ->
                if (reminderToEdit != null) {
                    viewModel.updateReminder(
                        reminderToEdit!!.copy(
                            title = title,
                            type = type,
                            daysOfWeek = daysOfWeek,
                            timeOfDay = timeOfDay,
                            iconEmoji = iconEmoji
                        )
                    )
                    Toast.makeText(context, "Recordatorio actualizado", Toast.LENGTH_SHORT).show()
                } else {
                    viewModel.addReminder(title, type, daysOfWeek, timeOfDay, iconEmoji)
                    Toast.makeText(context, "Recordatorio creado", Toast.LENGTH_SHORT).show()
                }
                openDialog = null
                reminderToEdit = null
            }
        )
    }

    if (openDialog == SettingsDialog.Water) {
        EditWaterGoalModal(
            currentValue = state.waterTargetMl,
            onDismiss    = { openDialog = null },
            onSave       = { amount ->
                viewModel.updateWaterTarget(amount)
                openDialog = null
                Toast.makeText(context, "Objetivo de agua actualizado", Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (openDialog == SettingsDialog.ResetConfirm) {
        ResetDefaultsDialog(
            onDismiss = { openDialog = null },
            onConfirm = {
                viewModel.resetToDefaults()
                openDialog = null
            }
        )
    }
}
