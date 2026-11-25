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

// Secciones de Ajustes. Cada una recibe el estado, el ViewModel y las acciones
// que necesitan algo de la pantalla (abrir diálogos, pedir permisos, elegir archivos).

/** Diálogos que puede abrir Ajustes. Solo hay uno abierto a la vez. */
enum class SettingsDialog {
    Profile, ApiKey, Steps, Sleep, Calories, Workout, AddWeight, WeightHistory,
    WakeUpTime, Macros, AddEditReminder, Water, ResetConfirm
}

/** Acciones que las secciones piden a la pantalla. */
class SettingsActions(
    val open: (SettingsDialog) -> Unit,
    val editReminder: (ReminderEntity?) -> Unit,
    val askBackupPassword: (String) -> Unit,
    val connectHealth: () -> Unit,
    val requestCalendar: () -> Unit,
    val exportCsv: () -> Unit,
)

@Composable
internal fun ProfileSection(state: SettingsState, viewModel: SettingsViewModel, actions: SettingsActions) {
    val context = LocalContext.current
    // ─── Tarjeta de Perfil Avanzada ──────────────────────────────────
    EnhancedUserProfileCard(
        userName      = state.userName,
        fitnessLevel  = state.fitnessLevel,
        primaryGoal   = state.primaryGoal,
        weightKg      = state.weightHistory.firstOrNull()?.weightKg ?: state.weightKg,
        heightCm      = state.heightCm,
        tdeeKcal      = state.tdeeKcal,
        onOpenProfile = { actions.open(SettingsDialog.Profile) },
        onSyncAll     = { viewModel.forceSyncAll(context) }
    )

    // ─── Seguimiento de Peso & Composición ─────────────────────────────
    WeightTrackerCard(
        latestWeight  = state.weightHistory.firstOrNull()?.weightKg ?: state.weightKg,
        heightCm      = state.heightCm,
        weightHistory = state.weightHistory,
        onAddWeight   = { actions.open(SettingsDialog.AddWeight) },
        onViewHistory = { actions.open(SettingsDialog.WeightHistory) }
    )
}

