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

// Tarjetas de la pantalla Comida: cabecera, resumen, macros, coach y miniatura de alimento.

@Composable
internal fun NutritionHeader(
    selectedDate: LocalDate,
    hasGeminiApiKey: Boolean,
    onPreviousDay: () -> Unit,
    onNextDay: () -> Unit,
    onTodayClick: () -> Unit,
    onApiKeyClick: () -> Unit
) {
    val today = LocalDate.now()
    val isToday = selectedDate == today

    val dayPillText = when {
        selectedDate == today -> "Hoy"
        selectedDate == today.minusDays(1) -> "Ayer"
        selectedDate == today.plusDays(1) -> "Mañana"
        else -> selectedDate.format(DateTimeFormatter.ofPattern("d MMM", Locale("es"))).replaceFirstChar { it.uppercase() }
    }

    val fullDateStr = selectedDate.format(
        DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", Locale("es"))
    ).replaceFirstChar { it.uppercase() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 36.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier              = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text       = "Comida & Nutrición",
                    style      = MaterialTheme.typography.headlineSmall,
                    color      = White,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text  = fullDateStr,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Gray400
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (!isToday) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Violet400.copy(alpha = 0.2f))
                            .clickable(onClick = onTodayClick)
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Text("↻ Hoy", style = MaterialTheme.typography.labelSmall, color = Violet300, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(2.dp))
                }

                IconButton(
                    onClick  = onPreviousDay,
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Indigo800)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Día anterior", tint = White, modifier = Modifier.size(16.dp))
                }

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Indigo800)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = dayPillText,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isToday) Cyan400 else White,
                        fontWeight = FontWeight.Bold
                    )
                }

                IconButton(
                    onClick  = onNextDay,
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Indigo800)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Día siguiente", tint = White, modifier = Modifier.size(16.dp))
                }
            }
        }

        // Píldora interactiva de Estado de la Inteligencia Artificial (Google Gemini)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(if (hasGeminiApiKey) Violet400.copy(alpha = 0.12f) else Indigo800.copy(alpha = 0.7f))
                .border(
                    1.dp,
                    if (hasGeminiApiKey) Violet400.copy(alpha = 0.35f) else Amber400.copy(alpha = 0.35f),
                    RoundedCornerShape(12.dp)
                )
                .clickable(onClick = onApiKeyClick)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(
                        imageVector = if (hasGeminiApiKey) Icons.Default.AutoAwesome else Icons.Default.Key,
                        contentDescription = null,
                        tint = if (hasGeminiApiKey) Violet300 else Amber400,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (hasGeminiApiKey) "Gemini 3.8 Flash · Activo" else "Modo Local · Activar Gemini 3.8 IA",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (hasGeminiApiKey) Violet200 else Amber400,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (hasGeminiApiKey) "Configurar" else "Activar",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (hasGeminiApiKey) Violet300 else Amber400,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
            }
        }
    }
}

// ─── Hero Card de Macros y Balance Calórico Rediseñado ────────────────────────

