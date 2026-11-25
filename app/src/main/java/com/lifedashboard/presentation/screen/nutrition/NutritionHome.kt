package com.lifedashboard.presentation.screen.nutrition

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifedashboard.data.local.entity.MealEntryEntity
import com.lifedashboard.data.remote.food.FoodCatalogItem
import com.lifedashboard.presentation.theme.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as JTextStyle
import java.util.Locale
import kotlin.math.roundToInt

private val es = Locale("es", "ES")
private val mono11 = TextStyle(fontFamily = MonoFamily, fontSize = 11.sp, letterSpacing = 1.2.sp)
private fun Float.safe() = if (isNaN() || isInfinite()) 0f else this

/** Colores de los macros en toda la pantalla de Comida. */
internal object MacroColors {
    val protein: Color get() = Ion
    val carbs: Color get() = Flux
    val fat: Color get() = Ember
}

/** Icono, franja horaria y nombre de cada momento del día (sin emojis). */
internal fun mealIcon(key: String): ImageVector = when (key) {
    "DESAYUNO" -> Icons.Outlined.WbTwilight
    "COMIDA" -> Icons.Outlined.LightMode
    "MERIENDA" -> Icons.Outlined.LocalCafe
    "CENA" -> Icons.Outlined.DarkMode
    else -> Icons.Outlined.Cookie
}

internal fun mealHours(key: String): String = when (key) {
    "DESAYUNO" -> "7 – 10 h"
    "COMIDA" -> "13 – 16 h"
    "MERIENDA" -> "17 – 19 h"
    "CENA" -> "20 – 23 h"
    else -> "A cualquier hora"
}

/** Acciones de la pantalla Comida. */
internal class NutritionHomeActions(
    val selectDate: (LocalDate) -> Unit,
    val search: (mealType: String) -> Unit,
    val scan: (mealType: String) -> Unit,
    val photo: (mealType: String) -> Unit,
    val label: () -> Unit,
    val describe: (mealType: String) -> Unit,
    val quickAdd: (mealType: String) -> Unit,
    val editMeal: (MealEntryEntity) -> Unit,
    val quickFood: (FoodCatalogItem) -> Unit,
    val copyYesterday: () -> Unit,
    val savedMeals: () -> Unit,
    val saveMeal: () -> Unit,
    val targets: () -> Unit,
    val calculator: () -> Unit,
    val gemini: () -> Unit,
    val refreshAdvice: () -> Unit,
)

/**
 * Comida: el plato del día, una barra para registrar y el día como línea de tiempo.
 */
@Composable
internal fun NutritionHome(state: NutritionUiState, a: NutritionHomeActions) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Obsidian),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 52.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item { HomeHeader(state, a) }
        item { DayStrip(state.selectedDate, a.selectDate) }
        item { PlateCard(state, onClick = a.targets) }
        item { AddBar(a) }
        item {
            Text("TU DÍA", color = TextMute, style = mono11, modifier = Modifier.padding(top = 4.dp))
        }
        val sections = MealSectionType.entries
        sections.forEachIndexed { i, section ->
            item(key = section.key) {
                TimelineMoment(
                    section = section,
                    meals = state.mealsForType(section.key),
                    isFirst = i == 0,
                    isLast = i == sections.lastIndex,
                    a = a,
                )
            }
        }
        val repeat = (state.favorites + state.recents).distinctBy { NutritionViewModel.foodKey(it.name, it.brand) }.take(12)
        if (repeat.isNotEmpty()) {
            item { RepeatCarousel(repeat, state.favoriteKeys, a.quickFood) }
        }
        item { DetailsCard(state, a.refreshAdvice) }
    }
}

// ─── Cabecera y días ─────────────────────────────────────────────────────────

@Composable
private fun HomeHeader(state: NutritionUiState, a: NutritionHomeActions) {
    var menu by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                if (state.selectedDate == LocalDate.now()) "HOY" else state.selectedDate.format(DateTimeFormatter.ofPattern("EEEE d MMM", es)).uppercase(),
                color = TextMute, style = mono11,
            )
            Text("Comida", color = TextHigh, style = MaterialTheme.typography.headlineMedium)
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = "Más opciones", tint = TextHigh) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = Panel) {
                MenuRow(Icons.Outlined.ContentCopy, "Copiar comidas de ayer") { menu = false; a.copyYesterday() }
                MenuRow(Icons.Outlined.Bookmarks, "Mis comidas guardadas") { menu = false; a.savedMeals() }
                MenuRow(Icons.Outlined.BookmarkAdd, "Guardar una comida") { menu = false; a.saveMeal() }
                HorizontalDivider(color = Line)
                MenuRow(Icons.Outlined.Tune, "Objetivos y macros") { menu = false; a.targets() }
                MenuRow(Icons.Outlined.Calculate, "Calculadora de calorías") { menu = false; a.calculator() }
                MenuRow(Icons.Outlined.AutoAwesome, if (state.hasGeminiApiKey) "Gemini activado" else "Activar Gemini") { menu = false; a.gemini() }
            }
        }
    }
}

