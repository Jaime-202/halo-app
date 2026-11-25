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

@Composable
fun NutritionScreen(
    onBack: () -> Unit = {},
    viewModel: NutritionViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Estado para diálogo de búsqueda y añadido manual
    var showSearchDialog by remember { mutableStateOf(false) }
    var selectedMealTypeForAdd by remember { mutableStateOf("COMIDA") }
    var editingMealItem by remember { mutableStateOf<MealEntryEntity?>(null) }
    var showQuickAddDialog by remember { mutableStateOf(false) }
    var showCustomTargetsDialog by remember { mutableStateOf(false) }
    var showApiKeyModal by remember { mutableStateOf(false) }

    // Biblioteca y etiqueta
    var quickFood by remember { mutableStateOf<FoodCatalogItem?>(null) }
    var showSavedMeals by remember { mutableStateOf(false) }
    var showSaveMeal by remember { mutableStateOf(false) }
    val labelLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        if (bitmap != null) viewModel.readNutritionLabel(bitmap, defaultMealTypeForNow())
    }
    LaunchedEffect(state.libraryMessage) {
        state.libraryMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.consumeLibraryMessage()
        }
    }

    // Launchers para tomar foto con la cámara y seleccionar de la galería
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        if (bitmap != null) {
            viewModel.setStagedPhoto(bitmap, selectedMealTypeForAdd)
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val bitmap = BitmapFactory.decodeStream(stream)
                    if (bitmap != null) {
                        viewModel.setStagedPhoto(bitmap, selectedMealTypeForAdd)
                    }
                }
            } catch (e: Exception) {
                // error abriendo imagen
            }
        }
    }

    // Lanzador directo del escáner de códigos de barras y QR de Google Play Services
    val launchBarcodeScanner: (String) -> Unit = { mealType ->
        selectedMealTypeForAdd = mealType
        val scanner = GmsBarcodeScanning.getClient(context)
        scanner.startScan()
            .addOnSuccessListener { barcode ->
                val code = barcode.rawValue
                if (!code.isNullOrBlank()) {
                    viewModel.onBarcodeDetected(code, mealType)
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(context, "No se pudo escanear: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // Acciones pedidas desde el botón "+" de la barra Órbita
    val pendingAction by com.lifedashboard.presentation.quicklog.QuickActionBus.pending.collectAsStateWithLifecycle()
    LaunchedEffect(pendingAction) {
        val action = com.lifedashboard.presentation.quicklog.QuickActionBus.consume() ?: return@LaunchedEffect
        val type = defaultMealTypeForNow()
        selectedMealTypeForAdd = type
        when (action) {
            com.lifedashboard.presentation.quicklog.NutritionAction.SEARCH -> showSearchDialog = true
            com.lifedashboard.presentation.quicklog.NutritionAction.PHOTO -> cameraLauncher.launch(null)
            com.lifedashboard.presentation.quicklog.NutritionAction.BARCODE -> launchBarcodeScanner(type)
            com.lifedashboard.presentation.quicklog.NutritionAction.LABEL -> labelLauncher.launch(null)
            com.lifedashboard.presentation.quicklog.NutritionAction.TEXT -> viewModel.openTextAiDialog(type)
        }
    }

    // Diálogo de foto y descripción con IA
    if (state.stagedBitmap != null) {
        PhotoAiDialog(
            bitmap = state.stagedBitmap!!,
            description = state.stagedDescription,
            mealType = state.activeMealType,
            isAnalyzing = state.isAiAnalyzing,
            detectedItems = state.aiResultItems,
            errorMessage = state.aiErrorMessage,
            explanation = state.aiExplanation,
            hasGeminiApiKey = state.hasGeminiApiKey,
            onOpenApiKeyDialog = { showApiKeyModal = true },
            onDescriptionChange = { viewModel.onStagedDescriptionChange(it) },
            onAnalyze = { viewModel.analyzeStagedPhoto() },
            onItemChange = { idx, item -> viewModel.updateAiResultItem(idx, item) },
            onItemDelete = { idx -> viewModel.removeAiResultItem(idx) },
            onConfirm = { items, type -> viewModel.confirmAiItems(items, type) },
            onDismiss = { viewModel.clearStagedPhoto() }
        )
    }

    // Diálogo de registro de comida con IA mediante Texto
    if (state.showTextAiDialog) {
        TextAiMealDialog(
            mealType = state.textAiMealType,
            inputText = state.textAiInput,
            isAnalyzing = state.isTextAiAnalyzing,
            detectedItems = state.textAiResultItems,
            errorMessage = state.textAiErrorMessage,
            explanation = state.textAiExplanation,
            hasGeminiApiKey = state.hasGeminiApiKey,
            onOpenApiKeyDialog = { showApiKeyModal = true },
            onTakePhoto = {
                viewModel.closeTextAiDialog()
                selectedMealTypeForAdd = state.textAiMealType
                cameraLauncher.launch(null)
            },
            onPickGallery = {
                viewModel.closeTextAiDialog()
                selectedMealTypeForAdd = state.textAiMealType
                galleryLauncher.launch("image/*")
            },
            onInputChange = { viewModel.onTextAiInputChange(it) },
            onAnalyze = { viewModel.analyzeTextAiMeal() },
            onItemChange = { idx, item -> viewModel.updateTextAiResultItem(idx, item) },
            onItemDelete = { idx -> viewModel.removeTextAiResultItem(idx) },
            onConfirm = { items, type -> viewModel.confirmTextAiItems(items, type) },
            onDismiss = { viewModel.closeTextAiDialog() }
        )
    }

    // Diálogo de confirmación de alimento detectado por QR o Código de Barras (1-tap add)
    state.scannedFoodItem?.let { food ->
        ScannedFoodDialog(
            food = food,
            initialMealType = state.activeMealType,
            onConfirm = { item, grams, mealType ->
                viewModel.addFoodFromCatalog(item, grams, mealType)
                viewModel.dismissScannedItem()
                Toast.makeText(context, "Añadido: ${item.name} (${grams.toInt()}g)", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { viewModel.dismissScannedItem() }
        )
    }

    // Diálogo para código escaneado desconocido (opción de buscar con IA o añadir manual)
    state.scannedUnknownBarcode?.let { rawCode ->
        UnknownBarcodeDialog(
            barcode = rawCode,
            onSearchAi = {
                viewModel.openTextAiDialog(selectedMealTypeForAdd)
                viewModel.onTextAiInputChange("Producto con código de barras $rawCode")
                viewModel.dismissScannedItem()
            },
            onQuickAdd = {
                showQuickAddDialog = true
                viewModel.dismissScannedItem()
            },
            onDismiss = { viewModel.dismissScannedItem() }
        )
    }

    quickFood?.let { food ->
        QuickFoodDialog(
            food = food,
            onAdd = { grams, type ->
                viewModel.addFoodFromCatalog(food, grams, type)
                quickFood = null
            },
            onDismiss = { quickFood = null }
        )
    }
    if (showSavedMeals) {
        SavedMealsDialog(
            savedMeals = state.savedMeals,
            onAdd = { saved, type ->
                viewModel.addSavedMeal(saved, type)
                showSavedMeals = false
            },
            onDelete = { viewModel.deleteSavedMeal(it) },
            onDismiss = { showSavedMeals = false }
        )
    }
    if (showSaveMeal) {
        SaveMealDialog(
            state = state,
            onSave = { type, name ->
                viewModel.saveMealGroup(type, name)
                showSaveMeal = false
            },
            onDismiss = { showSaveMeal = false }
        )
    }
    if (state.isReadingLabel) {
        AlertDialog(
            onDismissRequest = {},
            containerColor = Panel,
            title = { Text("Leyendo la etiqueta…", color = TextHigh) },
            text = { LinearProgressIndicator(color = Ion, trackColor = Line, modifier = Modifier.fillMaxWidth()) },
            confirmButton = {}
        )
    }
    state.labelValues?.let { values ->
        LabelReviewDialog(
            values = values,
            onAdd = { item, grams, fav -> viewModel.addFromLabel(item, grams, fav) },
            onDismiss = { viewModel.dismissLabel() }
        )
    }

    // Diálogo de búsqueda y escaneo de alimentos
    if (showSearchDialog) {
        SearchFoodDialog(
            mealType = selectedMealTypeForAdd,
            query = state.searchQuery,
            selectedCategory = state.selectedCategory,
            results = state.searchResults,
            isSearching = state.isSearching,
            onQueryChange = { viewModel.onSearchQueryChange(it) },
            onCategorySelect = { viewModel.onCategorySelect(it) },
            onScanBarcode = {
                showSearchDialog = false
                launchBarcodeScanner(selectedMealTypeForAdd)
            },
            onAddFood = { item, grams ->
                viewModel.addFoodFromCatalog(item, grams, selectedMealTypeForAdd)
                showSearchDialog = false
            },
            onDismiss = { showSearchDialog = false },
            favorites = state.favorites,
            recents = state.recents,
            favoriteKeys = state.favoriteKeys,
            onToggleFavorite = { viewModel.toggleFavorite(it) }
        )
    }

    // Diálogo de cálculo de ingesta calórica según peso y objetivo (Mifflin-St Jeor)
    if (state.showCalculatorDialog) {
        CaloricCalculatorDialog(
            currentWeight = state.userWeightKg,
            currentHeight = state.userHeightCm,
            currentGoal = state.userGoal,
            onCalculate = { w, h, g -> viewModel.calculateIntake(w, h, g) },
            onApply = { intake, w, h, g ->
                viewModel.applyCalculatedIntake(intake, w, h, g)
                Toast.makeText(context, "Meta calórica actualizada: ${intake.calories.toInt()} kcal", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { viewModel.setCalculatorDialogVisible(false) }
        )
    }

    // Diálogo para escribir objetivos personalizados manualmente (Calorías y Macros)
    if (showCustomTargetsDialog) {
        CustomMacroTargetsDialog(
            currentTargetCalories = state.targetCalories,
            currentTargetProtein = state.targetProtein,
            currentTargetCarbs = state.targetCarbs,
            currentTargetFat = state.targetFat,
            userHeightCm = state.userHeightCm,
            userWeightKg = state.userWeightKg,
            userGoal = state.userGoal,
            onRecalculateFromProfile = {
                viewModel.recalculateFromProfile()
                showCustomTargetsDialog = false
                Toast.makeText(context, "Objetivos recalculados según tu perfil", Toast.LENGTH_SHORT).show()
            },
            onSaveTargets = { cal, p, c, f ->
                viewModel.updateCustomTargets(cal, p, c, f)
                showCustomTargetsDialog = false
                Toast.makeText(context, "Objetivos guardados: ${cal.toInt()} kcal", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showCustomTargetsDialog = false }
        )
    }

    // Diálogo para ajustar porción (gramos) de un alimento ya registrado
    editingMealItem?.let { meal ->
        EditPortionDialog(
            meal = meal,
            onSaveGrams = { newGrams ->
                viewModel.updateMealGrams(meal, newGrams)
                editingMealItem = null
            },
            onDelete = {
                viewModel.deleteMeal(meal.id)
                editingMealItem = null
            },
            onDismiss = { editingMealItem = null }
        )
    }

    // Diálogo de añadido rápido de calorías o comida manual
    if (showQuickAddDialog) {
        QuickAddMealDialog(
            mealType = selectedMealTypeForAdd,
            onAdd = { name, kcal, p, c, f ->
                viewModel.addQuickMeal(name, kcal, p, c, f, selectedMealTypeForAdd)
                showQuickAddDialog = false
            },
            onDismiss = { showQuickAddDialog = false }
        )
    }

    // Diálogo para configurar clave de Gemini API directamente desde Comida
    if (showApiKeyModal) {
        NutritionApiKeyDialog(
            currentKey = state.geminiApiKey,
            currentModel = state.geminiModel,
            onDismiss  = { showApiKeyModal = false },
            onTest     = { key, model -> viewModel.testApiKey(key, model) },
            onSave     = { newKey, newModel ->
                viewModel.updateGeminiApiKey(newKey)
                viewModel.updateGeminiModel(newModel)
                showApiKeyModal = false
                Toast.makeText(context, "Clave y modelo ($newModel) guardados", Toast.LENGTH_SHORT).show()
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(GradientStart, GradientEnd)))
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // ─── Header con selector de fecha y estado de IA ─────────────
            item {
                NutritionHeader(
                    selectedDate = state.selectedDate,
                    hasGeminiApiKey = state.hasGeminiApiKey,
                    onPreviousDay = { viewModel.selectDate(state.selectedDate.minusDays(1)) },
                    onNextDay = { viewModel.selectDate(state.selectedDate.plusDays(1)) },
                    onTodayClick = { viewModel.selectDate(LocalDate.now()) },
                    onApiKeyClick = { showApiKeyModal = true }
                )
            }

            // ─── Resumen Hero de Macros y Balance Calórico ────────────────
            item {
                NutritionHeroCard(
                    state = state,
                    onOpenCustomTargets = { showCustomTargetsDialog = true },
                    onOpenCalculator = { viewModel.setCalculatorDialogVisible(true) }
                )
            }

            // ─── Atajos: copiar ayer, etiqueta, comidas guardadas, favoritos ─
            item {
                FoodShortcutsCard(
                    state = state,
                    onCopyYesterday = { viewModel.copyPreviousDay() },
                    onScanLabel = { labelLauncher.launch(null) },
                    onSaveMeal = { showSaveMeal = true },
                    onOpenSavedMeals = { showSavedMeals = true },
                    onQuickFood = { quickFood = it }
                )
            }

            // ─── Coach Nutricional IA ─────────────────────────────────────
            item {
                NutritionCoachCard(
                    advice = state.nutritionAdvice,
                    isLoading = state.isAdviceLoading,
                    onRefresh = { viewModel.requestNutritionAdvice() }
                )
            }

            // ─── Secciones de Comida ──────────────────────────────────────
            items(MealSectionType.entries.toTypedArray()) { section ->
                MealSectionCard(
                    section = section,
                    meals = state.mealsForType(section.key),
                    onAddClick = {
                        selectedMealTypeForAdd = section.key
                        showSearchDialog = true
                    },
                    onScanBarcodeClick = {
                        launchBarcodeScanner(section.key)
                    },
                    onAiClick = {
                        selectedMealTypeForAdd = section.key
                        viewModel.openTextAiDialog(section.key)
                    },
                    onQuickAddClick = {
                        selectedMealTypeForAdd = section.key
                        showQuickAddDialog = true
                    },
                    onEditMeal = { meal ->
                        editingMealItem = meal
                    },
                    onDeleteMeal = { mealId ->
                        viewModel.deleteMeal(mealId)
                    }
                )
            }

            item {
                Spacer(Modifier.height(80.dp))
            }
        }
    }
}

// ─── Header con selector de fecha y estado de IA ─────────────────────────────
