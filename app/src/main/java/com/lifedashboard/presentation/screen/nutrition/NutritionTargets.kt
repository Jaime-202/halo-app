package com.lifedashboard.presentation.screen.nutrition

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.lifedashboard.data.local.entity.MealEntryEntity
import com.lifedashboard.data.remote.food.DetectedFoodItem
import com.lifedashboard.data.remote.food.FoodCatalogItem
import com.lifedashboard.data.remote.food.FoodCatalogService
import com.lifedashboard.data.remote.food.FoodSourceCategory
import com.lifedashboard.data.remote.food.RealFoodImageResolver
import com.lifedashboard.presentation.theme.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

// Objetivos de calorías y macros: edición manual y calculadora.

@Composable
internal fun CustomMacroTargetsDialog(
    currentTargetCalories: Float,
    currentTargetProtein: Float,
    currentTargetCarbs: Float,
    currentTargetFat: Float,
    userHeightCm: Int,
    userWeightKg: Float,
    userGoal: String,
    onRecalculateFromProfile: () -> Unit,
    onSaveTargets: (calories: Float, protein: Float, carbs: Float, fat: Float) -> Unit,
    onDismiss: () -> Unit
) {
    var kcalInput by remember { mutableStateOf("${currentTargetCalories.toInt()}") }
    var proteinInput by remember { mutableStateOf("${currentTargetProtein.toInt()}") }
    var carbsInput by remember { mutableStateOf("${currentTargetCarbs.toInt()}") }
    var fatInput by remember { mutableStateOf("${currentTargetFat.toInt()}") }

    val kcal = kcalInput.toFloatOrNull() ?: currentTargetCalories
    val p = proteinInput.toFloatOrNull() ?: currentTargetProtein
    val c = carbsInput.toFloatOrNull() ?: currentTargetCarbs
    val f = fatInput.toFloatOrNull() ?: currentTargetFat
    val macroKcalSum = (p * 4f) + (c * 4f) + (f * 9f)

    val goalLabel = when (userGoal) {
        "PERDIDA_PESO", "GRASA" -> "Perder grasa"
        "VOLUMEN", "MUSCULO" -> "Ganar músculo"
        else -> "Mantenimiento"
    }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Indigo800)
                .border(1.dp, Violet400.copy(alpha = 0.5f), RoundedCornerShape(24.dp))
                .padding(20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Edit, contentDescription = null, tint = Violet300, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Tus Objetivos Diarios",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = White
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Gray400, modifier = Modifier.size(18.dp))
                    }
                }

                // ⚡ Botón de 1-tap para auto-calcular según perfil físico
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Violet400.copy(alpha = 0.15f))
                        .border(1.dp, Violet400.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                        .clickable {
                            val validW = if (userWeightKg > 30f) userWeightKg else 75f
                            val validH = if (userHeightCm > 100) userHeightCm else 175
                            val bmr = (10f * validW) + (6.25f * validH) - (5f * 25f) + 5f
                            val tdee = bmr * 1.35f
                            val calcKcal = when (userGoal) {
                                "PERDIDA_PESO", "GRASA" -> (tdee - 450f).coerceAtLeast(1400f)
                                "VOLUMEN", "MUSCULO" -> tdee + 300f
                                else -> tdee
                            }
                            val calculatedP = (validW * 2.0f).coerceIn(90f, 240f)
                            val calculatedF = (validW * 0.85f).coerceIn(40f, 110f)
                            val rem = calcKcal - (calculatedP * 4f + calculatedF * 9f)
                            val calculatedC = (rem / 4f).coerceAtLeast(50f)

                            kcalInput = "${calcKcal.toInt()}"
                            proteinInput = "${calculatedP.toInt()}"
                            carbsInput = "${calculatedC.toInt()}"
                            fatInput = "${calculatedF.toInt()}"
                        }
                        .padding(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Violet400.copy(alpha = 0.25f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Bolt, contentDescription = null, tint = Violet300, modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Auto-calcular con mi perfil",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = White
                            )
                            Text(
                                text = "${userHeightCm} cm · ${userWeightKg.toInt()} kg · $goalLabel",
                                style = MaterialTheme.typography.labelSmall,
                                color = Violet200,
                                fontSize = 10.sp
                            )
                        }
                        Text(
                            text = "Rellenar",
                            style = MaterialTheme.typography.labelSmall,
                            color = Violet300,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Text(
                    text = "O escribe directamente tus metas diarias de calorías y macronutrientes:",
                    style = MaterialTheme.typography.bodySmall,
                    color = Gray300
                )

                // Meta Calórica
                OutlinedTextField(
                    value = kcalInput,
                    onValueChange = { kcalInput = it },
                    label = { Text("Meta Calórica Diaria (kcal)", color = Gray400) },
                    trailingIcon = { Text("kcal", color = Amber400, fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 12.dp)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Amber400,
                        unfocusedBorderColor = Indigo600,
                        focusedTextColor = White,
                        unfocusedTextColor = White
                    ),
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Botones rápidos para calorías
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    listOf(1800, 2000, 2200, 2500).forEach { preset ->
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (kcalInput == "$preset") Violet400.copy(alpha = 0.3f) else Indigo700)
                                .border(1.dp, if (kcalInput == "$preset") Violet400 else Color.Transparent, RoundedCornerShape(10.dp))
                                .clickable { kcalInput = "$preset" }
                                .padding(vertical = 6.dp)
                        ) {
                            Text("$preset", style = MaterialTheme.typography.labelSmall, color = White, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                // 3 Macros
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = proteinInput,
                        onValueChange = { proteinInput = it },
                        label = { Text("Proteína (g)", color = Gray400, fontSize = 11.sp) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Violet400,
                            unfocusedBorderColor = Indigo600,
                            focusedTextColor = White,
                            unfocusedTextColor = White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = carbsInput,
                        onValueChange = { carbsInput = it },
                        label = { Text("Carbos (g)", color = Gray400, fontSize = 11.sp) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Cyan400,
                            unfocusedBorderColor = Indigo600,
                            focusedTextColor = White,
                            unfocusedTextColor = White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = fatInput,
                        onValueChange = { fatInput = it },
                        label = { Text("Grasas (g)", color = Gray400, fontSize = 11.sp) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Amber400,
                            unfocusedBorderColor = Indigo600,
                            focusedTextColor = White,
                            unfocusedTextColor = White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Validación de equivalencia calórica
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Indigo900)
                        .border(1.dp, Indigo700, RoundedCornerShape(14.dp))
                        .padding(12.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Suma de macros:", style = MaterialTheme.typography.bodySmall, color = Gray400)
                            Text("${macroKcalSum.toInt()} kcal", style = MaterialTheme.typography.bodySmall, color = White, fontWeight = FontWeight.Bold)
                        }
                        val diff = (macroKcalSum - kcal).toInt()
                        Text(
                            text = if (kotlin.math.abs(diff) <= 30) "Los macros cuadran perfectamente con tu meta calórica." else "Nota: La suma de macros difiere en ${kotlin.math.abs(diff)} kcal respecto a tu meta de ${kcal.toInt()} kcal.",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (kotlin.math.abs(diff) <= 30) Emerald400 else Gray400
                        )
                    }
                }

                // Botones
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    OutlinedButton(
                        onClick = onDismiss,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Gray400),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancelar", color = Gray400)
                    }
                    Button(
                        onClick = {
                            val finalKcal = kcalInput.toFloatOrNull() ?: currentTargetCalories
                            val finalP = proteinInput.toFloatOrNull() ?: currentTargetProtein
                            val finalC = carbsInput.toFloatOrNull() ?: currentTargetCarbs
                            val finalF = fatInput.toFloatOrNull() ?: currentTargetFat
                            onSaveTargets(finalKcal, finalP, finalC, finalF)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Violet400),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1.3f)
                    ) {
                        Text("Guardar Metas", color = Indigo900, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ─── Diálogo de Foto con IA y Descripción ─────────────────────────────────────

@Composable
internal fun CaloricCalculatorDialog(
    currentWeight: Float,
    currentHeight: Int,
    currentGoal: String,
    onCalculate: (Float, Int, String) -> CalculatedIntake,
    onApply: (CalculatedIntake, Float, Int, String) -> Unit,
    onDismiss: () -> Unit
) {
    var weightInput by remember { mutableStateOf(if (currentWeight > 0) "%.1f".format(Locale.US, currentWeight) else "75.0") }
    var heightInput by remember { mutableStateOf(if (currentHeight > 0) "$currentHeight" else "175") }
    var selectedGoal by remember { mutableStateOf(if (currentGoal.isNotBlank()) currentGoal else "PERDIDA_PESO") }

    val parsedWeight = weightInput.toFloatOrNull() ?: 75f
    val parsedHeight = heightInput.toIntOrNull() ?: 175
    val calculated = remember(parsedWeight, parsedHeight, selectedGoal) {
        onCalculate(parsedWeight, parsedHeight, selectedGoal)
    }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Indigo800)
                .border(1.dp, Violet400.copy(alpha = 0.5f), RoundedCornerShape(24.dp))
                .padding(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // Header
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier              = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Calculate, contentDescription = null, tint = Violet300, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text       = "Calculador de Ingesta",
                            style      = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color      = White
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Gray400, modifier = Modifier.size(18.dp))
                    }
                }

                Text(
                    text = "Ajusta tus datos corporales y objetivo para calcular tu ingesta diaria óptima basada en la fórmula científica de Mifflin-St Jeor.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Gray300
                )

                // Campos Peso y Altura
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = weightInput,
                        onValueChange = { weightInput = it },
                        label = { Text("Peso (kg)", color = Gray400, fontSize = 11.sp) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Violet400,
                            unfocusedBorderColor = Indigo600,
                            focusedTextColor = White,
                            unfocusedTextColor = White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )

                    OutlinedTextField(
                        value = heightInput,
                        onValueChange = { heightInput = it },
                        label = { Text("Altura (cm)", color = Gray400, fontSize = 11.sp) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Violet400,
                            unfocusedBorderColor = Indigo600,
                            focusedTextColor = White,
                            unfocusedTextColor = White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Selector de Objetivo
                Text("Objetivo primario:", style = MaterialTheme.typography.labelSmall, color = Gray400)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    GoalChip(
                        title = "Perder grasa",
                        subtitle = "-500 kcal",
                        isSelected = selectedGoal == "PERDIDA_PESO" || selectedGoal == "GRASA",
                        onClick = { selectedGoal = "PERDIDA_PESO" },
                        modifier = Modifier.weight(1f)
                    )
                    GoalChip(
                        title = "Mantenimiento",
                        subtitle = "Equilibrio",
                        isSelected = selectedGoal == "MANTENIMIENTO" || selectedGoal == "SALUD_GENERAL",
                        onClick = { selectedGoal = "MANTENIMIENTO" },
                        modifier = Modifier.weight(1f)
                    )
                    GoalChip(
                        title = "Ganar músculo",
                        subtitle = "+300 kcal",
                        isSelected = selectedGoal == "VOLUMEN" || selectedGoal == "MUSCULO",
                        onClick = { selectedGoal = "VOLUMEN" },
                        modifier = Modifier.weight(1f)
                    )
                }

                // Resultado del cálculo
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Indigo900)
                        .border(1.dp, Indigo700, RoundedCornerShape(16.dp))
                        .padding(14.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Ingesta diaria recomendada:", style = MaterialTheme.typography.bodySmall, color = Gray300)
                            Text(
                                text = "${calculated.calories.toInt()} kcal",
                                style = MaterialTheme.typography.titleLarge,
                                color = Amber400,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        HorizontalDivider(color = Indigo700, thickness = 1.dp)

                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("P: ${calculated.protein.toInt()}g", color = Violet300, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text("C: ${calculated.carbs.toInt()}g", color = Cyan400, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text("G: ${calculated.fat.toInt()}g", color = Amber400, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }

                        Text(
                            text = "Metabolismo basal: ${calculated.bmr.toInt()} kcal · Gasto estimado: ${calculated.tdee.toInt()} kcal",
                            style = MaterialTheme.typography.labelSmall,
                            color = Gray500,
                            fontSize = 10.sp
                        )
                    }
                }

                // Botón aplicar
                Button(
                    onClick = {
                        onApply(calculated, parsedWeight, parsedHeight, selectedGoal)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Violet400),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Aplicar como mi Meta Diaria", color = Indigo900, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
internal fun GoalChip(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) Violet400 else Indigo700)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = if (isSelected) Indigo900 else White,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                fontSize = 10.sp
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = if (isSelected) Indigo900.copy(alpha = 0.8f) else Gray400,
                fontSize = 9.sp
            )
        }
    }
}

// ─── Componente: Miniatura de Alimento con Fallback Inteligente ────────────────
