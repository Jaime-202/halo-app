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

// Buscador de alimentos y resultados del escáner de código de barras.

@Composable
internal fun SearchFoodDialog(
    mealType: String,
    query: String,
    selectedCategory: FoodSourceCategory,
    results: List<FoodCatalogItem>,
    isSearching: Boolean,
    onQueryChange: (String) -> Unit,
    onCategorySelect: (FoodSourceCategory) -> Unit,
    onScanBarcode: () -> Unit,
    onAddFood: (FoodCatalogItem, Float) -> Unit,
    onDismiss: () -> Unit,
    favorites: List<FoodCatalogItem> = emptyList(),
    recents: List<FoodCatalogItem> = emptyList(),
    favoriteKeys: Set<String> = emptySet(),
    onToggleFavorite: (FoodCatalogItem) -> Unit = {}
) {
    var selectedItem by remember { mutableStateOf<FoodCatalogItem?>(null) }
    var inputGrams by remember { mutableStateOf("100") }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .clip(RoundedCornerShape(24.dp))
                .background(Indigo800)
                .border(1.dp, Indigo700, RoundedCornerShape(24.dp))
                .padding(18.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Header
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier              = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text       = "Añadir a $mealType",
                        style      = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color      = White
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Gray400, modifier = Modifier.size(18.dp))
                    }
                }

                // Buscador con botón de Escáner de Código de Barras
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        placeholder = { Text("Buscar pan, pechuga, avena...", color = Gray500, fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Violet300, modifier = Modifier.size(20.dp)) },
                        trailingIcon = {
                            if (isSearching) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Violet400, strokeWidth = 2.dp)
                            } else if (query.isNotBlank()) {
                                IconButton(onClick = { onQueryChange("") }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Borrar", tint = Gray400, modifier = Modifier.size(16.dp))
                                }
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Violet400,
                            unfocusedBorderColor = Indigo600,
                            focusedTextColor = White,
                            unfocusedTextColor = White
                        ),
                        shape = RoundedCornerShape(14.dp),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )

                    // Botón para escanear código de barras con la cámara
                    IconButton(
                        onClick = onScanBarcode,
                        modifier = Modifier
                            .size(50.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Violet400.copy(alpha = 0.2f))
                            .border(1.dp, Violet400.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                    ) {
                        Icon(
                            Icons.Default.CropFree,
                            contentDescription = "Escanear código de barras",
                            tint = Violet300,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // Chips de filtro de supermercado
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(FoodSourceCategory.entries.toTypedArray()) { cat ->
                        val isSelected = cat == selectedCategory
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) Violet400 else Indigo700)
                                .clickable { onCategorySelect(cat) }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = cat.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isSelected) Indigo900 else Gray300,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }

                HorizontalDivider(color = Indigo700, thickness = 1.dp)

                // Si hay un alimento seleccionado para especificar gramos
                if (selectedItem != null) {
                    val item = selectedItem!!
                    val grams = inputGrams.toFloatOrNull() ?: 100f
                    val factor = grams / 100f
                    val baseKcal = if (item.caloriesPer100g > 0f || item.name.contains("agua", ignoreCase = true)) {
                        item.caloriesPer100g
                    } else {
                        FoodCatalogService.inferMacrosIfZero(item.name, 0f, item.proteinPer100g, item.carbsPer100g, item.fatPer100g).calories
                    }
                    val calcKcal = (baseKcal * factor).toInt()
                    val calcP = (item.proteinPer100g * factor).toInt()
                    val calcC = (item.carbsPer100g * factor).toInt()
                    val calcG = (item.fatPer100g * factor).toInt()

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Indigo900)
                            .border(1.dp, Violet400, RoundedCornerShape(16.dp))
                            .padding(16.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                FoodThumbnail(name = item.name, brand = item.brand, imageUrl = item.imageUrl, size = 56.dp)
                                Spacer(Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = item.name, style = MaterialTheme.typography.titleSmall, color = White, fontWeight = FontWeight.Bold)
                                    Text("Marca: ${item.brand}", style = MaterialTheme.typography.labelSmall, color = Violet300)
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Cantidad:", style = MaterialTheme.typography.bodyMedium, color = Gray300)
                                Spacer(Modifier.width(10.dp))
                                OutlinedTextField(
                                    value = inputGrams,
                                    onValueChange = { inputGrams = it },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Violet400,
                                        unfocusedBorderColor = Indigo600,
                                        focusedTextColor = White,
                                        unfocusedTextColor = White
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    singleLine = true,
                                    modifier = Modifier.width(90.dp).height(46.dp)
                                )
                                Text(" gramos", style = MaterialTheme.typography.bodyMedium, color = Gray400)
                            }

                            // Raciones rápidas: la ración del producto y 100 g
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                val portions = buildList {
                                    if (item.servingSizeGrams > 0f && item.servingSizeGrams != 100f) {
                                        add(item.servingDescription to item.servingSizeGrams)
                                    }
                                    add("100 g" to 100f)
                                }
                                portions.forEach { (label, g) ->
                                    val active = inputGrams.toFloatOrNull() == g
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(50))
                                            .border(1.dp, if (active) Ion else LineStrong, RoundedCornerShape(50))
                                            .clickable { inputGrams = g.toInt().toString() }
                                            .padding(horizontal = 10.dp, vertical = 5.dp)
                                    ) {
                                        Text(label, color = if (active) Ion else TextMute, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                                    }
                                }
                            }

                            // Macros calculados
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("$calcKcal kcal", color = Amber400, fontWeight = FontWeight.Bold)
                                Text("P: ${calcP}g", color = Violet300)
                                Text("C: ${calcC}g", color = Cyan400)
                                Text("G: ${calcG}g", color = Amber400)
                            }
                            if (item.fiberPer100g > 0f || item.sugarPer100g > 0f || item.saltPer100g > 0f) {
                                Text(
                                    "Fibra ${"%.1f".format(item.fiberPer100g * factor)} g · Azúcar ${"%.1f".format(item.sugarPer100g * factor)} g · Sal ${"%.2f".format(item.saltPer100g * factor)} g",
                                    color = TextMute,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { selectedItem = null },
                                    colors = ButtonDefaults.buttonColors(containerColor = Indigo700),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Volver", color = Gray300)
                                }
                                Button(
                                    onClick = { onAddFood(item, grams) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Violet400),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Añadir", color = Indigo900, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                } else {
                    // Lista de resultados con fotos de todos los alimentos
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (results.isEmpty() && !isSearching) {
                            item {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.fillMaxWidth().padding(top = 40.dp)
                                ) {
                                    Text(
                                        text = "No se encontraron alimentos.\nPrueba a buscar pan, avena, pechuga o escanear el código de barras.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Gray500,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }

                        val showLibrary = query.isBlank()
                        if (showLibrary && favorites.isNotEmpty()) {
                            item { LibrarySectionLabel("Favoritos") }
                            items(favorites, key = { "fav_" + it.id }) { food ->
                                SearchResultRow(food, true, onToggleFavorite) {
                                    selectedItem = food
                                    inputGrams = food.servingSizeGrams.toInt().toString()
                                }
                            }
                        }
                        if (showLibrary && recents.isNotEmpty()) {
                            item { LibrarySectionLabel("Recientes") }
                            items(recents, key = { "rec_" + it.id }) { food ->
                                SearchResultRow(food, NutritionViewModel.foodKey(food.name, food.brand) in favoriteKeys, onToggleFavorite) {
                                    selectedItem = food
                                    inputGrams = food.servingSizeGrams.toInt().toString()
                                }
                            }
                        }
                        if (showLibrary && (favorites.isNotEmpty() || recents.isNotEmpty()) && results.isNotEmpty()) {
                            item { LibrarySectionLabel("Catálogo") }
                        }
                        items(results) { food ->
                            SearchResultRow(food, NutritionViewModel.foodKey(food.name, food.brand) in favoriteKeys, onToggleFavorite) {
                                selectedItem = food
                                inputGrams = food.servingSizeGrams.toInt().toString()
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─── Diálogo de Confirmación de Alimento Detectado por QR / Código de Barras ──

@Composable
internal fun ScannedFoodDialog(
    food: FoodCatalogItem,
    initialMealType: String,
    onConfirm: (item: FoodCatalogItem, grams: Float, mealType: String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedMeal by remember { mutableStateOf(initialMealType) }
    var inputGrams by remember { mutableStateOf(food.servingSizeGrams.toInt().toString()) }

    val parsedGrams = inputGrams.toFloatOrNull() ?: food.servingSizeGrams
    val factor = parsedGrams / 100f
    val safeCaloriesPer100 = if (food.caloriesPer100g > 0f || food.name.contains("agua", ignoreCase = true)) {
        food.caloriesPer100g
    } else {
        FoodCatalogService.inferMacrosIfZero(food.name, 0f, food.proteinPer100g, food.carbsPer100g, food.fatPer100g).calories
    }
    val calcKcal = (safeCaloriesPer100 * factor).toInt()
    val calcP = "%.1f".format(Locale.US, food.proteinPer100g * factor)
    val calcC = "%.1f".format(Locale.US, food.carbsPer100g * factor)
    val calcF = "%.1f".format(Locale.US, food.fatPer100g * factor)

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Indigo800)
                .border(1.dp, Emerald400.copy(alpha = 0.5f), RoundedCornerShape(24.dp))
                .padding(20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Emerald400.copy(alpha = 0.2f))
                                .padding(6.dp)
                        ) {
                            Icon(Icons.Default.CropFree, contentDescription = null, tint = Emerald400, modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "¡Alimento Detectado!",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = White
                            )
                            if (!food.barcode.isNullOrBlank()) {
                                Text(
                                    text = "Código: ${food.barcode}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Gray400,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Gray400, modifier = Modifier.size(18.dp))
                    }
                }

                // Info del Producto con Imagen Real
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Indigo900.copy(alpha = 0.7f))
                        .padding(12.dp)
                ) {
                    FoodThumbnail(name = food.name, brand = food.brand, imageUrl = food.imageUrl, size = 60.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = food.name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = White,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Violet400.copy(alpha = 0.25f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = food.brand,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Violet300,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp
                                )
                            }
                            val safeFoodKcal = if (food.caloriesPer100g > 0f || food.name.contains("agua", ignoreCase = true)) {
                                food.caloriesPer100g
                            } else {
                                FoodCatalogService.inferMacrosIfZero(food.name, 0f, food.proteinPer100g, food.carbsPer100g, food.fatPer100g).calories
                            }
                            Text(
                                text = "${safeFoodKcal.toInt()} kcal / 100g",
                                style = MaterialTheme.typography.labelSmall,
                                color = Amber400,
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                // Selector de Comida Destino
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Añadir a:",
                        style = MaterialTheme.typography.labelSmall,
                        color = Gray300,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                    ) {
                        MealSectionType.entries.forEach { section ->
                            val isSel = section.key == selectedMeal
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSel) Violet400 else Indigo700)
                                    .clickable { selectedMeal = section.key }
                                    .padding(vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = section.title.take(4),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isSel) Indigo900 else Gray300,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }

                // Cantidad en gramos
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Porción consumida:",
                            style = MaterialTheme.typography.labelSmall,
                            color = Gray300,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Recomendada: ${food.servingDescription}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Gray400,
                            fontSize = 10.sp
                        )
                    }

                    OutlinedTextField(
                        value = inputGrams,
                        onValueChange = { inputGrams = it },
                        trailingIcon = { Text("g", color = Gray400, modifier = Modifier.padding(end = 12.dp)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Emerald400,
                            unfocusedBorderColor = Indigo600,
                            focusedTextColor = White,
                            unfocusedTextColor = White
                        ),
                        shape = RoundedCornerShape(12.dp),
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
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Indigo700)
                                    .clickable {
                                        val cur = inputGrams.toIntOrNull() ?: 100
                                        inputGrams = (cur + diff).coerceAtLeast(5).toString()
                                    }
                                    .padding(vertical = 6.dp)
                            ) {
                                Text("${diffStr}g", style = MaterialTheme.typography.labelSmall, color = Gray200, fontSize = 11.sp)
                            }
                        }
                    }
                }

                // Resumen de macros calculados
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Indigo900)
                        .border(1.dp, Indigo700, RoundedCornerShape(14.dp))
                        .padding(12.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Aporte calórico:", style = MaterialTheme.typography.bodySmall, color = Gray300)
                            Text("$calcKcal kcal", style = MaterialTheme.typography.titleMedium, color = Amber400, fontWeight = FontWeight.Bold)
                        }
                        HorizontalDivider(color = Indigo700, thickness = 1.dp)
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("P: ${calcP}g", style = MaterialTheme.typography.labelSmall, color = Violet300, fontWeight = FontWeight.SemiBold)
                            Text("C: ${calcC}g", style = MaterialTheme.typography.labelSmall, color = Cyan400, fontWeight = FontWeight.SemiBold)
                            Text("G: ${calcF}g", style = MaterialTheme.typography.labelSmall, color = Amber400, fontWeight = FontWeight.SemiBold)
                        }
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
                            val finalGrams = inputGrams.toFloatOrNull() ?: food.servingSizeGrams
                            onConfirm(food, finalGrams, selectedMeal)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Emerald400),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1.4f)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = Indigo900, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Registrar", color = Indigo900, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ─── Diálogo para Código de Barras no Encontrado en Local ─────────────────────

