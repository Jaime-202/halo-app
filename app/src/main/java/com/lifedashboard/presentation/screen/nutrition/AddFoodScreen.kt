package com.lifedashboard.presentation.screen.nutrition

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lifedashboard.data.local.entity.SavedMealEntity
import com.lifedashboard.data.remote.food.FoodCatalogItem
import com.lifedashboard.data.remote.food.FoodSourceCategory
import com.lifedashboard.presentation.theme.*
import kotlin.math.roundToInt

private val mono11 = TextStyle(fontFamily = MonoFamily, fontSize = 11.sp, letterSpacing = 1.2.sp)

private enum class AddTab(val label: String) { PARA_TI("Para ti"), RECIENTES("Recientes"), FAVORITOS("Favoritos"), MIS_COMIDAS("Mis comidas"), SUPER("Súper") }

/**
 * Añadir comida a pantalla completa: buscador con el teclado abierto, pestañas
 * (para ti, recientes, favoritos, mis comidas, supermercados) y una cesta para
 * añadir varios alimentos de una vez.
 */
@Composable
internal fun AddFoodScreen(
    state: NutritionUiState,
    initialMealType: String,
    onQuery: (String) -> Unit,
    onCategory: (FoodSourceCategory) -> Unit,
    onAddBasket: (List<BasketItem>, String) -> Unit,
    onAddSavedMeal: (SavedMealEntity, String) -> Unit,
    onToggleFavorite: (FoodCatalogItem) -> Unit,
    onScan: (String) -> Unit,
    onPhoto: (String) -> Unit,
    onLabel: () -> Unit,
    onDescribe: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var mealType by remember { mutableStateOf(initialMealType) }
    var tab by remember { mutableStateOf(AddTab.PARA_TI) }
    val basket = remember { mutableStateListOf<BasketItem>() }
    var detail by remember { mutableStateOf<FoodCatalogItem?>(null) }
    var showBasket by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val haptics = rememberHaloHaptics()
    LaunchedEffect(Unit) { onQuery(""); try { focus.requestFocus() } catch (_: Exception) { } }

    fun key(f: FoodCatalogItem) = NutritionViewModel.foodKey(f.name, f.brand)
    fun inBasket(f: FoodCatalogItem) = basket.any { key(it.food) == key(f) }
    fun toggle(f: FoodCatalogItem) {
        val i = basket.indexOfFirst { key(it.food) == key(f) }
        haptics.tick()
        if (i >= 0) basket.removeAt(i) else basket.add(BasketItem(f, f.servingSizeGrams.takeIf { it > 0f } ?: 100f))
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Column(Modifier.fillMaxSize().background(Obsidian).systemBarsPadding().imePadding()) {
            // Cabecera: cerrar y momento del día
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, contentDescription = "Cerrar", tint = TextHigh) }
                Text("Añadir a", color = TextMute, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.width(8.dp))
                MealPicker(mealType) { mealType = it }
            }

            // Buscador
            val shape = RoundedCornerShape(50)
            Row(
                Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(shape).background(Panel).border(1.dp, Ion.copy(alpha = 0.5f), shape)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Search, contentDescription = null, tint = Ion)
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f)) {
                    if (state.searchQuery.isEmpty()) Text("Busca un alimento o producto", color = TextDim, style = MaterialTheme.typography.bodyLarge)
                    BasicTextField(
                        value = state.searchQuery,
                        onValueChange = onQuery,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = TextHigh),
                        cursorBrush = SolidColor(Ion),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus).semantics { contentDescription = "Buscar alimento" },
                    )
                }
                if (state.searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onQuery("") }, modifier = Modifier.size(24.dp)) { Icon(Icons.Outlined.Close, contentDescription = "Borrar búsqueda", tint = TextMute) }
                }
            }

            // Accesos: código, foto, etiqueta, describir
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Quick(Icons.Outlined.QrCodeScanner, "Código", Modifier.weight(1f)) { onScan(mealType) }
                Quick(Icons.Outlined.PhotoCamera, "Foto", Modifier.weight(1f)) { onPhoto(mealType) }
                Quick(Icons.Outlined.DocumentScanner, "Etiqueta", Modifier.weight(1f)) { onLabel() }
                Quick(Icons.Outlined.AutoAwesome, "Describir", Modifier.weight(1f)) { onDescribe(mealType) }
            }

            val searching = state.searchQuery.isNotBlank()
            if (!searching) {
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AddTab.entries.forEach { t -> Pill(t.label, t == tab) { tab = t; if (t == AddTab.SUPER) onCategory(state.selectedCategory) } }
                }
            }
            if (searching || tab == AddTab.SUPER) {
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FoodSourceCategory.entries.forEach { c -> Pill(c.label, c == state.selectedCategory, small = true) { onCategory(c) } }
                }
            }

            val list: List<FoodCatalogItem> = when {
                searching || tab == AddTab.SUPER -> state.searchResults
                tab == AddTab.PARA_TI -> state.suggestionsFor(mealType)
                tab == AddTab.RECIENTES -> state.recents
                tab == AddTab.FAVORITOS -> state.favorites
                else -> emptyList()
            }

            Box(Modifier.weight(1f)) {
                LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 100.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!searching && tab == AddTab.MIS_COMIDAS) {
                        if (state.savedMeals.isEmpty()) item { Empty("Aún no tienes comidas guardadas. Desde el menú de Comida puedes guardar tu desayuno habitual.") }
                        items(state.savedMeals, key = { "saved_${it.id}" }) { saved ->
                            SavedRow(saved) { onAddSavedMeal(saved, mealType) }
                        }
                    } else {
                        if (searching && state.isSearching) item { LinearProgressIndicator(color = Ion, trackColor = Line, modifier = Modifier.fillMaxWidth()) }
                        if (searching && state.isSearching && list.isEmpty()) items(5) {
                            com.lifedashboard.presentation.common.components.SkeletonBlock(64.dp, radius = 18.dp)
                        }
                        if (!state.isSearching && list.isEmpty() && !(tab == AddTab.PARA_TI && !searching && (state.favorites.isNotEmpty() || state.recents.isNotEmpty()))) item {
                            Empty(
                                when {
                                    searching -> "Sin resultados. Prueba con otra palabra o escanea el código de barras."
                                    tab == AddTab.FAVORITOS -> "Marca alimentos con la estrella y aparecerán aquí."
                                    else -> "Cuando apuntes comidas, aquí verás lo que sueles comer."
                                }
                            )
                        }
                        if (!searching && tab == AddTab.PARA_TI && list.isNotEmpty()) item {
                            Text("LO QUE SUELES COMER EN ${MealSectionType.labelFor(mealType).uppercase()}", color = TextMute, style = mono11, modifier = Modifier.padding(top = 4.dp))
                        }
                        items(list, key = { "r_" + key(it) + it.id }) { food ->
                            ResultRow(food, inBasket(food), onAdd = { toggle(food) }, onOpen = { detail = food })
                        }
                        // En "Para ti", debajo: favoritos y recientes de otros momentos
                        if (!searching && tab == AddTab.PARA_TI) {
                            val shown = list.map { key(it) }.toSet()
                            val more = (state.favorites + state.recents).distinctBy { key(it) }.filter { key(it) !in shown }.take(12)
                            if (more.isNotEmpty()) {
                                item { Text("FAVORITOS Y RECIENTES", color = TextMute, style = mono11, modifier = Modifier.padding(top = 10.dp)) }
                                items(more, key = { "m_" + key(it) }) { food ->
                                    ResultRow(food, inBasket(food), onAdd = { toggle(food) }, onOpen = { detail = food })
                                }
                            }
                        }
                    }
                }

                // Cesta
                if (basket.isNotEmpty()) {
                    Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(12.dp)) {
                        val s = RoundedCornerShape(22.dp)
                        Column(Modifier.fillMaxWidth().clip(s).background(PanelHigh).border(1.dp, Ion, s).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            AnimatedVisibility(showBasket) {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    basket.forEach { b ->
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(b.food.name, color = TextHigh, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                            Text("${b.grams.roundToInt()} g · ${b.kcal.roundToInt()}", color = TextMute, style = MaterialTheme.typography.labelSmall)
                                            IconButton(onClick = { basket.remove(b) }, modifier = Modifier.size(28.dp)) { Icon(Icons.Outlined.Close, contentDescription = "Quitar ${b.food.name}", tint = TextMute, modifier = Modifier.size(16.dp)) }
                                        }
                                    }
                                    HorizontalDivider(color = Line)
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f).clickable { showBasket = !showBasket }) {
                                    Text("${basket.size} en la cesta", color = TextHigh, style = MaterialTheme.typography.titleSmall)
                                    Text("${basket.sumOf { it.kcal.toDouble() }.roundToInt()} kcal · ${if (showBasket) "ocultar" else "ver"}", color = TextMute, style = MaterialTheme.typography.labelSmall)
                                }
                                Button(
                                    onClick = { haptics.confirm(); onAddBasket(basket.toList(), mealType); onDismiss() },
                                    colors = ButtonDefaults.buttonColors(containerColor = Ion, contentColor = OnAccent), shape = RoundedCornerShape(14.dp),
                                ) { Text("Añadir") }
                            }
                        }
                    }
                }
            }
        }
    }

    detail?.let { food ->
        val existing = basket.firstOrNull { key(it.food) == key(food) }
        FoodDetailSheet(
            food = food,
            initialGrams = existing?.grams ?: food.servingSizeGrams.takeIf { it > 0f } ?: 100f,
            remainingKcal = state.targetCalories - state.totalCalories - basket.sumOf { it.kcal.toDouble() }.toFloat() + (existing?.kcal ?: 0f),
            remainingProtein = state.targetProtein - state.totalProtein,
            isFavorite = key(food) in state.favoriteKeys,
            primaryLabel = if (existing != null) "Actualizar en la cesta" else "Añadir a la cesta",
            onPrimary = { g ->
                val i = basket.indexOfFirst { key(it.food) == key(food) }
                if (i >= 0) basket[i] = BasketItem(food, g) else basket.add(BasketItem(food, g))
                detail = null
            },
            onToggleFavorite = { onToggleFavorite(food) },
            onDismiss = { detail = null },
        )
    }
}

