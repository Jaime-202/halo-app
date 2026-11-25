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

// Diálogos de datos y conexiones: clave de Gemini y copia de seguridad.

@Composable
internal fun EditApiKeyModal(
    currentKey: String,
    currentModel: String = "gemini-3.8-flash",
    onDismiss: () -> Unit,
    onTest: (suspend (String, String) -> Result<String>)? = null,
    onSave: (String, String) -> Unit
) {
    var keyInput by remember { mutableStateOf(currentKey) }
    var selectedModel by remember { mutableStateOf(if (currentModel.isBlank()) "gemini-3.8-flash" else currentModel) }
    var customModelInput by remember { mutableStateOf(if (currentModel !in listOf("gemini-3.8-flash", "gemini-3.8-flash-lite", "gemini-3.7-flash", "auto")) currentModel else "") }
    var isCustom by remember { mutableStateOf(currentModel !in listOf("gemini-3.8-flash", "gemini-3.8-flash-lite", "gemini-3.7-flash", "auto") && currentModel.isNotBlank()) }
    var isTesting by remember { mutableStateOf(false) }
    var testResultText by remember { mutableStateOf<String?>(null) }
    var testIsSuccess by remember { mutableStateOf<Boolean?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    val activeModelToUse = if (isCustom && customModelInput.isNotBlank()) customModelInput.trim() else selectedModel

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = Indigo800,
        shape            = RoundedCornerShape(22.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Key, contentDescription = null, tint = Violet300, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text("Google Gemini API Key", color = White, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text  = "Consigue una clave gratis en Google AI Studio (aistudio.google.com). Compatible con Gemini 3.8 Flash, con selección de modelos y auto-detección dinámica para modelos futuros.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Gray300
                )

                OutlinedButton(
                    onClick = {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/app/apikey"))
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Abre aistudio.google.com en tu navegador", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Cyan400),
                    border = BorderStroke(1.dp, Cyan400.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Obtener Clave Gratis en Google AI Studio", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }

                OutlinedTextField(
                    value         = keyInput,
                    onValueChange = {
                        keyInput = it
                        testResultText = null
                        testIsSuccess = null
                    },
                    label         = { Text("Clave API de Gemini", color = Gray400) },
                    placeholder   = { Text("AIzaSy...", color = Gray500) },
                    singleLine    = true,
                    colors        = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Violet400, unfocusedBorderColor = Indigo600,
                        focusedTextColor = White, unfocusedTextColor = White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                // Selector de Modelo Gemini (Gemini 3.8 Flash, 3.8 Lite, 3.7, Auto, Otro)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Modelo de IA Gemini:",
                        style = MaterialTheme.typography.labelSmall,
                        color = Gray300,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                    ) {
                        listOf(
                            "gemini-3.8-flash" to "⚡ 3.8 Flash",
                            "gemini-3.8-flash-lite" to "🚀 3.8 Lite",
                            "gemini-3.7-flash" to "✨ 3.7",
                            "auto" to "🎯 Auto"
                        ).forEach { (modelKey, label) ->
                            val isSelected = !isCustom && selectedModel == modelKey
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) Violet400.copy(alpha = 0.25f) else Indigo900.copy(alpha = 0.6f))
                                    .border(1.dp, if (isSelected) Violet400 else Indigo700, RoundedCornerShape(8.dp))
                                    .clickable {
                                        isCustom = false
                                        selectedModel = modelKey
                                        testResultText = null
                                    }
                                    .padding(horizontal = 8.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 11.sp,
                                    color = if (isSelected) Violet200 else Gray400,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isCustom) Violet400.copy(alpha = 0.25f) else Indigo900.copy(alpha = 0.6f))
                                .border(1.dp, if (isCustom) Violet400 else Indigo700, RoundedCornerShape(8.dp))
                                .clickable {
                                    isCustom = true
                                    testResultText = null
                                }
                                .padding(horizontal = 8.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = "✍️ Otro",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 11.sp,
                                color = if (isCustom) Violet200 else Gray400,
                                fontWeight = if (isCustom) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }

                if (isCustom) {
                    OutlinedTextField(
                        value = customModelInput,
                        onValueChange = {
                            customModelInput = it
                            testResultText = null
                        },
                        label = { Text("Nombre del modelo (ej: gemini-3.9-flash)", color = Gray400, fontSize = 11.sp) },
                        placeholder = { Text("gemini-3.8-flash", color = Gray500) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Cyan400, unfocusedBorderColor = Indigo600,
                            focusedTextColor = White, unfocusedTextColor = White
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (onTest != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val buttonBg = when (testIsSuccess) {
                            true -> Emerald400.copy(alpha = 0.25f)
                            false -> Rose400.copy(alpha = 0.25f)
                            null -> Indigo700
                        }
                        val buttonBorderColor = when (testIsSuccess) {
                            true -> Emerald400
                            false -> Rose400
                            null -> Color.Transparent
                        }
                        val buttonTextColor = when (testIsSuccess) {
                            true -> Emerald400
                            false -> Rose400
                            null -> White
                        }

                        Button(
                            onClick = {
                                if (keyInput.isBlank()) {
                                    Toast.makeText(context, "Escribe o pega una clave primero", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                isTesting = true
                                testResultText = null
                                testIsSuccess = null
                                coroutineScope.launch {
                                    val res = onTest(keyInput.trim(), activeModelToUse)
                                    isTesting = false
                                    if (res.isSuccess) {
                                        testIsSuccess = true
                                        testResultText = res.getOrNull() ?: "Conexión confirmada"
                                    } else {
                                        testIsSuccess = false
                                        testResultText = res.exceptionOrNull()?.message ?: "Error al conectar"
                                    }
                                }
                            },
                            enabled = !isTesting && keyInput.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(containerColor = buttonBg, contentColor = buttonTextColor),
                            border = if (buttonBorderColor != Color.Transparent) BorderStroke(1.dp, buttonBorderColor) else null,
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            when {
                                isTesting -> {
                                    CircularProgressIndicator(color = Violet300, strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Comprobando...", style = MaterialTheme.typography.labelSmall)
                                }
                                testIsSuccess == true -> {
                                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp), tint = Emerald400)
                                    Spacer(Modifier.width(6.dp))
                                    Text("¡Funciona! ✔", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                }
                                testIsSuccess == false -> {
                                    Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp), tint = Rose400)
                                    Spacer(Modifier.width(6.dp))
                                    Text("Error ✖", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                }
                                else -> {
                                    Icon(Icons.Default.CheckCircleOutline, contentDescription = null, modifier = Modifier.size(14.dp), tint = Violet300)
                                    Spacer(Modifier.width(6.dp))
                                    Text("Comprobar Clave", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }

                        if (keyInput.isNotBlank()) {
                            TextButton(
                                onClick = {
                                    keyInput = ""
                                    testResultText = null
                                    testIsSuccess = null
                                },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text("Borrar", style = MaterialTheme.typography.labelSmall, color = Rose400)
                            }
                        }
                    }
                }

                testResultText?.let { msg ->
                    val isSuccess = testIsSuccess == true
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSuccess) Emerald400.copy(alpha = 0.15f) else Rose400.copy(alpha = 0.15f))
                            .border(1.5.dp, if (isSuccess) Emerald400.copy(alpha = 0.8f) else Rose400.copy(alpha = 0.8f), RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(if (isSuccess) Emerald400 else Rose400)
                            ) {
                                Icon(
                                    imageVector = if (isSuccess) Icons.Default.Check else Icons.Default.Close,
                                    contentDescription = if (isSuccess) "Válido" else "Error",
                                    tint = if (isSuccess) Indigo900 else White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isSuccess) "¡API Key funcional y activa! (✔)" else "Clave no válida o sin conexión (✖)",
                                    color = if (isSuccess) Emerald400 else Rose400,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.labelMedium
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = msg,
                                    color = if (isSuccess) Gray100 else Gray300,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(keyInput, activeModelToUse) },
                colors = ButtonDefaults.buttonColors(containerColor = Violet400, contentColor = Indigo900),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Guardar", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", color = Gray400)
            }
        }
    )
}

// ─── Microcomponentes Reutilizables ──────────────────────────────────────────

@Composable
internal fun BackupPasswordDialog(
    creating: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var password by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    val tooShort = password.length < 6
    val mismatch = creating && repeat != password
    val valid = !tooShort && !mismatch

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Panel,
        title = { Text(if (creating) "Crear copia cifrada" else "Restaurar copia", color = TextHigh) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    if (creating)
                        "Elige una contraseña. Sin ella no se puede abrir la copia, ni siquiera desde Halo: apúntala en un sitio seguro."
                    else
                        "Escribe la contraseña de la copia. Los datos actuales se sustituirán por los de la copia.",
                    color = TextMute,
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Contraseña") },
                    singleLine = true,
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    supportingText = { if (password.isNotEmpty() && tooShort) Text("Mínimo 6 caracteres") },
                )
                if (creating) {
                    OutlinedTextField(
                        value = repeat,
                        onValueChange = { repeat = it },
                        label = { Text("Repite la contraseña") },
                        singleLine = true,
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        supportingText = { if (repeat.isNotEmpty() && mismatch) Text("No coinciden") },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(password) }, enabled = valid) {
                Text(if (creating) "Elegir dónde guardar" else "Elegir archivo", color = if (valid) Ion else TextDim)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar", color = TextMute) }
        },
    )
}
