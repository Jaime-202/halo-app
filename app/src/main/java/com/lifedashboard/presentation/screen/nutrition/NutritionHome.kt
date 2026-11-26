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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
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
    val add: (mealType: String) -> Unit,
    val quickAddFood: (FoodCatalogItem, String) -> Unit,
    val repeatYesterday: (String) -> Unit,
    val editMeal: (MealEntryEntity) -> Unit,
    val deleteMeal: (MealEntryEntity) -> Unit,
    val duplicateMeal: (MealEntryEntity) -> Unit,
    val addWater: () -> Unit,
    val copyYesterday: () -> Unit,
    val saveMeal: () -> Unit,
    val targets: () -> Unit,
    val calculator: () -> Unit,
    val gemini: () -> Unit,
)

/** Colores de cada momento del día en la barra de calorías. */
internal fun mealColor(key: String): Color = when (key) {
    "DESAYUNO" -> Color(0xFFF2C14E)
    "COMIDA" -> Ion
    "MERIENDA" -> Color(0xFFE07BB0)
    "CENA" -> Flux
    else -> Ember
}

private val zone = ZoneId.systemDefault()

/** Hora a la que se comió: la del registro si fue ese día; si no, la típica del momento. */
internal fun mealTime(m: MealEntryEntity): LocalTime {
    val created = Instant.ofEpochMilli(m.createdAt).atZone(zone)
    val day = Instant.ofEpochMilli(m.dateMillis).atZone(zone).toLocalDate()
    return if (created.toLocalDate() == day) created.toLocalTime() else when (m.mealType) {
        "DESAYUNO" -> LocalTime.of(8, 30); "COMIDA" -> LocalTime.of(14, 30); "MERIENDA" -> LocalTime.of(18, 0)
        "CENA" -> LocalTime.of(21, 0); else -> LocalTime.of(12, 0)
    }
}

/**
 * Comida · Hoy: lo que te queda arriba, sugerencias según la hora y el día
 * ordenado por horas. Registrar está siempre a un toque en la barra inferior.
 */
@Composable
internal fun NutritionHome(state: NutritionUiState, a: NutritionHomeActions) {
    var showWeek by remember { mutableStateOf(false) }
    val isToday = state.selectedDate == LocalDate.now()
    val nowType = defaultMealTypeForNow()
    Box(Modifier.fillMaxSize().background(Obsidian)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 52.dp, bottom = 190.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { HomeHeader(state, showWeek, { showWeek = !showWeek }, a) }
            item { DayStrip(state.selectedDate, a.selectDate) }
            item { RemainingCard(state) }
            if (showWeek) item { WeekCard(state) }
            item { WaterRow(state, a.addWater) }
            if (isToday) {
                val sugg = state.suggestionsFor(nowType).take(4)
                item { SuggestedNow(nowType, sugg, onAdd = { a.quickAddFood(it, nowType) }, onRepeat = { a.repeatYesterday(nowType) }) }
            }
            if (state.meals.isEmpty()) {
                item { EmptyDay(onAdd = { a.add(nowType) }) }
            } else {
                // Agrupar por momento, ordenado por la hora real de la primera comida de cada grupo
                val groups = state.meals.groupBy { it.mealType }.entries
                    .sortedWith(compareBy({ (_, l) -> l.minOf { mealTime(it) } }, { (t, _) -> MealSectionType.entries.indexOfFirst { it.key == t } }))
                groups.forEach { (type, meals) ->
                    item(key = "h_$type") { MomentHeader(type, meals) { a.add(type) } }
                    items(meals.sortedBy { mealTime(it) }, key = { "m_${it.id}" }) { meal ->
                        SwipeMealRow(meal, onClick = { a.editMeal(meal) }, onDelete = { a.deleteMeal(meal) }, onDuplicate = { a.duplicateMeal(meal) })
                    }
                }
                item { Text("Desliza un alimento a la izquierda para borrarlo o a la derecha para duplicarlo.", color = TextDim, style = MaterialTheme.typography.labelSmall) }
            }
        }
        // Barra fija para registrar
        val shape = RoundedCornerShape(50)
        Row(
            Modifier.align(Alignment.BottomCenter).padding(start = 20.dp, end = 20.dp, bottom = 108.dp).fillMaxWidth()
                .shadow(16.dp, shape, ambientColor = Ion.copy(alpha = 0.3f), spotColor = Ion.copy(alpha = 0.3f))
                .clip(shape).background(PanelHigh).border(1.dp, Ion.copy(alpha = 0.6f), shape)
                .clickable { a.add(if (isToday) nowType else "COMIDA") }
                .semantics { contentDescription = "Añadir comida" }
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Search, contentDescription = null, tint = Ion)
            Spacer(Modifier.width(12.dp))
            Text("¿Qué has comido?", color = TextHigh, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Icon(Icons.Outlined.QrCodeScanner, contentDescription = null, tint = TextMute, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Icon(Icons.Outlined.PhotoCamera, contentDescription = null, tint = TextMute, modifier = Modifier.size(20.dp))
        }
    }
}