@Composable
private fun MenuRow(icon: ImageVector, text: String, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(text, color = TextHigh) },
        leadingIcon = { Icon(icon, contentDescription = null, tint = Ion) },
        onClick = onClick,
    )
}

@Composable
private fun DayStrip(selected: LocalDate, onSelect: (LocalDate) -> Unit) {
    val today = LocalDate.now()
    // Semana de 7 días que termina hoy (o el día elegido, si es anterior)
    val end = if (selected.isAfter(today.minusDays(4))) today else selected.plusDays(3)
    val days = (6 downTo 0).map { end.minusDays(it.toLong()) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        IconButton(onClick = { onSelect(selected.minusDays(1)) }, modifier = Modifier.size(36.dp).align(Alignment.CenterVertically)) {
            Icon(Icons.Outlined.ChevronLeft, contentDescription = "Día anterior", tint = TextMute)
        }
        days.forEach { d ->
            val on = d == selected
            val shape = RoundedCornerShape(14.dp)
            Column(
                Modifier.weight(1f).clip(shape)
                    .background(if (on) Ion else Color.Transparent)
                    .border(1.dp, if (on) Ion else if (d == today) LineStrong else Color.Transparent, shape)
                    .clickable(role = Role.Tab) { onSelect(d) }
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    d.dayOfWeek.getDisplayName(JTextStyle.NARROW, es).uppercase(),
                    color = if (on) OnAccent.copy(alpha = 0.75f) else TextMute, style = mono11,
                )
                Text("${d.dayOfMonth}", color = if (on) OnAccent else TextHigh, style = MaterialTheme.typography.titleMedium)
            }
        }
        IconButton(
            onClick = { onSelect(selected.plusDays(1)) }, enabled = selected.isBefore(today),
            modifier = Modifier.size(36.dp).align(Alignment.CenterVertically),
        ) {
            Icon(Icons.Outlined.ChevronRight, contentDescription = "Día siguiente", tint = if (selected.isBefore(today)) TextMute else TextDim)
        }
    }
}

// ─── El plato ────────────────────────────────────────────────────────────────

/**
 * El plato del día: tres porciones (proteína, hidratos, grasa). El ángulo de cada
 * porción es su parte de las calorías objetivo y se va llenando desde el centro
 * según lo que comes. En el centro, las calorías que te quedan.
 */