@Composable
internal fun NutritionHeroCard(
    state: NutritionUiState,
    onOpenCustomTargets: () -> Unit,
    onOpenCalculator: () -> Unit
) {
    val remaining = state.remainingCalories.toInt()
    val animatedCaloriesProgress by animateFloatAsState(
        targetValue = state.caloriesProgress,
        animationSpec = tween(900, easing = FastOutSlowInEasing),
        label = "calories_progress"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        Indigo800,
                        PanelHigh,
                        Indigo900
                    )
                )
            )
            .border(1.dp, Violet400.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // ─── Fila 1: Título "Balance Calórico" y Botones Compactos de Metas ───
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier              = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.LocalFireDepartment,
                        contentDescription = null,
                        tint = Amber400,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Balance Calórico",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = White,
                        fontSize = 15.sp
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Violet400.copy(alpha = 0.18f))
                            .border(1.dp, Violet400.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                            .clickable(onClick = onOpenCustomTargets)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Tune, contentDescription = null, tint = Violet300, modifier = Modifier.size(11.dp))
                            Spacer(Modifier.width(3.dp))
                            Text("Metas", style = MaterialTheme.typography.labelSmall, color = Violet300, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Indigo700)
                            .clickable(onClick = onOpenCalculator),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Calculate, contentDescription = "Calcular según perfil", tint = Gray300, modifier = Modifier.size(13.dp))
                    }
                }
            }

            // ─── Fila 2: Resumen Numérico (Restantes + Consumidas vs Meta) ───
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = if (remaining >= 0) "$remaining" else "+${-remaining}",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                        // Registro sin juicio: pasarse no se pinta en rojo
                        color = if (remaining >= 0) Ion else TextHigh
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = if (remaining >= 0) "kcal restantes" else "kcal por encima del objetivo",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextMute,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }

                Text(
                    text = "${state.totalCalories.toInt()} de ${state.targetCalories.toInt()} kcal",
                    style = MaterialTheme.typography.labelSmall,
                    color = Gray300,
                    fontWeight = FontWeight.Medium,
                    fontSize = 11.sp
                )
            }

            // ─── Fila 3: Barra Fina de Progreso Calórico ───
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(2.5.dp))
                    .background(Indigo700)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedCaloriesProgress)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(2.5.dp))
                        .background(Brush.horizontalGradient(listOf(Violet400, Cyan400)))
                )
            }

            if (state.activeBurnedCalories > 0f) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Text(
                        text = "🔥 ${state.activeBurnedCalories.toInt()} kcal quemadas en entreno/pasos",
                        style = MaterialTheme.typography.labelSmall,
                        color = Cyan400,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // ─── Fila 4: 3 Mini Tarjetas Compactas de Macronutrientes ───
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                CompactMacroCard(
                    title = "Proteína",
                    emoji = "🥩",
                    currentVal = state.totalProtein,
                    targetVal = state.targetProtein,
                    progress = state.proteinProgress,
                    color = Violet400,
                    modifier = Modifier.weight(1f)
                )
                CompactMacroCard(
                    title = "Carbos",
                    emoji = "🍞",
                    currentVal = state.totalCarbs,
                    targetVal = state.targetCarbs,
                    progress = state.carbsProgress,
                    color = Cyan400,
                    modifier = Modifier.weight(1f)
                )
                CompactMacroCard(
                    title = "Grasas",
                    emoji = "🥑",
                    currentVal = state.totalFat,
                    targetVal = state.targetFat,
                    progress = state.fatProgress,
                    color = Amber400,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
internal fun CompactMacroCard(
    title: String,
    emoji: String,
    currentVal: Float,
    targetVal: Float,
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(900, easing = FastOutSlowInEasing),
        label = "macro_progress"
    )
    val isComplete = currentVal >= targetVal && targetVal > 0f
    val remainingGrams = (targetVal - currentVal).coerceAtLeast(0f).toInt()

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Indigo900.copy(alpha = 0.65f))
            .border(1.dp, color.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
            .padding(horizontal = 8.dp, vertical = 7.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            // Fila 1: Emoji + Nombre de Macro
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = emoji, fontSize = 11.sp)
                Spacer(Modifier.width(3.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = color,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    maxLines = 1
                )
            }

            // Fila 2: Gramos actuales / meta
            Row(
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "${currentVal.toInt()}",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = White
                )
                Text(
                    text = "/${targetVal.toInt()}g",
                    fontSize = 10.sp,
                    color = Gray400,
                    modifier = Modifier.padding(bottom = 1.dp)
                )
            }

            // Fila 3: Barra fina de progreso
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.5.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Indigo700)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedProgress)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(2.dp))
                        .background(color)
                )
            }

            // Fila 4: % y restantes
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "${(progress * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isComplete) Emerald400 else color,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (isComplete) "✓ Listo" else "${remainingGrams}g rest.",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isComplete) Emerald400 else Gray400,
                    fontSize = 8.5.sp,
                    maxLines = 1
                )
            }
        }
    }
}

// ─── Tarjeta de Sección de Comida Rediseñada ──────────────────────────────────