// ─── Cabecera ────────────────────────────────────────────────────────────────

@Composable
private fun HomeHeader(state: NutritionUiState, showWeek: Boolean, onWeek: () -> Unit, a: NutritionHomeActions) {
    var menu by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                if (state.selectedDate == LocalDate.now()) "HOY" else state.selectedDate.format(DateTimeFormatter.ofPattern("EEEE d MMM", es)).uppercase(),
                color = TextMute, style = mono11,
            )
            Text("Comida", color = TextHigh, style = MaterialTheme.typography.headlineMedium)
        }
        IconButton(onClick = onWeek) {
            Icon(Icons.Outlined.BarChart, contentDescription = if (showWeek) "Ocultar semana" else "Ver semana", tint = if (showWeek) Ion else TextHigh)
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = "Más opciones", tint = TextHigh) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = Panel) {
                MenuRow(Icons.Outlined.ContentCopy, "Copiar todo lo de ayer") { menu = false; a.copyYesterday() }
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

// ─── Lo que te queda ─────────────────────────────────────────────────────────

@Composable
private fun RemainingCard(state: NutritionUiState) {
    val shape = RoundedCornerShape(26.dp)
    val target = state.targetCalories.coerceAtLeast(1f)
    val remaining = (state.targetCalories - state.totalCalories).safe()
    val proteinLeft = (state.targetProtein - state.totalProtein).safe().coerceAtLeast(0f)
    val byType = MealSectionType.entries.map { it.key to state.mealsForType(it.key).sumOf { m -> m.calories.safe().toDouble() }.toFloat() }.filter { it.second > 0f }
    val anim = remember { Animatable(0f) }
    LaunchedEffect(state.selectedDate) { anim.snapTo(0f); anim.animateTo(1f, tween(800)) }

    Column(
        Modifier.fillMaxWidth().clip(shape).background(Panel).border(1.dp, Line, shape).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column {
            Text(if (remaining >= 0) "TE QUEDAN" else "POR ENCIMA DEL OBJETIVO", color = TextMute, style = mono11)
            Row(verticalAlignment = Alignment.Bottom) {
                Text("%,d".format(es, kotlin.math.abs(remaining).roundToInt()), color = TextHigh, style = MaterialTheme.typography.displayMedium)
                Spacer(Modifier.width(8.dp))
                Text(
                    if (remaining >= 0) "kcal · ${proteinLeft.roundToInt()} g de proteína" else "kcal",
                    color = TextMute, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 10.dp),
                )
            }
        }
        // Barra del día: un tramo por momento
        Row(Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp)).background(Line)) {
            byType.forEach { (k, kcal) ->
                Box(Modifier.fillMaxHeight().weight((kcal / target * anim.value).coerceAtLeast(0.001f)).background(mealColor(k)))
                Spacer(Modifier.width(2.dp))
            }
            val rest = (1f - state.totalCalories.safe() / target * anim.value).coerceAtLeast(0f)
            if (rest > 0f) Spacer(Modifier.weight(rest))
        }
        if (byType.isNotEmpty()) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                byType.forEach { (k, kcal) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(mealColor(k)))
                        Spacer(Modifier.width(5.dp))
                        Text("${MealSectionType.labelFor(k)} ${kcal.roundToInt()}", color = TextMute, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            MacroMini("Proteína", state.totalProtein, state.targetProtein, MacroColors.protein, Modifier.weight(1f))
            MacroMini("Hidratos", state.totalCarbs, state.targetCarbs, MacroColors.carbs, Modifier.weight(1f))
            MacroMini("Grasa", state.totalFat, state.targetFat, MacroColors.fat, Modifier.weight(1f))
        }
        Text(
            "${state.totalCalories.safe().roundToInt()} de ${state.targetCalories.roundToInt()} kcal" +
                if (state.activeBurnedCalories > 0f) " · ${state.activeBurnedCalories.roundToInt()} kcal en movimiento" else "",
            color = TextDim, style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun MacroMini(label: String, value: Float, target: Float, color: Color, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label.uppercase(), color = TextMute, style = mono11)
        Row(verticalAlignment = Alignment.Bottom) {
            Text("${value.safe().roundToInt()}", color = TextHigh, style = MaterialTheme.typography.titleMedium)
            Text("/${target.roundToInt()} g", color = TextMute, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(bottom = 2.dp))
        }
        Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(Line)) {
            Box(Modifier.fillMaxWidth(if (target > 0) (value.safe() / target).coerceIn(0f, 1f) else 0f).fillMaxHeight().background(color))
        }
    }
}

