package com.lifedashboard.presentation.screen.nutrition

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifedashboard.data.remote.food.FoodCatalogItem
import com.lifedashboard.presentation.theme.*
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.roundToInt

private val es = Locale("es", "ES")
private val mono11 = TextStyle(fontFamily = MonoFamily, fontSize = 11.sp, letterSpacing = 1.2.sp)

/** Nutri-Score oficial (A verde → E rojo). */
@Composable
internal fun NutriScoreBadge(grade: String?, modifier: Modifier = Modifier) {
    val g = grade?.lowercase() ?: return
    val color = when (g) {
        "a" -> Color(0xFF1E8F4E); "b" -> Color(0xFF7AC043); "c" -> Color(0xFFE5B812)
        "d" -> Color(0xFFEF7D22); else -> Color(0xFFE63E11)
    }
    Box(
        modifier.clip(RoundedCornerShape(5.dp)).background(color).padding(horizontal = 6.dp, vertical = 1.dp),
        contentAlignment = Alignment.Center,
    ) { Text(g.uppercase(), color = Color.White, style = TextStyle(fontFamily = MonoFamily, fontSize = 11.sp)) }
}

internal fun novaLabel(n: Int?) = when (n) {
    1 -> "Sin procesar"; 2 -> "Ingrediente culinario"; 3 -> "Procesado"; 4 -> "Ultraprocesado"; else -> null
}

/** Raciones rápidas para un alimento, en gramos. */
internal fun portionsFor(food: FoodCatalogItem): List<Pair<String, Float>> = buildList {
    val s = food.servingSizeGrams
    if (s > 0f && s != 100f) {
        add(food.servingDescription.ifBlank { "1 ración" }.let { if (it.length > 18) "1 ración" else it } to s)
        add("2 raciones" to s * 2)
    }
    add("100 g" to 100f)
    if (s != 50f && s < 100f) add("50 g" to 50f)
}.distinctBy { it.second }