@Composable
fun FoodThumbnail(
    name: String,
    brand: String? = null,
    imageUrl: String? = null,
    size: androidx.compose.ui.unit.Dp = 46.dp,
    modifier: Modifier = Modifier
) {
    val resolvedUrl = remember(name, brand, imageUrl) {
        RealFoodImageResolver.resolve(name, brand, imageUrl)
    }
    val emoji = remember(name) {
        RealFoodImageResolver.getAccurateEmoji(name)
    }
    val isPackaging = remember(resolvedUrl) {
        resolvedUrl?.contains("prod-mercadona.imgix.net") == true ||
        resolvedUrl?.contains("openfoodfacts") == true
    }

    var imageLoaded by remember(resolvedUrl) { mutableStateOf(false) }
    var imageFailed by remember(resolvedUrl) { mutableStateOf(false) }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (imageLoaded && isPackaging) Color.White
                else Indigo700.copy(alpha = 0.6f)
            )
            .border(
                1.dp,
                if (imageLoaded && isPackaging) Color(0xFFE2E8F0) else Indigo600.copy(alpha = 0.5f),
                RoundedCornerShape(12.dp)
            )
    ) {
        // Fallback emoji solo si no hay URL o si la carga falló
        if (resolvedUrl.isNullOrBlank() || imageFailed || !imageLoaded) {
            Text(text = emoji, fontSize = (size.value * 0.45f).sp)
        }

        if (!resolvedUrl.isNullOrBlank() && !imageFailed) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(resolvedUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = name,
                onSuccess = { imageLoaded = true },
                onError = { imageFailed = true },
                contentScale = if (isPackaging) ContentScale.Fit else ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(if (isPackaging) 3.dp else 0.dp)
                    .clip(RoundedCornerShape(10.dp))
            )
        }
    }
}

// ─── Componente: Coach Nutricional IA ──────────────────────────────────────────

@Composable
internal fun NutritionCoachCard(
    advice: String?,
    isLoading: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Indigo800,
                        PanelHigh
                    )
                )
            )
            .border(1.dp, Violet400.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
            .padding(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(Violet400.copy(alpha = 0.2f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = Violet300,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f, fill = false)) {
                        Text(
                            text = "Coach Nutricional IA",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = White,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "¿Necesitas comer más o menos? ¿Qué comer?",
                            style = MaterialTheme.typography.labelSmall,
                            color = Gray400,
                            fontSize = 10.5.sp
                        )
                    }
                }

                IconButton(
                    onClick = onRefresh,
                    enabled = !isLoading,
                    modifier = Modifier.size(30.dp)
                ) {
                    val rotation by animateFloatAsState(
                        targetValue = if (isLoading) 360f else 0f,
                        animationSpec = if (isLoading)
                            infiniteRepeatable(tween(1000), RepeatMode.Restart)
                        else
                            snap(),
                        label = "coach_refresh_anim"
                    )
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Actualizar recomendación",
                        tint = if (isLoading) Violet300 else Gray400,
                        modifier = Modifier.size(16.dp).rotate(rotation)
                    )
                }
            }

            AnimatedContent(
                targetState = isLoading to advice,
                transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(200)) },
                label = "coach_advice_anim"
            ) { (loading, text) ->
                if (loading) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Violet400, strokeWidth = 2.dp)
                        Text(
                            text = "Calculando tu balance calórico y recomendación...",
                            style = MaterialTheme.typography.bodySmall,
                            color = Violet300,
                            fontSize = 12.sp
                        )
                    }
                } else if (!text.isNullOrBlank()) {
                    Column(modifier = Modifier.clickable { isExpanded = !isExpanded }) {
                        Text(
                            text = text,
                            style = MaterialTheme.typography.bodySmall,
                            color = Gray100,
                            fontSize = 12.5.sp,
                            lineHeight = 18.sp,
                            maxLines = if (isExpanded) Int.MAX_VALUE else 3,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (text.length > 120) {
                            Text(
                                text = if (isExpanded) "Mostrar menos ▲" else "Leer más ▼",
                                style = MaterialTheme.typography.labelSmall,
                                color = Violet300,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                    ) {
                        Text(
                            text = "Toca para analizar tu ingesta y recibir consejo",
                            style = MaterialTheme.typography.bodySmall,
                            color = Gray400,
                            fontSize = 11.5.sp,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = onRefresh,
                            colors = ButtonDefaults.buttonColors(containerColor = Violet400),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text("Consultar IA", color = Indigo900, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

// ─── Diálogo de Registro de Comida con IA mediante Texto ───────────────────────