// ─── Semana ──────────────────────────────────────────────────────────────────

@Composable
private fun WeekCard(state: NutritionUiState) {
    val shape = RoundedCornerShape(22.dp)
    val target = state.targetCalories.coerceAtLeast(1f)
    val logged = state.weekDays.filter { it.kcal > 0f }
    val max = maxOf(state.weekDays.maxOfOrNull { it.kcal } ?: 0f, target) * 1.1f
    Column(
        Modifier.fillMaxWidth().clip(shape).background(Panel).border(1.dp, Line, shape).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row {
            Text("ÚLTIMOS 7 DÍAS", color = TextMute, style = mono11, modifier = Modifier.weight(1f))
            if (logged.isNotEmpty()) Text("media ${logged.map { it.kcal }.average().roundToInt()} kcal", color = TextMute, style = MaterialTheme.typography.labelSmall)
        }
        Box(Modifier.fillMaxWidth().height(110.dp)) {
            val line = TextDim
            Canvas(Modifier.fillMaxSize()) {
                // Mismo origen que las barras: base = alto - etiqueta; barras de 96 dp como máximo
                val base = size.height - 14.dp.toPx()
                val y = base - 96.dp.toPx() * (target / max)
                drawLine(line, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))
            }
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                state.weekDays.forEach { d ->
                    val sel = d.date == state.selectedDate
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier.fillMaxWidth().height((96f * (d.kcal / max)).coerceAtLeast(3f).dp)
                                .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomStart = 2.dp, bottomEnd = 2.dp))
                                .background(if (d.kcal > target * 1.1f) Ember else if (sel) Ion else Flux.copy(alpha = 0.7f))
                        )
                        Text(d.date.dayOfWeek.getDisplayName(JTextStyle.NARROW, es).uppercase(), color = if (sel) TextHigh else TextMute, style = mono11)
                    }
                }
            }
        }
        if (logged.isNotEmpty()) {
            val avgP = logged.map { it.protein }.average().roundToInt()
            val inTarget = logged.count { kotlin.math.abs(it.kcal - target) <= target * 0.1f }
            Text("Proteína media $avgP g · $inTarget de ${logged.size} días cerca del objetivo (±10 %)", color = TextMute, style = MaterialTheme.typography.bodySmall)
        }
        val top = state.recentEntries.groupBy { it.name }.entries.sortedByDescending { it.value.size }.take(3).map { it.key }
        if (top.isNotEmpty()) Text("Lo que más repites: ${top.joinToString(", ")}", color = TextMute, style = MaterialTheme.typography.bodySmall)
    }
}