@Composable
internal fun GoalsSection(state: SettingsState, viewModel: SettingsViewModel, actions: SettingsActions) {
    val context = LocalContext.current
    // ─── Metas & Objetivos Personales (Centralizado) ───────────────────
    SectionHeader("Objetivos")
    SettingsCard {
        SettingsItem(
            icon      = Icons.AutoMirrored.Filled.DirectionsWalk,
            iconColor = RingSteps,
            title     = "Objetivo de Pasos",
            subtitle  = "${state.stepsTarget} pasos al día",
            trailing  = {
                OutlinedButton(
                    onClick = { actions.open(SettingsDialog.Steps) },
                    colors  = ButtonDefaults.outlinedButtonColors(contentColor = Violet300),
                    border  = BorderStroke(1.dp, Violet400),
                    shape   = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(30.dp)
                ) {
                    Text("Modificar", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
            }
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        SettingsItem(
            icon      = Icons.Default.Bedtime,
            iconColor = RingSleep,
            title     = "Objetivo de Sueño",
            subtitle  = "${String.format(Locale.US, "%.1f", state.sleepTargetHours)} horas por noche",
            trailing  = {
                OutlinedButton(
                    onClick = { actions.open(SettingsDialog.Sleep) },
                    colors  = ButtonDefaults.outlinedButtonColors(contentColor = Violet300),
                    border  = BorderStroke(1.dp, Violet400),
                    shape   = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(30.dp)
                ) {
                    Text("Modificar", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
            }
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        SettingsItem(
            icon      = Icons.Default.LocalFireDepartment,
            iconColor = Amber400,
            title     = "Objetivo de Calorías Activas",
            subtitle  = "${state.caloriesTarget.toInt()} kcal quemadas / día",
            trailing  = {
                OutlinedButton(
                    onClick = { actions.open(SettingsDialog.Calories) },
                    colors  = ButtonDefaults.outlinedButtonColors(contentColor = Violet300),
                    border  = BorderStroke(1.dp, Violet400),
                    shape   = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(30.dp)
                ) {
                    Text("Modificar", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
            }
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        SettingsItem(
            icon      = Icons.Default.FitnessCenter,
            iconColor = Violet400,
            title     = "Sesiones de Entrenamiento",
            subtitle  = "${state.workoutDaysTarget} días / semana (${state.workoutMinutesTarget} min)",
            trailing  = {
                OutlinedButton(
                    onClick = { actions.open(SettingsDialog.Workout) },
                    colors  = ButtonDefaults.outlinedButtonColors(contentColor = Violet300),
                    border  = BorderStroke(1.dp, Violet400),
                    shape   = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(30.dp)
                ) {
                    Text("Modificar", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
            }
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        SettingsItem(
            icon      = Icons.Default.Restaurant,
            iconColor = Emerald400,
            title     = "Objetivo de Macronutrientes",
            subtitle  = "P: ${state.proteinTargetGrams.toInt()}g · C: ${state.carbsTargetGrams.toInt()}g · G: ${state.fatTargetGrams.toInt()}g",
            trailing  = {
                OutlinedButton(
                    onClick = { actions.open(SettingsDialog.Macros) },
                    colors  = ButtonDefaults.outlinedButtonColors(contentColor = Violet300),
                    border  = BorderStroke(1.dp, Violet400),
                    shape   = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(30.dp)
                ) {
                    Text("Modificar", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
            }
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        SettingsItem(
            icon      = Icons.Default.WaterDrop,
            iconColor = Cyan400,
            title     = "Objetivo Diario de Agua",
            subtitle  = "${state.waterTargetMl} ml al día · Seguimiento visual",
            trailing  = {
                OutlinedButton(
                    onClick = { actions.open(SettingsDialog.Water) },
                    colors  = ButtonDefaults.outlinedButtonColors(contentColor = Violet300),
                    border  = BorderStroke(1.dp, Violet400),
                    shape   = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(30.dp)
                ) {
                    Text("Modificar", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

@Composable
internal fun AutoRemindersSection(state: SettingsState, viewModel: SettingsViewModel, actions: SettingsActions) {
    val context = LocalContext.current
    // ─── Hábitos & Recordatorios Inteligentes ──────────────────────────
    SectionHeader("Avisos automáticos")
    SettingsCard {
        val notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()

        SettingsItem(
            icon      = if (notificationsEnabled) Icons.Default.NotificationsActive else Icons.Default.NotificationsOff,
            iconColor = if (notificationsEnabled) Violet400 else Rose400,
            title     = "Comprobar Notificaciones",
            subtitle  = if (notificationsEnabled) "Prueba el sonido y la alerta en tu móvil"
                        else "Desactivadas en Android · Toca para activar",
            trailing  = {
                if (!notificationsEnabled) {
                    Button(
                        onClick = {
                            val intent = Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                            }
                            context.startActivity(intent)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Rose400),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Text("Activar", color = White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                } else {
                    OutlinedButton(
                        onClick = { viewModel.sendTestNotification() },
                        colors  = ButtonDefaults.outlinedButtonColors(contentColor = Violet300),
                        border  = BorderStroke(1.dp, Violet400),
                        shape   = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Text("Probar", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                }
            }
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        SwitchSettingItem(
            title           = "Recordatorios Diarios",
            subtitle        = "Notificación matutina con el resumen de tus metas",
            checked         = state.dailyReminders,
            onCheckedChange = { viewModel.toggleDailyReminders(it) }
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        SwitchSettingItem(
            title           = "Resumen semanal",
            subtitle        = "El domingo a las 20:00: tu semana y una conclusión de tus datos",
            checked         = state.weeklySummary,
            onCheckedChange = { viewModel.toggleWeeklySummary(it) }
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        SwitchSettingItem(
            title           = "Resumen Nocturno de Recuperación",
            subtitle        = "Consejos para optimizar el sueño según tu día",
            checked         = state.eveningReview,
            onCheckedChange = { viewModel.toggleEveningReview(it) }
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Aviso para Ir a Dormir", style = MaterialTheme.typography.labelLarge, color = White, fontWeight = FontWeight.Bold)
                    Text(
                        "Notificación inteligente 30 min antes de la hora recomendada para dormir",
                        style = MaterialTheme.typography.bodySmall,
                        color = Gray400
                    )
                }
                Switch(
                    checked = state.bedtimeReminderEnabled,
                    onCheckedChange = { viewModel.toggleBedtimeReminder(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor   = Indigo900,
                        checkedTrackColor   = Violet400,
                        uncheckedThumbColor = Gray400,
                        uncheckedTrackColor = Indigo700
                    )
                )
            }

            if (state.bedtimeReminderEnabled) {
                val wakeMinutes = state.wakeUpHour * 60 + state.wakeUpMinute
                val sleepMinutes = (state.sleepTargetHours * 60).toInt()
                var bedtimeMin = (wakeMinutes - sleepMinutes) % 1440
                if (bedtimeMin < 0) bedtimeMin += 1440
                val bedtimeHour = bedtimeMin / 60
                val bedtimeM = bedtimeMin % 60

                var reminderMin = (bedtimeMin - state.bedtimeReminderMinutesBefore) % 1440
                if (reminderMin < 0) reminderMin += 1440
                val reminderHour = reminderMin / 60
                val reminderM = reminderMin % 60

                Spacer(Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Indigo900.copy(alpha = 0.5f))
                        .border(1.dp, Indigo700.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                        .padding(12.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Alarm, contentDescription = null, tint = RingSleep, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "Hora de levantarse: ${String.format(Locale.US, "%02d:%02d", state.wakeUpHour, state.wakeUpMinute)}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = White,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            OutlinedButton(
                                onClick = { actions.open(SettingsDialog.WakeUpTime) },
                                colors  = ButtonDefaults.outlinedButtonColors(contentColor = Violet300),
                                border  = BorderStroke(1.dp, Violet400),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Text("Cambiar", style = MaterialTheme.typography.labelSmall)
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Dormir a las: ${String.format(Locale.US, "%02d:%02d", bedtimeHour, bedtimeM)} (${String.format(Locale.US, "%.1fh", state.sleepTargetHours)})",
                                style = MaterialTheme.typography.bodySmall,
                                color = Gray300
                            )
                            Text(
                                text = "Aviso: ${String.format(Locale.US, "%02d:%02d", reminderHour, reminderM)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Amber400,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun MyRemindersSection(state: SettingsState, viewModel: SettingsViewModel, actions: SettingsActions) {
    val context = LocalContext.current
    // ─── Tomas & Recordatorios Personalizados ───────────────────────────
    SectionHeader("Mis recordatorios")
    SettingsCard {
        SettingsItem(
            icon      = Icons.Default.AlarmOn,
            iconColor = Violet400,
            title     = if (state.reminders.isNotEmpty()) "Tomas (${state.reminders.count { it.isEnabled }} activas)" else "Tomas y Hábitos",
            subtitle  = "Suplementos y hábitos con aviso puntual",
            trailing  = {
                OutlinedButton(
                    onClick = {
                        actions.editReminder(null)
                        actions.open(SettingsDialog.AddEditReminder)
                    },
                    colors  = ButtonDefaults.outlinedButtonColors(contentColor = Violet300),
                    border  = BorderStroke(1.dp, Violet400),
                    shape   = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(30.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Añadir", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
            }
        )

        if (state.reminders.isEmpty()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Sin tomas configuradas · Pulsa 'Añadir' para crear una.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Gray500
                )
            }
        } else {
            state.reminders.forEach { reminder ->
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                ReminderSettingsItemRow(
                    reminder = reminder,
                    onToggle = { enabled -> viewModel.toggleReminderEnabled(reminder.id, enabled) },
                    onEdit = {
                        actions.editReminder(reminder)
                        actions.open(SettingsDialog.AddEditReminder)
                    },
                    onDelete = { viewModel.deleteReminder(reminder.id) }
                )
            }
        }
    }
}

@Composable
internal fun GeminiSection(state: SettingsState, viewModel: SettingsViewModel, actions: SettingsActions) {
    val context = LocalContext.current
    // ─── Inteligencia Artificial & Coach ──────────────────────────────
    SectionHeader("Gemini (foto y texto de comida)")
    SettingsCard {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Estilo de Análisis del Coach", style = MaterialTheme.typography.labelLarge, color = White, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OptionChip("Científico", state.aiStyle == "CIENTIFICO", Modifier.weight(1f)) {
                    viewModel.setAiStyle("CIENTIFICO")
                }
                OptionChip("Motivador", state.aiStyle == "MOTIVADOR", Modifier.weight(1f)) {
                    viewModel.setAiStyle("MOTIVADOR")
                }
                OptionChip("Directo", state.aiStyle == "DIRECTO", Modifier.weight(1f)) {
                    viewModel.setAiStyle("DIRECTO")
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        SettingsItem(
            icon      = Icons.Default.AutoAwesome,
            iconColor = Violet400,
            title     = "Motor IA: ${if (state.geminiModel.isNotBlank()) state.geminiModel else "gemini-3.8-flash"}",
            subtitle  = if (state.geminiApiKey.isNotBlank()) "Clave personalizada activa · Modelo: ${state.geminiModel}"
                        else "Motor Heurístico Local inteligente (100% Offline y Gratis)",
            trailing  = {
                OutlinedButton(
                    onClick = { actions.open(SettingsDialog.ApiKey) },
                    colors  = ButtonDefaults.outlinedButtonColors(contentColor = Violet300),
                    border  = BorderStroke(1.dp, Violet400),
                    shape   = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(30.dp)
                ) {
                    Text(if (state.geminiApiKey.isNotBlank()) "Modificar" else "Añadir API Key", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

@Composable
internal fun HomeScreenSection(state: SettingsState, viewModel: SettingsViewModel, actions: SettingsActions) {
    val context = LocalContext.current
    // ─── Personalización del Panel ─────────────────────────────────────
    SectionHeader("Pantalla Hoy")
    SettingsCard {
        SwitchSettingItem(
            title           = "Próximos eventos",
            subtitle        = "Muestra tus próximos eventos del calendario al final de Hoy",
            checked         = state.showUpcomingEvents,
            onCheckedChange = { viewModel.toggleShowUpcomingEvents(it) }
        )
    }
}

@Composable
internal fun ConnectionsSection(state: SettingsState, viewModel: SettingsViewModel, actions: SettingsActions) {
    val context = LocalContext.current
    // ─── Sincronización & Fuentes de Datos ─────────────────────────────
    SectionHeader("Conexiones")
    SettingsCard {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Frecuencia de Auto-Sincronización", style = MaterialTheme.typography.labelLarge, color = White, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                OptionChip("15 min", state.syncFrequency == "15_MIN", Modifier.weight(1f)) {
                    viewModel.setSyncFrequency("15_MIN")
                }
                OptionChip("30 min", state.syncFrequency == "30_MIN", Modifier.weight(1f)) {
                    viewModel.setSyncFrequency("30_MIN")
                }
                OptionChip("1 hora", state.syncFrequency == "60_MIN", Modifier.weight(1f)) {
                    viewModel.setSyncFrequency("60_MIN")
                }
                OptionChip("Manual", state.syncFrequency == "MANUAL", Modifier.weight(1f)) {
                    viewModel.setSyncFrequency("MANUAL")
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        SettingsItem(
            icon      = Icons.Default.Favorite,
            iconColor = Rose400,
            title     = "Health Connect",
            subtitle  = if (state.healthConnectGranted) "Conectado y sincronizando"
                        else "Toca para vincular Health Connect",
            trailing  = {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = {
                            try {
                                actions.connectHealth()
                            } catch (e: Exception) {
                                openHealthConnectSettings(context)
                            }
                        },
                        colors  = ButtonDefaults.outlinedButtonColors(contentColor = Violet300),
                        border  = BorderStroke(1.dp, Violet400),
                        shape   = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(30.dp)
                    ) { Text("Conectar", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold) }

                    IconButton(
                        onClick = { openHealthConnectSettings(context) },
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = "Ajustes de Salud", tint = Gray400, modifier = Modifier.size(18.dp))
                    }
                }
            }
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        SettingsItem(
            icon      = Icons.Default.CalendarMonth,
            iconColor = Cyan400,
            title     = "Calendario del Dispositivo",
            subtitle  = if (state.calendarGranted) "Sincronizado localmente"
                        else "Permiso READ_CALENDAR requerido",
            trailing  = {
                if (state.calendarGranted) {
                    Icon(Icons.Default.CheckCircle, contentDescription = "Concedido", tint = Emerald400, modifier = Modifier.size(20.dp))
                } else {
                    OutlinedButton(
                        onClick = { actions.requestCalendar() },
                        colors  = ButtonDefaults.outlinedButtonColors(contentColor = Violet300),
                        border  = BorderStroke(1.dp, Violet400),
                        shape   = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(30.dp)
                    ) { Text("Permitir", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold) }
                }
            }
        )
    }
}

@Composable
internal fun ThemeSection(state: SettingsState, viewModel: SettingsViewModel, actions: SettingsActions) {
    val context = LocalContext.current
    // ─── Apariencia & Tema ─────────────────────────────────────────────
    SectionHeader("Tema")
    SettingsCard {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Estilo Visual & Colores", style = MaterialTheme.typography.labelLarge, color = White, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                ThemeOptionChip("Oscuro", Icons.Default.DarkMode, state.themeMode == ThemeMode.OSCURO || state.themeMode == ThemeMode.MIDNIGHT, Modifier.weight(1f)) {
                    viewModel.setThemeMode(ThemeMode.OSCURO)
                }
                ThemeOptionChip("Claro", Icons.Default.LightMode, state.themeMode == ThemeMode.CLARO, Modifier.weight(1f)) {
                    viewModel.setThemeMode(ThemeMode.CLARO)
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                ThemeOptionChip("Sistema", Icons.Default.SettingsSuggest, state.themeMode == ThemeMode.SISTEMA, Modifier.weight(1f)) {
                    viewModel.setThemeMode(ThemeMode.SISTEMA)
                }
                ThemeOptionChip("Negro OLED", Icons.Default.Nightlight, state.themeMode == ThemeMode.AMOLED, Modifier.weight(1f)) {
                    viewModel.setThemeMode(ThemeMode.AMOLED)
                }
            }
        }
    }
}

@Composable
internal fun CaloriesSection(state: SettingsState, viewModel: SettingsViewModel, actions: SettingsActions) {
    val context = LocalContext.current
    // ─── Perfil energético ─────────────────────────────────────────────
    SectionHeader("Calorías")
    SettingsCard {
        SettingsItem(
            icon      = Icons.Default.LocalFireDepartment,
            iconColor = Ion,
            title     = "Mis datos para calcular calorías",
            subtitle  = "Gasto diario ${state.tdeeKcal.toInt()} kcal · basal ${state.bmrKcal.toInt()} kcal",
            trailing  = {
                OutlinedButton(
                    onClick = { viewModel.redoEnergyProfile() },
                    colors  = ButtonDefaults.outlinedButtonColors(contentColor = Ion),
                    border  = BorderStroke(1.dp, Ion),
                    shape   = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(30.dp)
                ) { Text("Editar", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold) }
            }
        )
    }
}

@Composable
internal fun DataSection(state: SettingsState, viewModel: SettingsViewModel, actions: SettingsActions) {
    val context = LocalContext.current
    // ─── Gestión de Datos & Privacidad ─────────────────────────────────
    SectionHeader("Datos")
    SettingsCard {
        SettingsItem(
            icon      = Icons.Default.Lock,
            iconColor = Ion,
            title     = "Copia de seguridad cifrada",
            subtitle  = "Guárdala en Google Drive o donde quieras, protegida con contraseña",
            trailing  = {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        onClick = { actions.askBackupPassword("RESTAURAR") },
                        colors  = ButtonDefaults.outlinedButtonColors(contentColor = TextMute),
                        border  = BorderStroke(1.dp, LineStrong),
                        shape   = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(30.dp)
                    ) { Text("Restaurar", style = MaterialTheme.typography.labelSmall) }
                    OutlinedButton(
                        onClick = { actions.askBackupPassword("CREAR") },
                        colors  = ButtonDefaults.outlinedButtonColors(contentColor = Ion),
                        border  = BorderStroke(1.dp, Ion),
                        shape   = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(30.dp)
                    ) { Text("Crear", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold) }
                }
            }
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        SettingsItem(
            icon      = Icons.Default.Share,
            iconColor = Flux,
            title     = "Exportar a CSV",
            subtitle  = "Pesos, comidas y salud para Excel o Sheets",
            trailing  = {
                OutlinedButton(
                    onClick = { actions.exportCsv() },
                    colors  = ButtonDefaults.outlinedButtonColors(contentColor = Flux),
                    border  = BorderStroke(1.dp, Flux),
                    shape   = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(30.dp)
                ) { Text("Exportar", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold) }
            }
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        SettingsItem(
            icon      = Icons.Default.CleaningServices,
            iconColor = Amber400,
            title     = "Limpiar Caché Temporal",
            subtitle  = "Libera memoria y optimiza el rendimiento",
            trailing  = {
                OutlinedButton(
                    onClick = {
                        context.cacheDir.deleteRecursively()
                        Toast.makeText(context, "Caché temporal liberada", Toast.LENGTH_SHORT).show()
                    },
                    colors  = ButtonDefaults.outlinedButtonColors(contentColor = Amber400),
                    border  = BorderStroke(1.dp, Amber400),
                    shape   = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(30.dp)
                ) { Text("Limpiar", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold) }
            }
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        SettingsItem(
            icon      = Icons.Default.Refresh,
            iconColor = Rose400,
            title     = "Restablecer Ajustes",
            subtitle  = "Restaura metas y opciones a los valores iniciales",
            trailing  = {
                OutlinedButton(
                    onClick = { actions.open(SettingsDialog.ResetConfirm) },
                    colors  = ButtonDefaults.outlinedButtonColors(contentColor = Rose400),
                    border  = BorderStroke(1.dp, Rose400),
                    shape   = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(30.dp)
                ) { Text("Restablecer", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold) }
            }
        )
    }
}

@Composable
internal fun InfoSection(state: SettingsState, viewModel: SettingsViewModel, actions: SettingsActions) {
    val context = LocalContext.current
    // ─── Información de la App ─────────────────────────────────────────
    SectionHeader("Información")
    SettingsCard {
        SettingsItem(
            icon      = Icons.Default.Info,
            iconColor = Gray400,
            title     = "Halo",
            subtitle  = "v2.0 · Tu día, tu historial y tu comida",
            trailing  = {}
        )
    }
}