@Composable
internal fun MealPicker(selected: String, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.clip(RoundedCornerShape(50)).background(PanelHigh).clickable { open = true }.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(mealIcon(selected), contentDescription = null, tint = Ion, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(MealSectionType.labelFor(selected), color = TextHigh, style = MaterialTheme.typography.titleSmall)
            Icon(Icons.Outlined.ExpandMore, contentDescription = "Cambiar momento", tint = TextMute, modifier = Modifier.size(18.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = Panel) {
            MealSectionType.entries.forEach { m ->
                DropdownMenuItem(
                    text = { Text(m.title, color = TextHigh) },
                    leadingIcon = { Icon(mealIcon(m.key), contentDescription = null, tint = Ion) },
                    onClick = { onSelect(m.key); open = false },
                )
            }
        }
    }
}

@Composable
private fun Quick(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    val s = RoundedCornerShape(16.dp)
    Column(
        modifier.clip(s).background(Panel).clickable(onClick = onClick).padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(icon, contentDescription = null, tint = Ion, modifier = Modifier.size(20.dp))
        Text(label, color = TextHigh, style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}

@Composable
private fun Pill(label: String, on: Boolean, small: Boolean = false, onClick: () -> Unit) {
    val s = RoundedCornerShape(50)
    Box(
        Modifier.clip(s).background(if (on && !small) Ion else Color.Transparent).border(1.dp, if (on) Ion else LineStrong, s)
            .clickable(onClick = onClick).padding(horizontal = if (small) 10.dp else 14.dp, vertical = if (small) 5.dp else 7.dp)
    ) {
        Text(label, color = if (on && !small) OnAccent else if (on) Ion else TextMute, style = if (small) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

@Composable
private fun ResultRow(food: FoodCatalogItem, added: Boolean, onAdd: () -> Unit, onOpen: () -> Unit) {
    val s = RoundedCornerShape(18.dp)
    Row(
        Modifier.fillMaxWidth().clip(s).background(Panel).border(1.dp, if (added) Ion else Color.Transparent, s).clickable(onClick = onOpen).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FoodThumbnail(name = food.name, brand = food.brand, imageUrl = food.imageUrl, size = 48.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(food.name, color = TextHigh, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val serving = food.servingSizeGrams.takeIf { it > 0f } ?: 100f
            Text(
                listOfNotNull(food.brand.takeIf { it.isNotBlank() && it != "Básico" }, "${serving.roundToInt()} g", "${(food.caloriesPer100g * serving / 100f).roundToInt()} kcal").joinToString(" · "),
                color = TextMute, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        NutriScoreBadge(food.nutriScore, Modifier.padding(horizontal = 6.dp))
        Box(
            Modifier.size(36.dp).clip(CircleShape).background(if (added) Ion else PanelHigh).clickable(onClick = onAdd)
                .semantics { contentDescription = if (added) "Quitar ${food.name} de la cesta" else "Añadir ${food.name} a la cesta" },
            contentAlignment = Alignment.Center,
        ) {
            Icon(if (added) Icons.Outlined.Check else Icons.Outlined.Add, contentDescription = null, tint = if (added) OnAccent else Ion)
        }
    }
}

@Composable
private fun SavedRow(saved: SavedMealEntity, onAdd: () -> Unit) {
    val s = RoundedCornerShape(18.dp)
    Row(
        Modifier.fillMaxWidth().clip(s).background(Panel).clickable(onClick = onAdd).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Bookmarks, contentDescription = null, tint = Ion)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(saved.name, color = TextHigh, style = MaterialTheme.typography.bodyMedium)
            Text("${saved.totalCalories.roundToInt()} kcal · toca para añadir", color = TextMute, style = MaterialTheme.typography.labelSmall)
        }
        Icon(Icons.Outlined.Add, contentDescription = null, tint = Ion)
    }
}

@Composable
private fun Empty(text: String) {
    Text(text, color = TextMute, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 24.dp, horizontal = 8.dp))
}