@Composable
internal fun UnknownBarcodeDialog(
    barcode: String,
    onSearchAi: () -> Unit,
    onQuickAdd: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Indigo800)
                .border(1.dp, Amber400.copy(alpha = 0.5f), RoundedCornerShape(24.dp))
                .padding(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CropFree, contentDescription = null, tint = Amber400, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Código Escaneado", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = White)
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Gray400, modifier = Modifier.size(18.dp))
                    }
                }

                Text(
                    text = "Código: $barcode\n\nNo se encontró este código en la base local de Mercadona o supermercados. ¿Cómo prefieres registrarlo?",
                    style = MaterialTheme.typography.bodySmall,
                    color = Gray300
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = onSearchAi,
                        colors = ButtonDefaults.buttonColors(containerColor = Violet400),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Indigo900, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Identificar con IA Gemini", color = Indigo900, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = onQuickAdd,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Amber400),
                        border = BorderStroke(1.dp, Amber400.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.FlashOn, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Añadido Rápido Manual", color = Amber400, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ─── Modal Calculador de Ingesta Calórica y Macros ────────────────────────────

@Composable
internal fun SearchResultRow(
    food: FoodCatalogItem,
    isFavorite: Boolean,
    onToggleFavorite: (FoodCatalogItem) -> Unit,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Indigo900.copy(alpha = 0.7f))
            .clickable(onClick = onClick)
            .padding(10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            // FOTO DEL ALIMENTO / PRODUCTO CON FALLBACK INTELIGENTE
            FoodThumbnail(name = food.name, brand = food.brand, imageUrl = food.imageUrl, size = 46.dp)
            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = food.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = White,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Indigo700)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = food.brand,
                            style = MaterialTheme.typography.labelSmall,
                            color = Violet300,
                            fontSize = 9.sp,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    val safeKcal100 = if (food.caloriesPer100g > 0f || food.name.contains("agua", ignoreCase = true)) {
                        food.caloriesPer100g
                    } else {
                        FoodCatalogService.inferMacrosIfZero(food.name, 0f, food.proteinPer100g, food.carbsPer100g, food.fatPer100g).calories
                    }
                    Text("${safeKcal100.toInt()} kcal/100g", style = MaterialTheme.typography.labelSmall, color = Amber400, fontSize = 11.sp, maxLines = 1, softWrap = false)
                    Text("·", color = Gray500)
                    Text("P:${food.proteinPer100g.toInt()}g", style = MaterialTheme.typography.labelSmall, color = Gray400, fontSize = 11.sp, maxLines = 1, softWrap = false)
                }
            }

            Spacer(Modifier.width(8.dp))

            IconButton(onClick = { onToggleFavorite(food) }, modifier = Modifier.size(32.dp)) {
                Icon(
                    if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                    contentDescription = if (isFavorite) "Quitar de favoritos" else "Añadir a favoritos",
                    tint = if (isFavorite) Ion else TextMute,
                    modifier = Modifier.size(20.dp)
                )
            }
            Icon(Icons.Default.AddCircleOutline, contentDescription = "Seleccionar", tint = Violet400, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
internal fun LibrarySectionLabel(text: String) {
    Text(
        text.uppercase(),
        color = TextMute,
        style = androidx.compose.ui.text.TextStyle(fontFamily = MonoFamily, fontSize = 11.sp, letterSpacing = 1.2.sp),
        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
    )
}
