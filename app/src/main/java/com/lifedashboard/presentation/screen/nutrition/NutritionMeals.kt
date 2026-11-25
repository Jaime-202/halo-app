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

// Comidas del día: secciones, filas y diálogos para ajustar o añadir rápido.

@Composable
internal fun MealSectionCard(
    section: MealSectionType,
    meals: List<MealEntryEntity>,
    onAddClick: () -> Unit,
    onScanBarcodeClick: () -> Unit,
    onAiClick: () -> Unit,
    onQuickAddClick: () -> Unit,
    onEditMeal: (MealEntryEntity) -> Unit,
    onDeleteMeal: (Long) -> Unit
) {
    val sectionKcal = meals.sumOf { it.calories.toDouble() }.toInt()
    val sectionP = meals.sumOf { it.protein.toDouble() }.toInt()
    val sectionC = meals.sumOf { it.carbs.toDouble() }.toInt()
    val sectionG = meals.sumOf { it.fat.toDouble() }.toInt()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Indigo800)
            .border(1.dp, Indigo700, RoundedCornerShape(22.dp))
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // Cabecera elegante de la sección
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier              = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Indigo700),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = section.iconEmoji, fontSize = 20.sp)
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            text       = section.title,
                            style      = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color      = White,
                            maxLines   = 1
                        )
                        val mealRatio = when (section) {
                            MealSectionType.DESAYUNO -> "~25% ingesta diaria"
                            MealSectionType.COMIDA -> "~35% ingesta diaria"
                            MealSectionType.MERIENDA -> "~15% ingesta diaria"
                            MealSectionType.CENA -> "~25% ingesta diaria"
                            MealSectionType.SNACK -> "Snacks y complementos"
                        }
                        Text(
                            text = mealRatio,
                            style = MaterialTheme.typography.labelSmall,
                            color = Gray400,
                            fontSize = 10.sp,
                            maxLines = 1
                        )
                    }
                }

                if (sectionKcal > 0) {
                    Spacer(Modifier.width(8.dp))
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Indigo700)
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text  = "$sectionKcal kcal",
                                style = MaterialTheme.typography.labelSmall,
                                color = Amber400,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                        Text(
                            text = "P:${sectionP}g · C:${sectionC}g · G:${sectionG}g",
                            style = MaterialTheme.typography.labelSmall,
                            color = Gray400,
                            fontSize = 10.sp,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }

            // Lista de alimentos registrados en esta comida
            if (meals.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    meals.forEach { meal ->
                        MealItemRow(
                            meal = meal,
                            onEdit = { onEditMeal(meal) },
                            onDelete = { onDeleteMeal(meal.id) }
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Indigo900.copy(alpha = 0.4f))
                        .padding(vertical = 12.dp, horizontal = 12.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = "Sin alimentos registrados en ${section.title.lowercase()} aún.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Gray400
                    )
                }
            }

            // ─── Barra de Acciones Rediseñada y Unificada en 1 Sola Línea ───
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // 1. Añadir Alimento desde Buscador / Supermercados
                Button(
                    onClick = onAddClick,
                    colors = ButtonDefaults.buttonColors(containerColor = Indigo700),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 9.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = Violet300, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Añadir",
                        style = MaterialTheme.typography.labelMedium,
                        color = White,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                    )
                }

                // 2. Escanear Código de Barras / QR
                IconButton(
                    onClick = onScanBarcodeClick,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Cyan400.copy(alpha = 0.15f))
                        .border(1.dp, Cyan400.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                ) {
                    Icon(Icons.Default.CropFree, contentDescription = "Escanear QR o código de barras", tint = Cyan400, modifier = Modifier.size(18.dp))
                }

                // 3. Registrar con IA (Cámara o Texto)
                IconButton(
                    onClick = onAiClick,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Violet400.copy(alpha = 0.15f))
                        .border(1.dp, Violet400.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = "Registrar comida con IA", tint = Violet300, modifier = Modifier.size(18.dp))
                }

                // 4. Añadido Rápido de Calorías
                IconButton(
                    onClick = onQuickAddClick,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Amber400.copy(alpha = 0.15f))
                        .border(1.dp, Amber400.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                ) {
                    Icon(Icons.Default.FlashOn, contentDescription = "Añadido rápido de calorías", tint = Amber400, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
internal fun MealItemRow(
    meal: MealEntryEntity,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier              = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Indigo900.copy(alpha = 0.6f))
            .clickable(onClick = onEdit)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        // Thumbnail visual real con fallback limpio
        FoodThumbnail(name = meal.name, brand = meal.brand, imageUrl = meal.imageUrl, size = 42.dp)
        Spacer(Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            // Línea 1: Nombre del alimento
            Text(
                text       = meal.name,
                style      = MaterialTheme.typography.bodyMedium,
                color      = White,
                fontWeight = FontWeight.SemiBold,
                maxLines   = 1,
                softWrap   = false,
                overflow   = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(3.dp))

            // Línea 2: Marca (badge sutil), gramos y macronutrientes
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (meal.brand.isNotBlank() && meal.brand != "Básico" && meal.brand != "Manual" && meal.brand != "Genérico" && meal.brand != "Casero") {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Violet400.copy(alpha = 0.22f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text       = meal.brand,
                            style      = MaterialTheme.typography.labelSmall,
                            color      = Violet300,
                            fontSize   = 10.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines   = 1,
                            softWrap   = false
                        )
                    }
                    Text("·", color = Gray600, fontSize = 10.sp)
                }
                Text("${meal.grams.toInt()}g", style = MaterialTheme.typography.labelSmall, color = Gray400, fontSize = 11.sp, maxLines = 1, softWrap = false)
                Text("·", color = Gray600, fontSize = 10.sp)
                Text(
                    text = "P:${meal.protein.toInt()}g C:${meal.carbs.toInt()}g G:${meal.fat.toInt()}g",
                    style = MaterialTheme.typography.labelSmall,
                    color = Gray500,
                    fontSize = 10.sp,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(Modifier.width(8.dp))

        // Calorías a la derecha, destacadas con botones de acción compactos debajo
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.Center
        ) {
            val displayCalories = if (meal.calories > 0f || meal.name.contains("agua", ignoreCase = true)) {
                meal.calories
            } else {
                val inf = FoodCatalogService.inferMacrosIfZero(meal.name, 0f, meal.protein, meal.carbs, meal.fat)
                inf.calories * (meal.grams / 100f)
            }
            Text(
                text       = "${displayCalories.toInt()} kcal",
                style      = MaterialTheme.typography.labelMedium,
                color      = Amber400,
                fontWeight = FontWeight.Bold,
                maxLines   = 1,
                softWrap   = false
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick  = onEdit,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = "Editar porción", tint = Gray400, modifier = Modifier.size(13.dp))
                }
                IconButton(
                    onClick  = onDelete,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "Eliminar", tint = Gray500, modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}

// ─── Diálogo para Editar la Porción en Gramos ──────────────────────────────────

@Composable
internal fun EditPortionDialog(
    meal: MealEntryEntity,
    onSaveGrams: (Float) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    var gramsInput by remember(meal.grams) { mutableStateOf("${meal.grams.toInt()}") }
    val parsedGrams = gramsInput.toFloatOrNull() ?: meal.grams
    val ratio = if (meal.grams > 0f) parsedGrams / meal.grams else 1f

    val baseCalories = if (meal.calories > 0f || meal.name.contains("agua", ignoreCase = true)) {
        meal.calories
    } else {
        val inf = FoodCatalogService.inferMacrosIfZero(meal.name, 0f, meal.protein, meal.carbs, meal.fat)
        inf.calories * (meal.grams / 100f)
    }
    val previewKcal = (baseCalories * ratio).coerceAtLeast(0f).toInt()
    val previewProtein = "%.1f".format(Locale.US, (meal.protein * ratio).coerceAtLeast(0f))
    val previewCarbs = "%.1f".format(Locale.US, (meal.carbs * ratio).coerceAtLeast(0f))
    val previewFat = "%.1f".format(Locale.US, (meal.fat * ratio).coerceAtLeast(0f))

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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        FoodThumbnail(name = meal.name, brand = meal.brand, imageUrl = meal.imageUrl, size = 38.dp)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                text = meal.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (meal.brand.isNotBlank() && meal.brand != "Básico") {
                                Text(text = meal.brand, style = MaterialTheme.typography.labelSmall, color = Violet300)
                            }
                        }
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Gray400, modifier = Modifier.size(18.dp))
                    }
                }

                Text(
                    text = "Ajusta la cantidad en gramos consumida para recalcular las calorías y macros automáticamente:",
                    style = MaterialTheme.typography.bodySmall,
                    color = Gray300
                )

                // Input de gramos con teclado numérico
                OutlinedTextField(
                    value = gramsInput,
                    onValueChange = { gramsInput = it },
                    label = { Text("Porción consumida (gramos)", color = Gray400) },
                    trailingIcon = { Text("g", color = Gray400, modifier = Modifier.padding(end = 12.dp)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Violet400,
                        unfocusedBorderColor = Indigo600,
                        focusedTextColor = White,
                        unfocusedTextColor = White
                    ),
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Chips de ajuste rápido
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    listOf("-50", "-20", "+20", "+50").forEach { diffStr ->
                        val diff = diffStr.toInt()
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Indigo700)
                                .clickable {
                                    val current = gramsInput.toIntOrNull() ?: meal.grams.toInt()
                                    val updated = (current + diff).coerceAtLeast(5)
                                    gramsInput = "$updated"
                                }
                                .padding(vertical = 8.dp)
                        ) {
                            Text("${diffStr}g", style = MaterialTheme.typography.labelSmall, color = Gray100, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Resumen en vivo
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
                            Text("Calorías recalculadas:", style = MaterialTheme.typography.bodySmall, color = Gray300)
                            Text("$previewKcal kcal", style = MaterialTheme.typography.titleMedium, color = Amber400, fontWeight = FontWeight.Bold)
                        }
                        HorizontalDivider(color = Indigo700, thickness = 1.dp)
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Proteínas: ${previewProtein}g", style = MaterialTheme.typography.labelSmall, color = Violet300)
                            Text("Carbos: ${previewCarbs}g", style = MaterialTheme.typography.labelSmall, color = Cyan400)
                            Text("Grasas: ${previewFat}g", style = MaterialTheme.typography.labelSmall, color = Amber400)
                        }
                    }
                }

                // Acciones
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Rose400),
                        border = BorderStroke(1.dp, Rose400.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Eliminar", color = Rose400)
                    }

                    Button(
                        onClick = {
                            val finalGrams = gramsInput.toFloatOrNull() ?: meal.grams
                            if (finalGrams > 0f) {
                                onSaveGrams(finalGrams)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Violet400),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1.3f)
                    ) {
                        Text("Guardar", color = Indigo900, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ─── Diálogo de Añadido Rápido de Comida ───────────────────────────────────────

@Composable
internal fun QuickAddMealDialog(
    mealType: String,
    onAdd: (name: String, calories: Float, protein: Float, carbs: Float, fat: Float) -> Unit,
    onDismiss: () -> Unit
) {
    var nameInput by remember { mutableStateOf("") }
    var kcalInput by remember { mutableStateOf("") }
    var proteinInput by remember { mutableStateOf("") }
    var carbsInput by remember { mutableStateOf("") }
    var fatInput by remember { mutableStateOf("") }

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
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.FlashOn, contentDescription = null, tint = Amber400, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Añadido Rápido",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = White
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Gray400, modifier = Modifier.size(18.dp))
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Violet400.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("Destino: $mealType", style = MaterialTheme.typography.labelSmall, color = Violet300, fontWeight = FontWeight.SemiBold)
                }

                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    label = { Text("Nombre o descripción", color = Gray400) },
                    placeholder = { Text("Ej: Merienda o comida rápida", color = Gray500) },
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

                OutlinedTextField(
                    value = kcalInput,
                    onValueChange = { kcalInput = it },
                    label = { Text("Calorías (kcal) *", color = Gray400) },
                    trailingIcon = { Text("kcal", color = Amber400, modifier = Modifier.padding(end = 12.dp)) },
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

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = proteinInput,
                        onValueChange = { proteinInput = it },
                        label = { Text("Proteína", color = Gray400, fontSize = 11.sp) },
                        trailingIcon = { Text("g", color = Violet300, fontSize = 11.sp, modifier = Modifier.padding(end = 6.dp)) },
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
                        label = { Text("Carbos", color = Gray400, fontSize = 11.sp) },
                        trailingIcon = { Text("g", color = Cyan400, fontSize = 11.sp, modifier = Modifier.padding(end = 6.dp)) },
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
                        value = fatInput,
                        onValueChange = { fatInput = it },
                        label = { Text("Grasas", color = Gray400, fontSize = 11.sp) },
                        trailingIcon = { Text("g", color = Amber400, fontSize = 11.sp, modifier = Modifier.padding(end = 6.dp)) },
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
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
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
                            var kcal = kcalInput.toFloatOrNull() ?: 0f
                            var p = proteinInput.toFloatOrNull() ?: 0f
                            var c = carbsInput.toFloatOrNull() ?: 0f
                            var f = fatInput.toFloatOrNull() ?: 0f
                            val safeName = nameInput.ifBlank { "Comida rápida" }

                            if (kcal <= 0f && (p > 0f || c > 0f || f > 0f)) {
                                kcal = (p * 4f) + (c * 4f) + (f * 9f)
                            }
                            if (kcal <= 0f && !safeName.contains("agua", ignoreCase = true)) {
                                val inf = FoodCatalogService.inferMacrosIfZero(safeName, 0f, p, c, f)
                                kcal = inf.calories
                                if (p <= 0f) p = inf.protein
                                if (c <= 0f) c = inf.carbs
                                if (f <= 0f) f = inf.fat
                            }
                            if (kcal > 0f || p > 0f || c > 0f || f > 0f || safeName.isNotBlank()) {
                                onAdd(safeName, kcal, p, c, f)
                            }
                        },
                        enabled = nameInput.isNotBlank() || (kcalInput.toFloatOrNull() ?: 0f) > 0f || (proteinInput.toFloatOrNull() ?: 0f) > 0f,
                        colors = ButtonDefaults.buttonColors(containerColor = Violet400),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1.3f)
                    ) {
                        Text("Añadir", color = Indigo900, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ─── Diálogo para Escribir Objetivos Personalizados ───────────────────────────