/**
 * Ficha del alimento: foto, Nutri-Score, raciones en botones, selector +/− y
 * macros que cambian al momento. Se usa para añadir y para editar lo apuntado.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FoodDetailSheet(
    food: FoodCatalogItem,
    initialGrams: Float,
    remainingKcal: Float,
    remainingProtein: Float,
    isFavorite: Boolean,
    primaryLabel: String,
    onPrimary: (Float) -> Unit,
    onToggleFavorite: () -> Unit,
    onDelete: (() -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    var grams by remember { mutableStateOf(initialGrams.coerceAtLeast(1f)) }
    val f = grams / 100f
    val kcal = food.caloriesPer100g * f
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = Panel, dragHandle = { BottomSheetDefaults.DragHandle(color = LineStrong) }) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(22.dp))) {
                FoodThumbnail(name = food.name, brand = food.brand, imageUrl = food.imageUrl, size = 400.dp, modifier = Modifier.fillMaxSize())
                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).clip(CircleShape).background(Panel.copy(alpha = 0.85f)),
                ) {
                    Icon(if (isFavorite) Icons.Outlined.Star else Icons.Outlined.StarBorder, contentDescription = if (isFavorite) "Quitar de favoritos" else "Añadir a favoritos", tint = Ion)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(food.name, color = TextHigh, style = MaterialTheme.typography.titleLarge)
                    Text(
                        listOfNotNull(food.brand.takeIf { it.isNotBlank() && it != "Básico" }, novaLabel(food.novaGroup)).joinToString(" · ").ifBlank { "Alimento genérico" },
                        color = TextMute, style = MaterialTheme.typography.bodySmall,
                    )
                }
                NutriScoreBadge(food.nutriScore)
            }

            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                portionsFor(food).forEach { (label, g) ->
                    val on = kotlin.math.abs(grams - g) < 0.5f
                    val shape = RoundedCornerShape(50)
                    Box(
                        Modifier.clip(shape).background(if (on) Ion else Color.Transparent).border(1.dp, if (on) Ion else LineStrong, shape)
                            .clickable { grams = g }.padding(horizontal = 12.dp, vertical = 7.dp)
                    ) { Text(label, color = if (on) OnAccent else TextHigh, style = MaterialTheme.typography.labelMedium, maxLines = 1) }
                }
            }

            // Selector de cantidad sin teclado
            val shape = RoundedCornerShape(18.dp)
            Row(
                Modifier.fillMaxWidth().clip(shape).border(1.dp, LineStrong, shape).padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RepeatButton(Icons.Outlined.Remove, "Menos") { grams = (grams - step(grams)).coerceAtLeast(5f) }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${grams.roundToInt()} g", color = TextHigh, style = MaterialTheme.typography.headlineSmall)
                    if (food.servingSizeGrams > 0f && food.servingSizeGrams != 100f) {
                        val r = grams / food.servingSizeGrams
                        val txt = if (kotlin.math.abs(r - r.roundToInt()) < 0.05f) r.roundToInt().let { if (it == 1) "1 ración" else "$it raciones" }
                                  else "%.1f raciones".format(es, r)
                        Text(txt, color = TextMute, style = MaterialTheme.typography.labelSmall)
                    }
                }
                RepeatButton(Icons.Outlined.Add, "Más") { grams += step(grams) }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Stat("kcal", kcal, TextHigh)
                Stat("Proteína", food.proteinPer100g * f, MacroColors.protein, "g")
                Stat("Hidratos", food.carbsPer100g * f, MacroColors.carbs, "g")
                Stat("Grasa", food.fatPer100g * f, MacroColors.fat, "g")
            }

            if (food.sugarPer100g > 0f || food.fiberPer100g > 0f || food.saltPer100g > 0f) {
                val s2 = RoundedCornerShape(14.dp)
                Column(Modifier.fillMaxWidth().clip(s2).border(1.dp, Line, s2).padding(horizontal = 12.dp, vertical = 6.dp)) {
                    listOf("Azúcares" to food.sugarPer100g * f, "Fibra" to food.fiberPer100g * f, "Sal" to food.saltPer100g * f).forEach { (k, v) ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Text(k, color = TextMute, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            Text("%.1f g".format(es, v), color = TextHigh, style = TextStyle(fontFamily = MonoFamily, fontSize = 12.sp))
                        }
                    }
                }
            }

            val after = remainingKcal - kcal
            val afterP = (remainingProtein - food.proteinPer100g * f).coerceAtLeast(0f)
            Text(
                if (after >= 0) "Tras añadirlo te quedan ${after.roundToInt()} kcal y ${afterP.roundToInt()} g de proteína."
                else "Con esto pasas tu objetivo en ${(-after).roundToInt()} kcal.",
                color = TextMute, style = MaterialTheme.typography.bodySmall,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (onDelete != null) {
                    OutlinedButton(
                        onClick = onDelete, shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, LineStrong),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextMute),
                        modifier = Modifier.height(52.dp),
                    ) {
                        Icon(Icons.Outlined.DeleteOutline, contentDescription = null)
                        Spacer(Modifier.width(6.dp)); Text("Borrar")
                    }
                }
                Button(
                    onClick = { onPrimary(grams) }, shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Ion, contentColor = OnAccent),
                    modifier = Modifier.weight(1f).height(52.dp),
                ) { Text(primaryLabel, style = TextStyle(fontFamily = MonoFamily, fontSize = 14.sp)) }
            }
        }
    }
}

private fun step(g: Float) = when { g < 50f -> 5f; g < 300f -> 10f; else -> 25f }

@Composable
private fun Stat(label: String, value: Float, color: Color, unit: String = "") {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("${value.roundToInt()}$unit", color = color, style = MaterialTheme.typography.titleLarge)
        Text(label.uppercase(), color = TextMute, style = mono11)
    }
}

/** Botón que repite mientras se mantiene pulsado. */
@Composable
private fun RepeatButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onStep: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val current by rememberUpdatedState(onStep)
    LaunchedEffect(pressed) {
        if (pressed) { delay(400); while (true) { current(); delay(70) } }
    }
    IconButton(onClick = onStep, interactionSource = interaction, modifier = Modifier.size(48.dp).clip(CircleShape).background(PanelHigh)) {
        Icon(icon, contentDescription = label, tint = Ion)
    }
}
