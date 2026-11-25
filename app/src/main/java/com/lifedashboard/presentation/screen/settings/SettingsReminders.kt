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

// Mis recordatorios: fila y diálogo para crear o editar.

@Composable
internal fun ReminderSettingsItemRow(
    reminder: ReminderEntity,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val subtitle = when (reminder.type) {
        ReminderType.DIARIO -> "Diario · Aviso a las ${reminder.timeOfDay}"
        ReminderType.HORARIO -> "Diario a las ${reminder.timeOfDay}"
        ReminderType.SEMANAL -> {
            val daysMap = mapOf(
                1 to "L", 2 to "M", 3 to "X", 4 to "J", 5 to "V", 6 to "S", 7 to "D"
            )
            val dayNames = reminder.daysOfWeek.split(",")
                .mapNotNull { it.trim().toIntOrNull() }
                .mapNotNull { daysMap[it] }
                .joinToString(", ")
            if (dayNames.isNotBlank()) "Semanal ($dayNames) · ${reminder.timeOfDay}" else "Semanal · ${reminder.timeOfDay}"
        }
        else -> "Aviso a las ${reminder.timeOfDay}"
    }

    Row(
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier              = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier          = Modifier.weight(1f)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (reminder.isEnabled) Violet400.copy(alpha = 0.15f) else Indigo900.copy(alpha = 0.4f))
            ) {
                Text(text = reminder.iconEmoji.ifBlank { "💊" }, fontSize = 16.sp)
            }

            Spacer(Modifier.width(10.dp))

            Column {
                Text(
                    text = reminder.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (reminder.isEnabled) White else Gray500,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(1.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (reminder.isEnabled) Icons.Default.NotificationsActive else Icons.Default.NotificationsOff,
                        contentDescription = null,
                        tint = if (reminder.isEnabled) Amber400 else Gray500,
                        modifier = Modifier.size(11.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (reminder.isEnabled) Gray300 else Gray500,
                        fontSize = 11.sp
                    )
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(
                checked = reminder.isEnabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor   = Indigo900,
                    checkedTrackColor   = Violet400,
                    uncheckedThumbColor = Gray400,
                    uncheckedTrackColor = Indigo700
                ),
                modifier = Modifier.scale(0.85f)
            )

            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = "Eliminar",
                    tint = Rose400.copy(alpha = 0.7f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
internal fun AddEditReminderModal(
    reminder: ReminderEntity?,
    onDismiss: () -> Unit,
    onSave: (title: String, type: String, daysOfWeek: String, timeOfDay: String, iconEmoji: String) -> Unit
) {
    var title by remember { mutableStateOf(reminder?.title ?: "") }
    var type by remember { mutableStateOf(reminder?.type ?: ReminderType.DIARIO) }
    var selectedDays by remember {
        val initial = reminder?.daysOfWeek?.split(",")?.mapNotNull { it.trim().toIntOrNull() }?.toSet()
            ?: setOf(1, 2, 3, 4, 5, 6, 7)
        mutableStateOf(initial)
    }
    var timeOfDay by remember { mutableStateOf(reminder?.timeOfDay ?: "09:00") }
    var iconEmoji by remember { mutableStateOf(reminder?.iconEmoji ?: "💊") }

    val emojis = listOf("💊", "💧", "🏋️", "🥗", "⏰", "🌿", "⚡", "🧘", "☕", "🍎")
    val daysMap = listOf(
        1 to "L", 2 to "M", 3 to "X", 4 to "J", 5 to "V", 6 to "S", 7 to "D"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Indigo800,
        shape = RoundedCornerShape(22.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (reminder != null) Icons.Default.Edit else Icons.Default.AddCircle,
                    contentDescription = null,
                    tint = Violet300,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (reminder != null) "Editar Recordatorio" else "Nueva Toma / Recordatorio",
                    color = White,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Título
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Nombre o hábito (ej. Creatina 5g)", color = Violet300) },
                    placeholder = { Text("ej. Tomar magnesio, Creatina...", color = Gray500) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Violet400,
                        unfocusedBorderColor = Indigo600,
                        focusedTextColor = White,
                        unfocusedTextColor = White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Selector de Emoji
                Text("Icono / Emoji:", style = MaterialTheme.typography.labelSmall, color = Gray400)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                ) {
                    emojis.forEach { emoji ->
                        val isSelected = iconEmoji == emoji
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isSelected) Violet400.copy(alpha = 0.35f) else Indigo900.copy(alpha = 0.6f)
                                )
                                .border(
                                    1.dp,
                                    if (isSelected) Violet400 else Indigo700,
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable { iconEmoji = emoji }
                        ) {
                            Text(text = emoji, fontSize = 16.sp)
                        }
                    }
                }

                // Frecuencia: Diario, Semanal, Horario
                Text("Frecuencia:", style = MaterialTheme.typography.labelSmall, color = Gray400)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val types = listOf(
                        ReminderType.DIARIO to "Diario",
                        ReminderType.SEMANAL to "Semanal",
                        ReminderType.HORARIO to "Horario"
                    )
                    types.forEach { (t, label) ->
                        val isSelected = type == t
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isSelected) Violet400 else Indigo900.copy(alpha = 0.7f)
                                )
                                .border(
                                    1.dp,
                                    if (isSelected) Violet300 else Indigo700,
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable { type = t }
                                .padding(vertical = 6.dp)
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isSelected) Indigo900 else White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Días activos de la semana (si es Semanal)
                if (type == ReminderType.SEMANAL) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Días activos de la semana:", style = MaterialTheme.typography.labelSmall, color = Gray400)
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            daysMap.forEach { (dayNum, label) ->
                                val isDaySelected = selectedDays.contains(dayNum)
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (isDaySelected) Emerald400 else Indigo900.copy(alpha = 0.7f)
                                        )
                                        .border(
                                            1.dp,
                                            if (isDaySelected) Emerald400 else Indigo700,
                                            CircleShape
                                        )
                                        .clickable {
                                            selectedDays = if (isDaySelected) {
                                                if (selectedDays.size > 1) selectedDays - dayNum else selectedDays
                                            } else {
                                                selectedDays + dayNum
                                            }
                                        }
                                ) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isDaySelected) Indigo900 else White,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                // Hora del Aviso / Notificación
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Alarm, contentDescription = null, tint = Violet300, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Hora de la Notificación:",
                            style = MaterialTheme.typography.labelSmall,
                            color = White,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    OutlinedTextField(
                        value = timeOfDay,
                        onValueChange = { input ->
                            if (input.length <= 5) timeOfDay = input
                        },
                        label = { Text("Hora (HH:mm)", color = Violet300) },
                        placeholder = { Text("09:00", color = Gray500) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Violet400,
                            unfocusedBorderColor = Indigo600,
                            focusedTextColor = White,
                            unfocusedTextColor = White
                        ),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Sugerencias rápidas de horas
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        listOf("08:00", "09:30", "14:00", "19:00", "21:30").forEach { preset ->
                            val isPresetSelected = timeOfDay == preset
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (isPresetSelected) Violet400.copy(alpha = 0.35f) else Indigo900.copy(alpha = 0.6f)
                                    )
                                    .border(
                                        1.dp,
                                        if (isPresetSelected) Violet400 else Indigo700,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable { timeOfDay = preset }
                                    .padding(vertical = 5.dp)
                            ) {
                                Text(
                                    text = preset,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isPresetSelected) Violet300 else Gray300,
                                    fontSize = 10.sp,
                                    fontWeight = if (isPresetSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }

                    Text(
                        text = "🔔 Aviso puntual con sonido y opción de marcar como tomado.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Gray400,
                        fontSize = 11.sp
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        val daysString = selectedDays.sorted().joinToString(",")
                        onSave(title.trim(), type, daysString, timeOfDay.trim(), iconEmoji)
                    }
                },
                enabled = title.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = Violet400)
            ) {
                Text(
                    text = if (reminder != null) "Actualizar" else "Guardar",
                    color = Indigo900,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", color = Gray400)
            }
        }
    )
}

// ─── Componente: Tarjeta de Perfil Avanzada ───────────────────────────────────
