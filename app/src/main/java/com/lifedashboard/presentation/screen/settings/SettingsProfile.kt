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

// Perfil y peso: tarjeta de perfil, registro e historial de pesajes.

@Composable
internal fun EnhancedUserProfileCard(
    userName: String,
    fitnessLevel: String,
    primaryGoal: String,
    weightKg: Float,
    heightCm: Int,
    tdeeKcal: Float = 2350f,
    onOpenProfile: () -> Unit,
    onSyncAll: () -> Unit
) {
    val imc = if (heightCm > 0) {
        val hM = heightCm / 100f
        String.format(Locale.US, "%.1f", weightKg / (hM * hM))
    } else "24.0"

    val levelLabel = when (fitnessLevel) {
        "PRINCIPIANTE" -> "Principiante"
        "AVANZADO"     -> "Avanzado"
        "ATLETA"       -> "Atleta"
        else           -> "Intermedio"
    }

    val goalLabel = when (primaryGoal) {
        "GRASA"   -> "Pérdida de Grasa"
        "MUSCULO" -> "Hipertrofia"
        "CARDIO"  -> "Resistencia Cardio"
        else      -> "Salud Integral"
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        Indigo800,
                        PanelHigh
                    )
                )
            )
            .border(1.dp, Indigo700, RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier              = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f)
                        .clickable(onClick = onOpenProfile)
                ) {
                    // Avatar compacto con degradado violeta
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(listOf(Ion, Flux))
                            )
                    ) {
                        Text(
                            text       = userName.firstOrNull()?.uppercase() ?: "J",
                            fontSize   = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color      = White
                        )
                    }

                    Spacer(Modifier.width(12.dp))

                    Column {
                        Text(
                            text       = userName,
                            style      = MaterialTheme.typography.titleMedium,
                            color      = White,
                            fontWeight = FontWeight.Bold,
                            fontSize   = 16.sp
                        )
                        Spacer(Modifier.height(2.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Violet400.copy(alpha = 0.2f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(levelLabel, fontSize = 10.sp, color = Violet300, fontWeight = FontWeight.SemiBold)
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Cyan400.copy(alpha = 0.15f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(goalLabel, fontSize = 10.sp, color = Cyan400, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }

                IconButton(
                    onClick  = onOpenProfile,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Indigo700.copy(alpha = 0.5f))
                ) {
                    Icon(Icons.Default.Edit, contentDescription = "Editar perfil", tint = Gray300, modifier = Modifier.size(15.dp))
                }
            }

            // Barra unificada de métricas (cápsula compacta y limpia)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Indigo900.copy(alpha = 0.5f))
                    .border(1.dp, Indigo700.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                    .padding(vertical = 8.dp, horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                CompactMetricItem("Peso", "${String.format(Locale.US, "%.1f", weightKg)} kg", Modifier.weight(1f))
                Box(modifier = Modifier.width(1.dp).height(20.dp).background(Indigo700.copy(alpha = 0.5f)))
                CompactMetricItem("Altura", "${heightCm} cm", Modifier.weight(1f))
                Box(modifier = Modifier.width(1.dp).height(20.dp).background(Indigo700.copy(alpha = 0.5f)))
                CompactMetricItem("IMC", imc, Modifier.weight(1f))
                Box(modifier = Modifier.width(1.dp).height(20.dp).background(Indigo700.copy(alpha = 0.5f)))
                CompactMetricItem("Gasto Base", "${tdeeKcal.toInt()} kcal", Modifier.weight(1.1f))
            }
        }
    }
}

@Composable
internal fun WeightTrackerCard(
    latestWeight: Float,
    heightCm: Int,
    weightHistory: List<WeightEntryEntity>,
    onAddWeight: () -> Unit,
    onViewHistory: () -> Unit
) {
    val imc = if (heightCm > 0) {
        val hM = heightCm / 100f
        latestWeight / (hM * hM)
    } else 24.0f

    val imcCategory = when {
        imc < 18.5f -> "Bajo peso" to Cyan400
        imc < 25.0f -> "Peso saludable" to Emerald400
        imc < 30.0f -> "Sobrepeso" to Amber400
        else        -> "Obesidad" to Rose400
    }

    val deltaTextAndColor: Triple<String, Color, ImageVector?> = if (weightHistory.size >= 2) {
        val diff = latestWeight - weightHistory[1].weightKg
        when {
            diff < -0.05f -> Triple(String.format(Locale.US, "-%.1f kg", Math.abs(diff)), Emerald400, Icons.Default.ArrowDownward)
            diff > 0.05f  -> Triple(String.format(Locale.US, "+%.1f kg", diff), Amber400, Icons.Default.ArrowUpward)
            else          -> Triple("0.0 kg", Gray400, Icons.Default.Remove)
        }
    } else if (weightHistory.size == 1) {
        Triple("Primer registro", Violet300, null)
    } else {
        Triple("Sin historial", Gray400, null)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        Indigo800,
                        Indigo900.copy(alpha = 0.9f)
                    )
                )
            )
            .border(1.dp, Indigo700, RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Cabecera con título, peso actual y botón de registro
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Violet400.copy(alpha = 0.15f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.FitnessCenter,
                            contentDescription = null,
                            tint = Violet300,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Seguimiento de Peso",
                            style = MaterialTheme.typography.titleMedium,
                            color = White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${String.format(Locale.US, "%.1f", latestWeight)} kg",
                                style = MaterialTheme.typography.bodySmall,
                                color = Violet300,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "· ${deltaTextAndColor.first} · ${imcCategory.first}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Gray400,
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                IconButton(
                    onClick  = onAddWeight,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Violet400)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Registrar pesaje",
                        tint = Indigo900,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Gráfica interactiva de evolución de peso
            if (weightHistory.isNotEmpty()) {
                WeightEvolutionChart(
                    weightHistory = weightHistory,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(135.dp)
                )

                // Enlace al historial completo
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onViewHistory)
                        .padding(vertical = 2.dp)
                ) {
                    Text(
                        text = "Ver historial completo (${weightHistory.size} registros)",
                        style = MaterialTheme.typography.labelSmall,
                        color = Violet300,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = Violet300,
                        modifier = Modifier.size(14.dp)
                    )
                }
            } else {
                Text(
                    text = "Aún no has registrado ningún pesaje. Pulsa en '+' para comenzar tu seguimiento.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Gray500
                )
            }
        }
    }
}