// ─── Agua ────────────────────────────────────────────────────────────────────

@Composable
private fun WaterRow(state: NutritionUiState, onAdd: () -> Unit) {
    val haptics = rememberHaloHaptics()
    val glasses = (state.waterTargetMl / 250).coerceIn(4, 12)
    val filled = state.waterMl / 250
    val shape = RoundedCornerShape(20.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(Panel).border(1.dp, Line, shape).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.width(76.dp)) {
            Text("AGUA", color = TextMute, style = mono11)
            Text("%.1f / %.1f L".format(es, state.waterMl / 1000f, state.waterTargetMl / 1000f), color = TextHigh, style = MaterialTheme.typography.labelMedium)
        }
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceBetween) {
            (0 until glasses).forEach { i ->
                val on = i < filled
                Icon(
                    if (on) Icons.Filled.WaterDrop else Icons.Outlined.WaterDrop,
                    contentDescription = if (i == filled) "Añadir un vaso de agua" else null,
                    tint = if (on) Flux else LineStrong,
                    modifier = Modifier.size(22.dp).clip(CircleShape).clickable(enabled = i >= filled) { haptics.tick(); onAdd() },
                )
            }
        }
    }
}

// ─── Sugerido ahora ──────────────────────────────────────────────────────────

@Composable
private fun SuggestedNow(type: String, foods: List<FoodCatalogItem>, onAdd: (FoodCatalogItem) -> Unit, onRepeat: () -> Unit) {
    val haptics = rememberHaloHaptics()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("SUGERIDO AHORA · ${MealSectionType.labelFor(type).uppercase()}", color = TextMute, style = mono11)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            foods.forEach { f ->
                val s = RoundedCornerShape(50)
                Row(
                    Modifier.clip(s).background(PanelHigh).border(1.dp, Ion.copy(alpha = 0.4f), s).clickable { haptics.confirm(); onAdd(f) }
                        .semantics { contentDescription = "Añadir ${f.name}" }
                        .padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(28.dp).clip(CircleShape)) { FoodThumbnail(f.name, f.brand, f.imageUrl, size = 28.dp) }
                    Spacer(Modifier.width(8.dp))
                    Text(f.name, color = TextHigh, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 150.dp))
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Outlined.Add, contentDescription = null, tint = Ion, modifier = Modifier.size(16.dp))
                }
            }
            val s = RoundedCornerShape(50)
            Row(
                Modifier.clip(s).border(1.dp, LineStrong, s).clickable(onClick = onRepeat).padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.History, contentDescription = null, tint = TextMute, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Lo de ayer", color = TextHigh, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

// ─── Día vacío ───────────────────────────────────────────────────────────────

@Composable
private fun EmptyDay(onAdd: () -> Unit) {
    val shape = RoundedCornerShape(26.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).border(1.dp, Line, shape).clickable(onClick = onAdd).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Ilustración: plato vacío con cubiertos
        val rim = LineStrong; val accent = Ion
        Canvas(Modifier.size(width = 150.dp, height = 90.dp)) {
            val c = Offset(size.width / 2, size.height / 2)
            val r = size.height / 2 - 4.dp.toPx()
            drawCircle(rim, r, c, style = Stroke(2.dp.toPx()))
            drawCircle(rim.copy(alpha = 0.6f), r * 0.68f, c, style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 7f))))
            drawArc(accent, -100f, 40f, false, Offset(c.x - r * 0.68f, c.y - r * 0.68f), Size(r * 1.36f, r * 1.36f), style = Stroke(3.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round))
            val fx = c.x - r - 18.dp.toPx(); val kx = c.x + r + 18.dp.toPx()
            drawLine(rim, Offset(fx, 8.dp.toPx()), Offset(fx, size.height - 8.dp.toPx()), 2.dp.toPx())
            listOf(-5, 0, 5).forEach { dx -> drawLine(rim, Offset(fx + dx.dp.toPx(), 8.dp.toPx()), Offset(fx + dx.dp.toPx(), 26.dp.toPx()), 1.5.dp.toPx()) }
            drawLine(rim, Offset(kx, 8.dp.toPx()), Offset(kx, size.height - 8.dp.toPx()), 2.dp.toPx())
            drawRoundRect(rim, Offset(kx - 3.dp.toPx(), 8.dp.toPx()), Size(6.dp.toPx(), 26.dp.toPx()), androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()))
        }
        Text("Aún no has apuntado nada", color = TextHigh, style = MaterialTheme.typography.titleMedium)
        Text("Busca, escanea o haz una foto. Puedes añadir varios alimentos a la vez.", color = TextMute, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
    }
}

