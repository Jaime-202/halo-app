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

// Registro con Gemini: foto, texto y clave de API.

@Composable
internal fun PhotoAiDialog(
    bitmap: Bitmap,
    description: String,
    mealType: String,
    isAnalyzing: Boolean,
    detectedItems: List<DetectedFoodItem>?,
    errorMessage: String?,
    explanation: String?,
    hasGeminiApiKey: Boolean,
    onOpenApiKeyDialog: () -> Unit,
    onDescriptionChange: (String) -> Unit,
    onAnalyze: () -> Unit,
    onItemChange: (Int, DetectedFoodItem) -> Unit,
    onItemDelete: (Int) -> Unit,
    onConfirm: (List<DetectedFoodItem>, String) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Indigo800)
                .border(1.dp, Violet400.copy(alpha = 0.4f), RoundedCornerShape(24.dp))
                .padding(20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Título y botón cerrar
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier              = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Violet300, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text       = "Análisis con IA Gemini",
                            style      = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color      = White
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Gray400, modifier = Modifier.size(18.dp))
                    }
                }

                // Banner de estado de Gemini
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (hasGeminiApiKey) Violet400.copy(alpha = 0.15f) else Amber400.copy(alpha = 0.15f))
                        .border(1.dp, if (hasGeminiApiKey) Violet400.copy(alpha = 0.35f) else Amber400.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                        .clickable(onClick = onOpenApiKeyDialog)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = if (hasGeminiApiKey) Violet300 else Amber400, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = if (hasGeminiApiKey) "Gemini 3.8 Flash Vision Activo" else "Modo Básico · Toca para vincular tu Gemini API Key gratis",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (hasGeminiApiKey) Violet200 else Amber400,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Vista previa de la foto tomada
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Foto de comida",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(16.dp))
                )

                // Campo de descripción opcional
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Descripción opcional (para que la IA acierte mejor):",
                        style = MaterialTheme.typography.labelSmall,
                        color = Gray300
                    )
                    OutlinedTextField(
                        value = description,
                        onValueChange = onDescriptionChange,
                        placeholder = {
                            Text("Ej: Pollo con arroz y 2 cucharadas de aceite de oliva...", color = Gray500, fontSize = 12.sp)
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Violet400,
                            unfocusedBorderColor = Indigo600,
                            focusedTextColor = White,
                            unfocusedTextColor = White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        maxLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Botón de análisis si aún no se ha analizado
                if (detectedItems == null && !isAnalyzing) {
                    Button(
                        onClick = onAnalyze,
                        colors = ButtonDefaults.buttonColors(containerColor = Violet400),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Indigo900)
                        Spacer(Modifier.width(8.dp))
                        Text("Analizar Plato Ahora", color = Indigo900, fontWeight = FontWeight.Bold)
                    }
                }

                // Indicador de carga
                if (isAnalyzing) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
                    ) {
                        CircularProgressIndicator(color = Violet400, strokeWidth = 3.dp)
                        Text(
                            text = "Gemini Vision está identificando los alimentos y macros...",
                            style = MaterialTheme.typography.bodySmall,
                            color = Violet300,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // Mensaje de error
                if (errorMessage != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Rose400.copy(alpha = 0.2f))
                            .padding(12.dp)
                    ) {
                        Text(text = errorMessage, color = Rose400, style = MaterialTheme.typography.bodySmall)
                    }
                }

                // Lista de alimentos detectados
                if (detectedItems != null) {
                    if (explanation != null) {
                        Text(
                            text = explanation,
                            style = MaterialTheme.typography.bodySmall,
                            color = Gray300
                        )
                    }

                    Text(
                        text = "Alimentos detectados (ajusta gramos si es necesario):",
                        style = MaterialTheme.typography.labelMedium,
                        color = White,
                        fontWeight = FontWeight.Bold
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        detectedItems.forEachIndexed { idx, item ->
                            DetectedItemEditor(
                                item = item,
                                onChange = { updated -> onItemChange(idx, updated) },
                                onDelete = { onItemDelete(idx) }
                            )
                        }
                    }

                    // Resumen calórico detectado
                    val totalDetectedKcal = detectedItems.sumOf { it.calories.toDouble() }.toInt()
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                    ) {
                        Text("Total estimado:", style = MaterialTheme.typography.bodyMedium, color = Gray300)
                        Text("$totalDetectedKcal kcal", style = MaterialTheme.typography.titleMedium, color = Amber400, fontWeight = FontWeight.Bold)
                    }

                    // Botón de confirmación
                    Button(
                        onClick = { onConfirm(detectedItems, mealType) },
                        colors = ButtonDefaults.buttonColors(containerColor = Emerald400),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = Indigo900)
                        Spacer(Modifier.width(8.dp))
                        Text("Revisar en la cesta", color = Indigo900, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
internal fun DetectedItemEditor(
    item: DetectedFoodItem,
    onChange: (DetectedFoodItem) -> Unit,
    onDelete: () -> Unit
) {
    var gramsText by remember(item.grams) { mutableStateOf("${item.grams.toInt()}") }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Indigo900)
            .border(1.dp, Indigo700, RoundedCornerShape(14.dp))
            .padding(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = White,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(3.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (!item.isGeneric && item.brand.isNotBlank() && !item.brand.equals("genérico", ignoreCase = true) && !item.brand.equals("casero", ignoreCase = true)) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Violet400.copy(alpha = 0.22f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = item.brand,
                                style = MaterialTheme.typography.labelSmall,
                                color = Violet300,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                    Text("${item.calories.toInt()} kcal", style = MaterialTheme.typography.labelSmall, color = Amber400)
                    Text("·", color = Gray500)
                    Text("P:${item.protein.toInt()}g C:${item.carbs.toInt()}g G:${item.fat.toInt()}g", style = MaterialTheme.typography.labelSmall, color = Gray400)
                }
            }

            Spacer(Modifier.width(8.dp))

            // Campo de gramos
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = gramsText,
                    onValueChange = { newText ->
                        gramsText = newText
                        val newGrams = newText.toFloatOrNull() ?: item.grams
                        if (newGrams > 0 && item.grams > 0) {
                            val ratio = newGrams / item.grams
                            onChange(
                                item.copy(
                                    grams = newGrams,
                                    calories = item.calories * ratio,
                                    protein = item.protein * ratio,
                                    carbs = item.carbs * ratio,
                                    fat = item.fat * ratio
                                )
                            )
                        }
                    },
                    textStyle = MaterialTheme.typography.labelMedium.copy(color = White, textAlign = TextAlign.Center),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Violet400,
                        unfocusedBorderColor = Indigo600,
                        focusedTextColor = White,
                        unfocusedTextColor = White
                    ),
                    modifier = Modifier.width(65.dp).height(42.dp),
                    singleLine = true
                )
                Text(" g", style = MaterialTheme.typography.labelSmall, color = Gray400)
                Spacer(Modifier.width(4.dp))
                IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Eliminar", tint = Gray500, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