// ─── Modales de Pesaje ────────────────────────────────────────────────────────

@Composable
internal fun AddWeightEntryModal(
    currentWeight: Float,
    onDismiss: () -> Unit,
    onSave: (Float, String?) -> Unit
) {
    var weightInput by remember { mutableStateOf(String.format(Locale.US, "%.1f", currentWeight)) }
    var noteInput by remember { mutableStateOf("") }

    val adjustWeight = { delta: Float ->
        val current = weightInput.toFloatOrNull() ?: currentWeight
        val updated = (current + delta).coerceAtLeast(30.0f)
        weightInput = String.format(Locale.US, "%.1f", updated)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = Indigo800,
        shape            = RoundedCornerShape(24.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Violet400.copy(alpha = 0.2f))
                ) {
                    Icon(
                        imageVector = Icons.Default.FitnessCenter,
                        contentDescription = null,
                        tint = Violet300,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text("Registrar Pesaje", color = White, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                OutlinedTextField(
                    value = weightInput,
                    onValueChange = { weightInput = it.replace(',', '.') },
                    label = { Text("Peso corporal (kg)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Violet400,
                        unfocusedBorderColor = Indigo600,
                        focusedTextColor = White,
                        unfocusedTextColor = White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // Botones rápidos de ajuste
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    listOf(-0.5f, -0.1f, +0.1f, +0.5f).forEach { delta ->
                        val sign = if (delta > 0) "+$delta" else "$delta"
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Indigo700)
                                .clickable { adjustWeight(delta) }
                                .padding(vertical = 8.dp)
                        ) {
                            Text(
                                text = "$sign kg",
                                style = MaterialTheme.typography.labelSmall,
                                color = White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = noteInput,
                    onValueChange = { noteInput = it },
                    label = { Text("Nota (opcional)") },
                    placeholder = { Text("Ej. En ayunas, tras entrenar...", color = Gray500) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Violet400,
                        unfocusedBorderColor = Indigo600,
                        focusedTextColor = White,
                        unfocusedTextColor = White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val w = weightInput.toFloatOrNull() ?: currentWeight
                    onSave(w, noteInput.takeIf { it.isNotBlank() })
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Violet400,
                    contentColor = Indigo900
                )
            ) {
                Text("Guardar Pesaje", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", color = Gray400)
            }
        }
    )
}

@Composable
internal fun WeightHistoryModal(
    history: List<WeightEntryEntity>,
    onDismiss: () -> Unit,
    onDelete: (Long) -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("d MMM yyyy, HH:mm", Locale("es")) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = Indigo800,
        shape            = RoundedCornerShape(24.dp),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        tint = Violet300,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Historial de Pesajes", color = White, fontWeight = FontWeight.Bold)
                }
                Text(
                    text = "${history.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = Gray400
                )
            }
        },
        text = {
            if (history.isEmpty()) {
                Text(
                    text = "No hay registros guardados en tu historial.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Gray400,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    history.forEach { entry ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Indigo900.copy(alpha = 0.6f))
                                .border(1.dp, Indigo700.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${String.format(Locale.US, "%.1f", entry.weightKg)} kg",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = White,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = dateFormat.format(Date(entry.dateMillis)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Gray400,
                                    fontSize = 11.sp
                                )
                                if (!entry.note.isNullOrBlank()) {
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = entry.note,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Violet300,
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            IconButton(
                                onClick = { onDelete(entry.id) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = "Eliminar pesaje",
                                    tint = Rose400,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors  = ButtonDefaults.buttonColors(containerColor = Violet400, contentColor = Indigo900)
            ) {
                Text("Cerrar", fontWeight = FontWeight.Bold)
            }
        }
    )
}

// ─── Modal: Editar Perfil Completo ────────────────────────────────────────────

@Composable
internal fun EnhancedProfileModal(
    currentName: String,
    currentLevel: String,
    currentGoal: String,
    currentWeight: Float,
    currentHeight: Int,
    onDismiss: () -> Unit,
    onSave: (String, String, String, Float, Int) -> Unit
) {
    var nameInput by remember { mutableStateOf(currentName) }
    var selectedLevel by remember { mutableStateOf(currentLevel) }
    var selectedGoal by remember { mutableStateOf(currentGoal) }
    var weightInput by remember { mutableStateOf(currentWeight.toInt().toString()) }
    var heightInput by remember { mutableStateOf(currentHeight.toString()) }

    val levels = listOf(
        "PRINCIPIANTE" to "Principiante",
        "INTERMEDIO"   to "Intermedio",
        "AVANZADO"     to "Avanzado",
        "ATLETA"       to "Atleta"
    )

    val goals = listOf(
        "SALUD_GENERAL" to "Salud General",
        "GRASA"         to "Bajar Grasa",
        "MUSCULO"       to "Ganar Músculo",
        "CARDIO"        to "Rendimiento"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = Indigo800,
        shape            = RoundedCornerShape(24.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AccountCircle, contentDescription = null, tint = Violet300, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
                Text("Perfil Biométrico", color = White, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                OutlinedTextField(
                    value         = nameInput,
                    onValueChange = { nameInput = it },
                    label         = { Text("Nombre") },
                    singleLine    = true,
                    colors        = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Violet400, unfocusedBorderColor = Indigo600,
                        focusedTextColor = White, unfocusedTextColor = White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Nivel de Entrenamiento:", style = MaterialTheme.typography.labelMedium, color = Gray300)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    levels.take(2).forEach { (key, label) ->
                        OptionChip(label, selectedLevel == key, Modifier.weight(1f)) { selectedLevel = key }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    levels.drop(2).forEach { (key, label) ->
                        OptionChip(label, selectedLevel == key, Modifier.weight(1f)) { selectedLevel = key }
                    }
                }

                Text("Objetivo Primario:", style = MaterialTheme.typography.labelMedium, color = Gray300)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    goals.take(2).forEach { (key, label) ->
                        OptionChip(label, selectedGoal == key, Modifier.weight(1f)) { selectedGoal = key }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    goals.drop(2).forEach { (key, label) ->
                        OptionChip(label, selectedGoal == key, Modifier.weight(1f)) { selectedGoal = key }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value         = weightInput,
                        onValueChange = { weightInput = it },
                        label         = { Text("Peso (kg)") },
                        singleLine    = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors        = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Violet400, unfocusedBorderColor = Indigo600,
                            focusedTextColor = White, unfocusedTextColor = White
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value         = heightInput,
                        onValueChange = { heightInput = it },
                        label         = { Text("Altura (cm)") },
                        singleLine    = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors        = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Violet400, unfocusedBorderColor = Indigo600,
                            focusedTextColor = White, unfocusedTextColor = White
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val w = weightInput.toFloatOrNull() ?: currentWeight
                    val h = heightInput.toIntOrNull() ?: currentHeight
                    onSave(nameInput, selectedLevel, selectedGoal, w, h)
                },
                colors  = ButtonDefaults.buttonColors(containerColor = Violet400, contentColor = Indigo900)
            ) {
                Text("Guardar", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar", color = Gray400) }
        }
    )
}

// ─── Modales Auxiliares de Metas ─────────────────────────────────────────────