@Composable
private fun PlateCard(state: NutritionUiState, onClick: () -> Unit) {
    val shape = RoundedCornerShape(28.dp)
    val pK = state.targetProtein * 4f
    val cK = state.targetCarbs * 4f
    val fK = state.targetFat * 9f
    val totalK = (pK + cK + fK).takeIf { it > 0f } ?: 1f
    val shares = listOf(pK / totalK, cK / totalK, fK / totalK)
    val progress = listOf(
        if (state.targetProtein > 0) (state.totalProtein / state.targetProtein).safe().coerceIn(0f, 1f) else 0f,
        if (state.targetCarbs > 0) (state.totalCarbs / state.targetCarbs).safe().coerceIn(0f, 1f) else 0f,
        if (state.targetFat > 0) (state.totalFat / state.targetFat).safe().coerceIn(0f, 1f) else 0f,
    )
    val anim = remember { Animatable(0f) }
    LaunchedEffect(state.selectedDate) { anim.snapTo(0f); anim.animateTo(1f, tween(900)) }
    val colors = listOf(MacroColors.protein, MacroColors.carbs, MacroColors.fat)
    val remaining = (state.targetCalories - state.totalCalories).safe()

    Column(
        Modifier.fillMaxWidth().clip(shape).background(Panel).border(1.dp, Line, shape).clickable(onClick = onClick).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            val rim = LineStrong
            val track = Line
            Canvas(Modifier.size(220.dp)) {
                val r = size.minDimension / 2f
                val c = Offset(size.width / 2f, size.height / 2f)
                // Borde del plato
                drawCircle(rim, radius = r, center = c, style = Stroke(width = 2.dp.toPx()))
                drawCircle(rim.copy(alpha = 0.5f), radius = r - 10.dp.toPx(), center = c, style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 8f))))
                val inner = r * 0.36f
                val outer = r - 16.dp.toPx()
                var start = -90f
                shares.forEachIndexed { i, share ->
                    val sweep = 360f * share - 3f
                    // Porción vacía
                    drawArc(track, start, sweep, true, Offset(c.x - outer, c.y - outer), Size(outer * 2, outer * 2))
                    // Porción llena según el progreso
                    val filled = inner + (outer - inner) * progress[i] * anim.value
                    if (progress[i] > 0f) {
                        drawArc(colors[i], start, sweep, true, Offset(c.x - filled, c.y - filled), Size(filled * 2, filled * 2))
                    }
                    start += 360f * share
                }
                // Hueco central
                drawCircle(Color.Black.copy(alpha = 0f), inner, c)
            }
            Box(Modifier.size(110.dp).clip(CircleShape).background(Panel), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (remaining >= 0) "${remaining.roundToInt()}" else "+${(-remaining).roundToInt()}",
                        color = TextHigh, style = MaterialTheme.typography.headlineMedium,
                    )
                    Text(if (remaining >= 0) "kcal quedan" else "kcal de más", color = TextMute, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            MacroLegend("Proteína", state.totalProtein, state.targetProtein, MacroColors.protein)
            MacroLegend("Hidratos", state.totalCarbs, state.targetCarbs, MacroColors.carbs)
            MacroLegend("Grasa", state.totalFat, state.targetFat, MacroColors.fat)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${state.totalCalories.safe().roundToInt()} de ${state.targetCalories.roundToInt()} kcal", color = TextMute, style = MaterialTheme.typography.labelMedium)
            if (state.activeBurnedCalories > 0f) {
                Text("+${state.activeBurnedCalories.roundToInt()} kcal en movimiento", color = TextMute, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun MacroLegend(label: String, value: Float, target: Float, color: Color) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(6.dp))
            Text(label.uppercase(), color = TextMute, style = mono11)
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text("${value.safe().roundToInt()}", color = TextHigh, style = MaterialTheme.typography.titleLarge)
            Text(" / ${target.roundToInt()} g", color = TextMute, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(bottom = 3.dp))
        }
    }
}

// ─── Barra para registrar ────────────────────────────────────────────────────

@Composable
private fun AddBar(a: NutritionHomeActions) {
    val now = defaultMealTypeForNow()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        val shape = RoundedCornerShape(50)
        Row(
            Modifier.fillMaxWidth().clip(shape).background(Panel).border(1.dp, LineStrong, shape)
                .clickable { a.search(now) }.padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Search, contentDescription = null, tint = Ion)
            Spacer(Modifier.width(12.dp))
            Text("¿Qué has comido?", color = TextMute, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(MealSectionType.labelFor(now), color = TextDim, style = MaterialTheme.typography.labelMedium)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AddChip(Icons.Outlined.QrCodeScanner, "Código", Modifier.weight(1f)) { a.scan(now) }
            AddChip(Icons.Outlined.PhotoCamera, "Foto", Modifier.weight(1f)) { a.photo(now) }
            AddChip(Icons.Outlined.DocumentScanner, "Etiqueta", Modifier.weight(1f)) { a.label() }
            AddChip(Icons.Outlined.AutoAwesome, "Describir", Modifier.weight(1f)) { a.describe(now) }
        }
    }
}

@Composable
private fun AddChip(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier.clip(shape).background(PanelHigh).clickable(onClick = onClick).padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, contentDescription = null, tint = Ion, modifier = Modifier.size(22.dp))
        Text(label, color = TextHigh, style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}

// ─── Línea de tiempo del día ─────────────────────────────────────────────────

@Composable
private fun TimelineMoment(
    section: MealSectionType,
    meals: List<MealEntryEntity>,
    isFirst: Boolean,
    isLast: Boolean,
    a: NutritionHomeActions,
) {
    val kcal = meals.sumOf { it.calories.safe().toDouble() }.roundToInt()
    val done = meals.isNotEmpty()
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        // Eje: línea vertical con el icono del momento
        Box(Modifier.width(44.dp).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
            val line = Line
            Canvas(Modifier.fillMaxSize()) {
                val x = size.width / 2
                if (!isFirst) drawLine(line, Offset(x, 0f), Offset(x, 22.dp.toPx()), strokeWidth = 2.dp.toPx())
                if (!isLast) drawLine(line, Offset(x, 22.dp.toPx()), Offset(x, size.height), strokeWidth = 2.dp.toPx())
            }
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(if (done) Ion else Panel).border(1.dp, if (done) Ion else LineStrong, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(mealIcon(section.key), contentDescription = null, tint = if (done) OnAccent else TextMute, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f).padding(bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 40.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(section.title, color = TextHigh, style = MaterialTheme.typography.titleMedium)
                    Text(if (done) "$kcal kcal" else mealHours(section.key), color = if (done) Ion else TextDim, style = MaterialTheme.typography.labelMedium)
                }
                IconButton(onClick = { a.search(section.key) }) {
                    Icon(Icons.Outlined.AddCircleOutline, contentDescription = "Añadir a ${section.title}", tint = Ion)
                }
            }
            meals.forEach { meal -> FoodRow(meal) { a.editMeal(meal) } }
            if (!done) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallAction("Buscar") { a.search(section.key) }
                    SmallAction("Foto") { a.photo(section.key) }
                    SmallAction("Rápido") { a.quickAdd(section.key) }
                }
            }
        }
    }
}