// ─── Diálogo de Búsqueda de Alimentos, Supermercados y Escáner de Códigos ─────

@Composable
internal fun TextAiMealDialog(
    mealType: String,
    inputText: String,
    isAnalyzing: Boolean,
    detectedItems: List<DetectedFoodItem>?,
    errorMessage: String?,
    explanation: String?,
    hasGeminiApiKey: Boolean,
    onOpenApiKeyDialog: () -> Unit,
    onTakePhoto: () -> Unit,
    onPickGallery: () -> Unit,
    onInputChange: (String) -> Unit,
    onAnalyze: () -> Unit,
    onItemChange: (Int, DetectedFoodItem) -> Unit,
    onItemDelete: (Int) -> Unit,
    onConfirm: (List<DetectedFoodItem>, String) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Indigo800)
                .border(1.dp, Violet400.copy(alpha = 0.4f), RoundedCornerShape(24.dp))
                .padding(20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Cabecera
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Violet300, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Registrar con IA Texto",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = White
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Gray400, modifier = Modifier.size(18.dp))
                    }
                }

                // Banner de estado de Gemini
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (hasGeminiApiKey) Violet400.copy(alpha = 0.15f) else Amber400.copy(alpha = 0.15f))
                        .border(1.dp, if (hasGeminiApiKey) Violet400.copy(alpha = 0.35f) else Amber400.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                        .clickable(onClick = onOpenApiKeyDialog)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = if (hasGeminiApiKey) Violet300 else Amber400, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = if (hasGeminiApiKey) "Gemini 3.8 Flash Conectado · IA Activa" else "Motor Local Básico · Toca para vincular tu Gemini API Key gratis",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (hasGeminiApiKey) Violet200 else Amber400,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Violet400.copy(alpha = 0.15f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "Destino: $mealType",
                            style = MaterialTheme.typography.labelSmall,
                            color = Violet300,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(
                            onClick = onTakePhoto,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Gray300),
                            border = BorderStroke(1.dp, Indigo600),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(13.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Foto", style = MaterialTheme.typography.labelSmall)
                        }

                        OutlinedButton(
                            onClick = onPickGallery,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Gray300),
                            border = BorderStroke(1.dp, Indigo600),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(13.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Galería", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                // Campo de entrada de texto
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Describe lo que has comido en lenguaje natural:",
                        style = MaterialTheme.typography.labelSmall,
                        color = Gray300
                    )
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = onInputChange,
                        placeholder = {
                            Text(
                                "Ej: 2 tostadas de pan integral con aceite de oliva, 50g de jamón serrano y un café con leche",
                                color = Gray500,
                                fontSize = 12.sp
                            )
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Violet400,
                            unfocusedBorderColor = Indigo600,
                            focusedTextColor = White,
                            unfocusedTextColor = White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        minLines = 3,
                        maxLines = 5,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Botón de análisis
                if (detectedItems == null && !isAnalyzing) {
                    Button(
                        onClick = onAnalyze,
                        enabled = inputText.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = Violet400),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Indigo900)
                        Spacer(Modifier.width(8.dp))
                        Text("Calcular Alimentos y Macros", color = Indigo900, fontWeight = FontWeight.Bold)
                    }
                }

                // Indicador de análisis en curso
                if (isAnalyzing) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
                    ) {
                        CircularProgressIndicator(color = Violet400, strokeWidth = 3.dp)
                        Text(
                            text = if (hasGeminiApiKey) "Gemini 3.8 Flash está calculando alimentos, porciones y calorías..." else "Analizando alimentos y macros con el motor local...",
                            style = MaterialTheme.typography.bodySmall,
                            color = Violet300,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // Mensaje de error
                if (errorMessage != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Rose400.copy(alpha = 0.2f))
                            .padding(12.dp)
                    ) {
                        Text(text = errorMessage, color = Rose400, style = MaterialTheme.typography.bodySmall)
                    }
                }

                // Lista de alimentos detectados
                if (detectedItems != null) {
                    if (explanation != null) {
                        Text(
                            text = explanation,
                            style = MaterialTheme.typography.bodySmall,
                            color = Gray300
                        )
                    }

                    Text(
                        text = "Alimentos calculados (ajusta gramos si es necesario):",
                        style = MaterialTheme.typography.labelMedium,
                        color = White,
                        fontWeight = FontWeight.Bold
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        detectedItems.forEachIndexed { idx, item ->
                            DetectedItemEditor(
                                item = item,
                                onChange = { updated -> onItemChange(idx, updated) },
                                onDelete = { onItemDelete(idx) }
                            )
                        }
                    }

                    // Resumen calórico
                    val totalDetectedKcal = detectedItems.sumOf { it.calories.toDouble() }.toInt()
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                    ) {
                        Text("Total estimado:", style = MaterialTheme.typography.bodyMedium, color = Gray300)
                        Text("$totalDetectedKcal kcal", style = MaterialTheme.typography.titleMedium, color = Amber400, fontWeight = FontWeight.Bold)
                    }

                    // Botones de acción
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onAnalyze,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Violet300),
                            border = BorderStroke(1.dp, Violet400.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Recalcular", maxLines = 1)
                        }
                        Button(
                            onClick = { onConfirm(detectedItems, mealType) },
                            colors = ButtonDefaults.buttonColors(containerColor = Emerald400),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(2f)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = Indigo900)
                            Spacer(Modifier.width(6.dp))
                            Text("Revisar en la cesta", color = Indigo900, fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

// ─── Diálogo de Configuración de Clave Gemini desde Nutrición ──────────────────

@Composable
internal fun NutritionApiKeyDialog(
    currentKey: String,
    currentModel: String = "gemini-3.8-flash",
    onDismiss: () -> Unit,
    onTest: (suspend (String, String) -> Result<String>),
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
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Key, contentDescription = null, tint = Violet300, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Google Gemini API Key",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = White
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Gray400, modifier = Modifier.size(18.dp))
                    }
                }

                Text(
                    text = "Consigue una clave gratis en Google AI Studio (aistudio.google.com). Compatible con Gemini 3.8 Flash, con selección de modelos y auto-detección dinámica para modelos futuros.",
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
                    value = keyInput,
                    onValueChange = {
                        keyInput = it
                        testResultText = null
                        testIsSuccess = null
                    },
                    label = { Text("Clave API de Gemini", color = Gray400) },
                    placeholder = { Text("AIzaSy...", color = Gray500) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
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
                            "gemini-3.8-flash" to "3.8 Flash",
                            "gemini-3.8-flash-lite" to "3.8 Lite",
                            "gemini-3.7-flash" to "3.7",
                            "auto" to "Auto"
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
                                text = "Otro",
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

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = {
                            if (keyInput.isBlank()) {
                                Toast.makeText(context, "Escribe o pega una clave primero", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            isTesting = true
                            testResultText = null
                            coroutineScope.launch {
                                val res = onTest(keyInput.trim(), activeModelToUse)
                                isTesting = false
                                if (res.isSuccess) {
                                    testIsSuccess = true
                                    testResultText = res.getOrNull() ?: "Conexión exitosa"
                                } else {
                                    testIsSuccess = false
                                    testResultText = res.exceptionOrNull()?.message ?: "Error al conectar"
                                }
                            }
                        },
                        enabled = !isTesting && keyInput.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = Indigo700, contentColor = White),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        if (isTesting) {
                            CircularProgressIndicator(color = Violet300, strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Comprobando...", style = MaterialTheme.typography.labelSmall)
                        } else {
                            Icon(Icons.Default.CheckCircleOutline, contentDescription = null, modifier = Modifier.size(14.dp), tint = Violet300)
                            Spacer(Modifier.width(6.dp))
                            Text("Probar Clave", style = MaterialTheme.typography.labelSmall)
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

                testResultText?.let { msg ->
                    val isSuccess = testIsSuccess == true
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSuccess) Emerald400.copy(alpha = 0.2f) else Rose400.copy(alpha = 0.2f))
                            .border(1.dp, if (isSuccess) Emerald400.copy(alpha = 0.5f) else Rose400.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                            .padding(8.dp)
                    ) {
                        Text(
                            text = msg,
                            color = if (isSuccess) Emerald400 else Rose400,
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 11.sp
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        border = BorderStroke(1.dp, Indigo600),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancelar", color = Gray400)
                    }

                    Button(
                        onClick = { onSave(keyInput, activeModelToUse) },
                        colors = ButtonDefaults.buttonColors(containerColor = Violet400, contentColor = Indigo900),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Guardar", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}


// ─── Fila de resultado del buscador (catálogo, favoritos y recientes) ────────
