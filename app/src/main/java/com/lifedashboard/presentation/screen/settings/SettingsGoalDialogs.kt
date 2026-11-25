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

// Diálogos para editar objetivos (pasos, sueño, calorías, entreno, macros, agua, despertar).

@Composable
internal fun EditStepsGoalModal(currentValue: Int, onDismiss: () -> Unit, onSave: (Int) -> Unit) {
    var stepsText by remember { mutableStateOf(currentValue.toString()) }
    val stepOptions = listOf(6000, 8000, 10000, 12000, 15000)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = Indigo800,
        shape            = RoundedCornerShape(22.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Filled.DirectionsWalk, null, tint = RingSteps, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text("Objetivo de Pasos", color = White, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Escribe tu objetivo diario personalizado de pasos:", style = MaterialTheme.typography.bodySmall, color = Gray400)

                OutlinedTextField(
                    value = stepsText,
                    onValueChange = { stepsText = it.filter { char -> char.isDigit() } },
                    label = { Text("Pasos diarios", color = Cyan400) },
                    trailingIcon = { Text("pasos", color = Gray400, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(end = 12.dp)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Cyan400,
                        unfocusedBorderColor = Indigo600,
                        focusedTextColor = White,
                        unfocusedTextColor = White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("O elige una sugerencia rápida:", style = MaterialTheme.typography.labelSmall, color = Gray400)

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    stepOptions.forEach { steps ->
                        val isSelected = stepsText == steps.toString()
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) RingSteps else Indigo700)
                                .clickable { stepsText = steps.toString() }
                                .padding(vertical = 8.dp)
                        ) {
                            Text("${steps / 1000}k", color = if (isSelected) Indigo900 else White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val s = stepsText.toIntOrNull()?.coerceIn(1000, 100000) ?: currentValue
                    onSave(s)
                },
                colors = ButtonDefaults.buttonColors(containerColor = RingSteps, contentColor = Indigo900)
            ) {
                Text("Guardar", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar", color = Gray400) } }
    )
}

@Composable
internal fun EditSleepGoalModal(currentValue: Float, onDismiss: () -> Unit, onSave: (Float) -> Unit) {
    var sleepText by remember { mutableStateOf(String.format(Locale.US, "%.1f", currentValue)) }
    val sleepOptions = listOf(6.5f, 7.0f, 7.5f, 8.0f, 8.5f, 9.0f)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = Indigo800,
        shape            = RoundedCornerShape(22.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Bedtime, null, tint = RingSleep, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text("Objetivo de Sueño", color = White, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Escribe las horas de descanso nocturno deseadas:", style = MaterialTheme.typography.bodySmall, color = Gray400)

                OutlinedTextField(
                    value = sleepText,
                    onValueChange = { sleepText = it.replace(',', '.') },
                    label = { Text("Horas de sueño", color = Teal400) },
                    trailingIcon = { Text("horas", color = Gray400, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(end = 12.dp)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Teal400,
                        unfocusedBorderColor = Indigo600,
                        focusedTextColor = White,
                        unfocusedTextColor = White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("O elige una sugerencia rápida:", style = MaterialTheme.typography.labelSmall, color = Gray400)

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    sleepOptions.forEach { hours ->
                        val formatted = String.format(Locale.US, "%.1f", hours)
                        val isSelected = sleepText == formatted || sleepText == hours.toString()
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) RingSleep else Indigo700)
                                .clickable { sleepText = formatted }
                                .padding(vertical = 8.dp)
                        ) {
                            Text("${hours}h", color = if (isSelected) Indigo900 else White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val h = sleepText.toFloatOrNull()?.coerceIn(4f, 14f) ?: currentValue
                    onSave(h)
                },
                colors = ButtonDefaults.buttonColors(containerColor = RingSleep, contentColor = Indigo900)
            ) {
                Text("Guardar", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar", color = Gray400) } }
    )
}

@Composable
internal fun EditCaloriesGoalModal(currentValue: Float, onDismiss: () -> Unit, onSave: (Float) -> Unit) {
    var calText by remember { mutableStateOf(currentValue.toInt().toString()) }
    val calOptions = listOf(300f, 400f, 500f, 600f, 750f, 1000f)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = Indigo800,
        shape            = RoundedCornerShape(22.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.LocalFireDepartment, null, tint = Amber400, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text("Meta de Calorías Activas", color = White, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    "Calorías que quieres quemar al día con tu movimiento, pasos y ejercicio (independiente de tu comida):",
                    style = MaterialTheme.typography.bodySmall,
                    color = Gray400
                )

                OutlinedTextField(
                    value = calText,
                    onValueChange = { calText = it.filter { char -> char.isDigit() } },
                    label = { Text("Calorías activas diarias", color = Amber400) },
                    trailingIcon = { Text("kcal", color = Gray400, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(end = 12.dp)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Amber400,
                        unfocusedBorderColor = Indigo600,
                        focusedTextColor = White,
                        unfocusedTextColor = White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("O elige una sugerencia rápida:", style = MaterialTheme.typography.labelSmall, color = Gray400)

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    calOptions.forEach { cal ->
                        val isSelected = calText == cal.toInt().toString()
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) Amber400 else Indigo700)
                                .clickable { calText = cal.toInt().toString() }
                                .padding(vertical = 8.dp)
                        ) {
                            Text("${cal.toInt()}", color = if (isSelected) Indigo900 else White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val c = calText.toFloatOrNull()?.coerceIn(100f, 5000f) ?: currentValue
                    onSave(c)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Amber400, contentColor = Indigo900)
            ) {
                Text("Guardar", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar", color = Gray400) } }
    )
}

@Composable
internal fun EditWorkoutGoalModal(currentDays: Int, currentMinutes: Int, onDismiss: () -> Unit, onSave: (Int, Int) -> Unit) {
    var daysText by remember { mutableStateOf(currentDays.toString()) }
    var minutesText by remember { mutableStateOf(currentMinutes.toString()) }
    val dayOptions = listOf(2, 3, 4, 5, 6)
    val minuteOptions = listOf(120, 150, 180, 240, 300)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = Indigo800,
        shape            = RoundedCornerShape(22.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.FitnessCenter, null, tint = Violet300, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text("Meta de Entrenamientos", color = White, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Personaliza tus objetivos semanales escribiendo los valores:", style = MaterialTheme.typography.bodySmall, color = Gray400)

                OutlinedTextField(
                    value = daysText,
                    onValueChange = { daysText = it.filter { char -> char.isDigit() } },
                    label = { Text("Días por semana", color = Violet300) },
                    trailingIcon = { Text("días", color = Gray400, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(end = 12.dp)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
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

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    dayOptions.forEach { days ->
                        OptionChip("$days d", days.toString() == daysText, Modifier.weight(1f)) { daysText = days.toString() }
                    }
                }

                Spacer(Modifier.height(4.dp))

                OutlinedTextField(
                    value = minutesText,
                    onValueChange = { minutesText = it.filter { char -> char.isDigit() } },
                    label = { Text("Minutos totales semanales", color = Violet300) },
                    trailingIcon = { Text("min", color = Gray400, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(end = 12.dp)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
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

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    minuteOptions.forEach { mins ->
                        OptionChip("${mins}m", mins.toString() == minutesText, Modifier.weight(1f)) { minutesText = mins.toString() }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val d = daysText.toIntOrNull()?.coerceIn(1, 7) ?: currentDays
                    val m = minutesText.toIntOrNull()?.coerceIn(30, 2000) ?: currentMinutes
                    onSave(d, m)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Violet400, contentColor = Indigo900)
            ) {
                Text("Guardar", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar", color = Gray400) } }
    )
}

@Composable
internal fun EditWakeUpTimeModal(
    currentHour: Int,
    currentMinute: Int,
    onDismiss: () -> Unit,
    onSave: (Int, Int) -> Unit
) {
    var selectedHour by remember { mutableIntStateOf(currentHour) }
    var selectedMinute by remember { mutableIntStateOf(currentMinute) }

    val presetTimes = listOf(
        6 to 0,
        6 to 30,
        7 to 0,
        7 to 30,
        8 to 0,
        8 to 30,
        9 to 0,
        9 to 30
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = Indigo800,
        shape            = RoundedCornerShape(22.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Alarm, contentDescription = null, tint = RingSleep, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text("Hora para Levantarse", color = White, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Selecciona tu hora habitual de despertar para que Halo calcule cuándo avisarte (30 min antes de dormir):",
                    style = MaterialTheme.typography.bodySmall,
                    color = Gray300
                )

                // Presets en cuadrícula
                presetTimes.chunked(4).forEach { rowPresets ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        rowPresets.forEach { (h, m) ->
                            val isSelected = h == selectedHour && m == selectedMinute
                            val formatted = String.format(Locale.US, "%02d:%02d", h, m)
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSelected) RingSleep else Indigo700)
                                    .clickable {
                                        selectedHour = h
                                        selectedMinute = m
                                    }
                                    .padding(vertical = 10.dp)
                            ) {
                                Text(
                                    text = formatted,
                                    color = if (isSelected) Indigo900 else White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))

                // Selector con botones +/- de 15 minutos para ajuste fino
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Indigo900.copy(alpha = 0.6f))
                        .border(1.dp, Indigo700.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        IconButton(
                            onClick = {
                                val totalMin = (selectedHour * 60 + selectedMinute - 15 + 1440) % 1440
                                selectedHour = totalMin / 60
                                selectedMinute = totalMin % 60
                            },
                            modifier = Modifier.size(32.dp).clip(CircleShape).background(Indigo700)
                        ) {
                            Text("-15", color = White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }

                        Text(
                            text = String.format(Locale.US, "%02d:%02d", selectedHour, selectedMinute),
                            style = MaterialTheme.typography.headlineMedium,
                            color = White,
                            fontWeight = FontWeight.Bold
                        )

                        IconButton(
                            onClick = {
                                val totalMin = (selectedHour * 60 + selectedMinute + 15) % 1440
                                selectedHour = totalMin / 60
                                selectedMinute = totalMin % 60
                            },
                            modifier = Modifier.size(32.dp).clip(CircleShape).background(Indigo700)
                        ) {
                            Text("+15", color = White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(selectedHour, selectedMinute) },
                colors  = ButtonDefaults.buttonColors(containerColor = RingSleep, contentColor = Indigo900)
            ) {
                Text("Guardar", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar", color = Gray400) }
        }
    )
}

@Composable
internal fun EditMacrosGoalModal(
    currentProtein: Float,
    currentCarbs: Float,
    currentFat: Float,
    onDismiss: () -> Unit,
    onSave: (Float, Float, Float) -> Unit
) {
    var proteinText by remember { mutableStateOf("${currentProtein.toInt()}") }
    var carbsText by remember { mutableStateOf("${currentCarbs.toInt()}") }
    var fatText by remember { mutableStateOf("${currentFat.toInt()}") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = Indigo800,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Restaurant, contentDescription = null, tint = Emerald400, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Objetivo de Macros", color = White, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Define tus objetivos diarios en gramos para cada macronutriente:",
                    style = MaterialTheme.typography.bodySmall,
                    color = Gray400
                )

                // Proteínas
                OutlinedTextField(
                    value = proteinText,
                    onValueChange = { proteinText = it },
                    label = { Text("Proteínas (g)", color = Violet300) },
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

                // Carbohidratos
                OutlinedTextField(
                    value = carbsText,
                    onValueChange = { carbsText = it },
                    label = { Text("Carbohidratos (g)", color = Cyan400) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Cyan400,
                        unfocusedBorderColor = Indigo600,
                        focusedTextColor = White,
                        unfocusedTextColor = White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Grasas
                OutlinedTextField(
                    value = fatText,
                    onValueChange = { fatText = it },
                    label = { Text("Grasas (g)", color = Amber400) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Amber400,
                        unfocusedBorderColor = Indigo600,
                        focusedTextColor = White,
                        unfocusedTextColor = White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val p = proteinText.toFloatOrNull() ?: currentProtein
                    val c = carbsText.toFloatOrNull() ?: currentCarbs
                    val f = fatText.toFloatOrNull() ?: currentFat
                    onSave(p, c, f)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Emerald400, contentColor = Indigo900)
            ) {
                Text("Guardar", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar", color = Gray400) }
        }
    )
}

@Composable
internal fun EditWaterGoalModal(
    currentValue: Int,
    onDismiss: () -> Unit,
    onSave: (Int) -> Unit
) {
    var waterText by remember { mutableStateOf("$currentValue") }
    val presets = listOf(1500, 2000, 2500, 3000)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = Indigo800,
        shape            = RoundedCornerShape(22.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.WaterDrop, contentDescription = null, tint = Cyan400, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text("Meta Diaria de Agua", color = White, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Ajusta tu objetivo de hidratación diaria en mililitros para el seguimiento visual en tus paneles:",
                    style = MaterialTheme.typography.bodySmall,
                    color = Gray300
                )

                // Presets rápidos
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    presets.forEach { preset ->
                        val isSelected = waterText == "$preset"
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) Cyan400 else Indigo700)
                                .clickable { waterText = "$preset" }
                                .padding(vertical = 8.dp)
                        ) {
                            Text(
                                text = "${preset / 1000f}L",
                                color = if (isSelected) Indigo900 else White,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = waterText,
                    onValueChange = { waterText = it },
                    label = { Text("Cantidad (ml)", color = Cyan400) },
                    placeholder = { Text("ej. 2500", color = Gray500) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Cyan400,
                        unfocusedBorderColor = Indigo600,
                        focusedTextColor = White,
                        unfocusedTextColor = White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "Esta meta es visual para tu progreso en la app (sin notificaciones emergentes).",
                    style = MaterialTheme.typography.bodySmall,
                    color = Gray400,
                    fontSize = 11.sp
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amount = waterText.toIntOrNull() ?: currentValue
                    onSave(amount)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Cyan400, contentColor = Indigo900)
            ) {
                Text("Guardar", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar", color = Gray400) }
        }
    )
}

@Composable
internal fun ResetDefaultsDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = Indigo800,
        shape            = RoundedCornerShape(22.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Refresh, contentDescription = null, tint = Rose400, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
                Text("Restablecer Ajustes", color = White, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Text(
                text = "¿Estás seguro de que deseas restablecer todas tus metas y preferencias a los valores por defecto recomendados?\n\nTu historial de pesaje y sesiones registradas se mantendrán a salvo.",
                style = MaterialTheme.typography.bodyMedium,
                color = Gray300
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors  = ButtonDefaults.buttonColors(containerColor = Rose400, contentColor = White)
            ) {
                Text("Restablecer Todo", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", color = Gray400)
            }
        }
    )
}


// ─── Diálogo de contraseña para la copia cifrada ─────────────────────────────