@Composable
private fun SmallAction(label: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Box(
        Modifier.clip(shape).border(1.dp, LineStrong, shape).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 6.dp)
    ) { Text(label, color = TextMute, style = MaterialTheme.typography.labelMedium) }
}

@Composable
private fun FoodRow(meal: MealEntryEntity, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(Panel).clickable(onClick = onClick).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FoodThumbnail(name = meal.name, brand = meal.brand, imageUrl = meal.imageUrl, size = 44.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(meal.name, color = TextHigh, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val brand = meal.brand.takeIf { it.isNotBlank() && it != "Manual" && it != "Básico" }
            Text(
                listOfNotNull(brand, "${meal.grams.roundToInt()} g", "P ${meal.protein.safe().roundToInt()} · H ${meal.carbs.safe().roundToInt()} · G ${meal.fat.safe().roundToInt()}").joinToString(" · "),
                color = TextMute, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text("${meal.calories.safe().roundToInt()}", color = TextHigh, style = TextStyle(fontFamily = MonoFamily, fontSize = 14.sp))
        Text(" kcal", color = TextMute, style = MaterialTheme.typography.labelSmall)
    }
}

// ─── Para repetir ────────────────────────────────────────────────────────────

@Composable
private fun RepeatCarousel(foods: List<FoodCatalogItem>, favoriteKeys: Set<String>, onPick: (FoodCatalogItem) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("PARA REPETIR", color = TextMute, style = mono11)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            foods.forEach { food ->
                val shape = RoundedCornerShape(18.dp)
                Column(
                    Modifier.width(132.dp).clip(shape).background(Panel).border(1.dp, Line, shape).clickable { onPick(food) },
                ) {
                    Box {
                        FoodThumbnail(name = food.name, brand = food.brand, imageUrl = food.imageUrl, size = 132.dp, modifier = Modifier.height(88.dp))
                        if (NutritionViewModel.foodKey(food.name, food.brand) in favoriteKeys) {
                            Icon(
                                Icons.Outlined.Star, contentDescription = "Favorito", tint = Ion,
                                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(18.dp),
                            )
                        }
                    }
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(food.name, color = TextHigh, style = MaterialTheme.typography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis, minLines = 2)
                        Text(
                            "${(food.caloriesPer100g * food.servingSizeGrams / 100f).safe().roundToInt()} kcal · ${food.servingSizeGrams.roundToInt()} g",
                            color = TextMute, style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
        }
    }
}

// ─── Detalle: fibra, azúcar, sal y consejo ───────────────────────────────────

@Composable
private fun DetailsCard(state: NutritionUiState, onRefresh: () -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(Panel).border(1.dp, Line, shape).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Mini("Fibra", "${state.totalFiber.safe().roundToInt()} g", "objetivo 25–30 g")
            Mini("Azúcar", "${state.totalSugar.safe().roundToInt()} g", "máx. 50 g")
            Mini("Sal", "%.1f g".format(es, state.totalSalt.safe()), "máx. 5 g")
        }
        HorizontalDivider(color = Line)
        Row(verticalAlignment = Alignment.Top) {
            Icon(Icons.Outlined.TipsAndUpdates, contentDescription = null, tint = Ion, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                when {
                    state.isAdviceLoading -> "Preparando un consejo…"
                    !state.nutritionAdvice.isNullOrBlank() -> state.nutritionAdvice!!
                    else -> "Registra lo que comes y aquí verás un consejo para el resto del día."
                },
                color = TextHigh, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onRefresh, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Outlined.Refresh, contentDescription = "Nuevo consejo", tint = TextMute, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun Mini(label: String, value: String, hint: String) {
    Column {
        Text(label.uppercase(), color = TextMute, style = mono11)
        Text(value, color = TextHigh, style = MaterialTheme.typography.titleMedium)
        Text(hint, color = TextDim, style = MaterialTheme.typography.labelSmall)
    }
}
