package com.lifedashboard.presentation.screen.nutrition

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifedashboard.data.local.entity.SavedMealEntity
import com.lifedashboard.data.remote.food.FoodCatalogItem
import com.lifedashboard.domain.food.LabelValues
import com.lifedashboard.presentation.theme.*
import java.time.LocalTime
import kotlin.math.roundToInt

/** Momento del día más probable según la hora, para los atajos. */
fun defaultMealTypeForNow(now: LocalTime = LocalTime.now()): String = when (now.hour) {
    in 5..10 -> "DESAYUNO"
    in 11..15 -> "COMIDA"
    in 16..19 -> "MERIENDA"
    in 20..23 -> "CENA"
    else -> "SNACK"
}

private val monoLabel = TextStyle(fontFamily = MonoFamily, fontSize = 11.sp, letterSpacing = 1.2.sp)

// ─── Tarjeta de atajos ────────────────────────────────────────────────────────

/**
 * Atajos de registro: copiar ayer, leer una etiqueta, guardar o usar comidas guardadas,
 * y una fila con favoritos y recientes para añadir en un toque.
 */
@Composable
fun FoodShortcutsCard(
    state: NutritionUiState,
    onCopyYesterday: () -> Unit,
    onScanLabel: () -> Unit,
    onSaveMeal: () -> Unit,
    onOpenSavedMeals: () -> Unit,
    onQuickFood: (FoodCatalogItem) -> Unit,
) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Panel)
            .border(1.dp, Line, shape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Fibra, azúcar y sal del día (solo si hay datos)
        if (state.totalFiber > 0f || state.totalSugar > 0f || state.totalSalt > 0f) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                MiniStat("Fibra", "${state.totalFiber.roundToInt()} g")
                MiniStat("Azúcar", "${state.totalSugar.roundToInt()} g")
                MiniStat("Sal", "%.1f g".format(state.totalSalt))
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ShortcutButton(Icons.Outlined.ContentCopy, "Copiar ayer", onCopyYesterday)
            ShortcutButton(Icons.Outlined.DocumentScanner, "Leer etiqueta", onScanLabel)
            ShortcutButton(Icons.Outlined.Bookmarks, "Mis comidas", onOpenSavedMeals)
            ShortcutButton(Icons.Outlined.BookmarkAdd, "Guardar comida", onSaveMeal)
        }

        val quick = (state.favorites + state.recents)
            .distinctBy { NutritionViewModel.foodKey(it.name, it.brand) }
            .take(12)
        if (quick.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (state.favorites.isNotEmpty()) "FAVORITOS Y RECIENTES" else "RECIENTES", color = TextMute, style = monoLabel)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    quick.forEach { food ->
                        val chipShape = RoundedCornerShape(50)
                        Row(
                            modifier = Modifier
                                .clip(chipShape)
                                .background(PanelHigh)
                                .border(1.dp, LineStrong, chipShape)
                                .clickable { onQuickFood(food) }
                                .padding(horizontal = 12.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                food.name,
                                color = TextHigh,
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 160.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("+", color = Ion, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String) {
    Column {
        Text(label.uppercase(), color = TextMute, style = monoLabel)
        Text(value, color = TextHigh, style = TextStyle(fontFamily = MonoFamily, fontSize = 14.sp))
    }
}

@Composable
private fun ShortcutButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .clip(shape)
            .border(1.dp, LineStrong, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = Ion, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, color = TextHigh, style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}

// ─── Selector de momento del día ─────────────────────────────────────────────

@Composable
fun MealTypeSelector(selected: String, onSelect: (String) -> Unit) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        MealSectionType.entries.forEach { type ->
            val active = type.key == selected
            val shape = RoundedCornerShape(50)
            Box(
                modifier = Modifier
                    .clip(shape)
                    .background(if (active) PanelHigh else Panel)
                    .border(1.dp, if (active) Ion else LineStrong, shape)
                    .clickable { onSelect(type.key) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(type.title, color = if (active) Ion else TextMute, style = MaterialTheme.typography.labelMedium, maxLines = 1)
            }
        }
    }
}

// ─── Añadir rápido (favorito o reciente) ─────────────────────────────────────

@Composable
fun QuickFoodDialog(
    food: FoodCatalogItem,
    onAdd: (grams: Float, mealType: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var mealType by remember { mutableStateOf(defaultMealTypeForNow()) }
    var gramsText by remember { mutableStateOf(food.servingSizeGrams.roundToInt().toString()) }
    val grams = gramsText.replace(',', '.').toFloatOrNull() ?: 0f
    val f = grams / 100f

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Panel,
        title = { Text(food.name, color = TextHigh, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (food.brand.isNotBlank()) Text(food.brand, color = Flux, style = MaterialTheme.typography.labelMedium)
                MealTypeSelector(mealType) { mealType = it }
                OutlinedTextField(
                    value = gramsText,
                    onValueChange = { gramsText = it.take(5) },
                    label = { Text("Cantidad (g)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                Text(
                    "${(food.caloriesPer100g * f).roundToInt()} kcal · P ${(food.proteinPer100g * f).roundToInt()} g · " +
                        "C ${(food.carbsPer100g * f).roundToInt()} g · G ${(food.fatPer100g * f).roundToInt()} g",
                    color = TextMute,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onAdd(grams, mealType) }, enabled = grams > 0f) { Text("Añadir", color = Ion) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar", color = TextMute) } },
    )
}

// ─── Comidas guardadas ───────────────────────────────────────────────────────

@Composable
fun SavedMealsDialog(
    savedMeals: List<SavedMealEntity>,
    onAdd: (SavedMealEntity, String) -> Unit,
    onDelete: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    var mealType by remember { mutableStateOf(defaultMealTypeForNow()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Panel,
        title = { Text("Mis comidas", color = TextHigh) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (savedMeals.isEmpty()) {
                    Text(
                        "Aún no tienes comidas guardadas. Registra tu desayuno habitual y pulsa “Guardar comida” para reutilizarlo en un toque.",
                        color = TextMute,
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else {
                    Text("Añadir a", color = TextMute, style = MaterialTheme.typography.labelSmall)
                    MealTypeSelector(mealType) { mealType = it }
                    LazyColumn(Modifier.heightIn(max = 320.dp)) {
                        items(savedMeals, key = { it.id }) { saved ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onAdd(saved, mealType) }
                                    .padding(vertical = 10.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(saved.name, color = TextHigh, style = MaterialTheme.typography.bodyMedium)
                                    Text("${saved.totalCalories.roundToInt()} kcal", color = TextMute, style = MaterialTheme.typography.labelSmall)
                                }
                                IconButton(onClick = { onDelete(saved.id) }) {
                                    Icon(Icons.Outlined.DeleteOutline, contentDescription = "Borrar ${saved.name}", tint = TextMute)
                                }
                            }
                            HorizontalDivider(color = Line)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar", color = TextMute) } },
    )
}

@Composable
fun SaveMealDialog(
    state: NutritionUiState,
    onSave: (mealType: String, name: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val withFood = MealSectionType.entries.filter { state.mealsForType(it.key).isNotEmpty() }
    var mealType by remember { mutableStateOf(withFood.firstOrNull()?.key ?: defaultMealTypeForNow()) }
    var name by remember { mutableStateOf("") }
    val items = state.mealsForType(mealType)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Panel,
        title = { Text("Guardar comida", color = TextHigh) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (withFood.isEmpty()) {
                    Text("Registra primero los alimentos de una comida y después guárdala aquí.", color = TextMute, style = MaterialTheme.typography.bodySmall)
                } else {
                    MealTypeSelector(mealType) { mealType = it }
                    Text(
                        if (items.isEmpty()) "No hay alimentos en esta comida." else items.joinToString(" · ") { it.name },
                        color = TextMute,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it.take(40) },
                        label = { Text("Nombre (ej. Mi desayuno)") },
                        singleLine = true,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(mealType, name) }, enabled = items.isNotEmpty()) { Text("Guardar", color = Ion) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar", color = TextMute) } },
    )
}

// ─── Etiqueta nutricional ────────────────────────────────────────────────────

/** Revisión de los valores leídos de la etiqueta (por 100 g) antes de registrar. */
@Composable
fun LabelReviewDialog(
    values: LabelValues,
    onAdd: (FoodCatalogItem, grams: Float, saveFavorite: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    fun fmt(v: Float?) = v?.let { if (it % 1f == 0f) it.roundToInt().toString() else "%.1f".format(it).replace('.', ',') } ?: ""
    var name by remember { mutableStateOf("") }
    var brand by remember { mutableStateOf("") }
    var kcal by remember { mutableStateOf(fmt(values.kcal)) }
    var protein by remember { mutableStateOf(fmt(values.protein)) }
    var carbs by remember { mutableStateOf(fmt(values.carbs)) }
    var sugar by remember { mutableStateOf(fmt(values.sugar)) }
    var fat by remember { mutableStateOf(fmt(values.fat)) }
    var fiber by remember { mutableStateOf(fmt(values.fiber)) }
    var salt by remember { mutableStateOf(fmt(values.salt)) }
    var grams by remember { mutableStateOf("100") }
    var favorite by remember { mutableStateOf(true) }

    fun num(t: String) = t.replace(',', '.').toFloatOrNull() ?: 0f
    val valid = name.isNotBlank() && num(kcal) > 0f && num(grams) > 0f

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Panel,
        title = { Text("Revisa la etiqueta", color = TextHigh) },
        text = {
            Column(
                Modifier.heightIn(max = 460.dp).verticalScrollCompat(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "Valores por 100 g leídos de la foto (${values.foundCount} de 7). Corrige lo que no cuadre.",
                    color = TextMute,
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(name, { name = it.take(60) }, label = { Text("Producto") }, singleLine = true)
                OutlinedTextField(brand, { brand = it.take(30) }, label = { Text("Marca (opcional)") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumField("kcal", kcal, Modifier.weight(1f)) { kcal = it }
                    NumField("Proteína g", protein, Modifier.weight(1f)) { protein = it }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumField("Hidratos g", carbs, Modifier.weight(1f)) { carbs = it }
                    NumField("Azúcar g", sugar, Modifier.weight(1f)) { sugar = it }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumField("Grasa g", fat, Modifier.weight(1f)) { fat = it }
                    NumField("Fibra g", fiber, Modifier.weight(1f)) { fiber = it }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumField("Sal g", salt, Modifier.weight(1f)) { salt = it }
                    NumField("Cantidad g", grams, Modifier.weight(1f)) { grams = it }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = favorite,
                        onCheckedChange = { favorite = it },
                        colors = CheckboxDefaults.colors(checkedColor = Ion, checkmarkColor = Obsidian),
                    )
                    Text("Guardar en favoritos", color = TextHigh, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    val item = FoodCatalogItem(
                        id = "label_${System.currentTimeMillis()}",
                        name = name.trim(),
                        brand = brand.trim().ifBlank { "Etiqueta" },
                        caloriesPer100g = num(kcal),
                        proteinPer100g = num(protein),
                        carbsPer100g = num(carbs),
                        fatPer100g = num(fat),
                        servingSizeGrams = num(grams),
                        servingDescription = "${num(grams).roundToInt()} g",
                        fiberPer100g = num(fiber),
                        sugarPer100g = num(sugar),
                        saltPer100g = num(salt),
                    )
                    onAdd(item, num(grams), favorite)
                },
            ) { Text("Añadir", color = if (valid) Ion else TextDim) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar", color = TextMute) } },
    )
}

@Composable
private fun NumField(label: String, value: String, modifier: Modifier, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.take(7)) },
        label = { Text(label, maxLines = 1) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier,
    )
}

@Composable
private fun Modifier.verticalScrollCompat(): Modifier =
    this.then(Modifier.verticalScroll(rememberScrollState()))