// ─── El día por horas ────────────────────────────────────────────────────────

@Composable
private fun MomentHeader(type: String, meals: List<MealEntryEntity>, onAdd: () -> Unit) {
    val time = meals.minOf { mealTime(it) }
    val kcal = meals.sumOf { it.calories.safe().toDouble() }.roundToInt()
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(34.dp).clip(CircleShape).background(mealColor(type).copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
            Icon(mealIcon(type), contentDescription = null, tint = mealColor(type), modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(MealSectionType.labelFor(type), color = TextHigh, style = MaterialTheme.typography.titleMedium)
            Text("${time.format(DateTimeFormatter.ofPattern("HH:mm"))} · $kcal kcal", color = TextMute, style = MaterialTheme.typography.labelMedium)
        }
        IconButton(onClick = onAdd) { Icon(Icons.Outlined.AddCircleOutline, contentDescription = "Añadir a ${MealSectionType.labelFor(type)}", tint = Ion) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeMealRow(meal: MealEntryEntity, onClick: () -> Unit, onDelete: () -> Unit, onDuplicate: () -> Unit) {
    val haptics = rememberHaloHaptics()
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { v ->
            when (v) {
                SwipeToDismissBoxValue.EndToStart -> { haptics.reject(); onDelete(); true }
                SwipeToDismissBoxValue.StartToEnd -> { haptics.confirm(); onDuplicate(); false }
                else -> false
            }
        },
    )
    SwipeToDismissBox(
        state = state,
        backgroundContent = {
            val toDelete = state.dismissDirection == SwipeToDismissBoxValue.EndToStart
            Row(
                Modifier.fillMaxSize().clip(RoundedCornerShape(18.dp)).background(if (toDelete) Rose400.copy(alpha = 0.2f) else Ion.copy(alpha = 0.15f)).padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (toDelete) Arrangement.End else Arrangement.Start,
            ) {
                Icon(if (toDelete) Icons.Outlined.DeleteOutline else Icons.Outlined.ContentCopy, contentDescription = null, tint = if (toDelete) Rose400 else Ion)
                Spacer(Modifier.width(8.dp))
                Text(if (toDelete) "Borrar" else "Duplicar", color = if (toDelete) Rose400 else Ion, style = MaterialTheme.typography.labelLarge)
            }
        },
    ) {
        val shape = RoundedCornerShape(18.dp)
        Row(
            Modifier.fillMaxWidth().clip(shape).background(Panel).clickable(onClick = onClick).padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FoodThumbnail(name = meal.name, brand = meal.brand, imageUrl = meal.imageUrl, size = 46.dp)
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
            Text("${meal.calories.safe().roundToInt()}", color = TextHigh, style = TextStyle(fontFamily = MonoFamily, fontSize = 15.sp))
            Text(" kcal", color = TextMute, style = MaterialTheme.typography.labelSmall)
        }
    }
}
