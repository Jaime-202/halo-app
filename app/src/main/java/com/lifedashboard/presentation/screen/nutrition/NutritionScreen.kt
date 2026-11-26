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

    var aiBasket by remember { mutableStateOf<List<BasketItem>>(emptyList()) }

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
            onConfirm = { items, type -> aiBasket = viewModel.aiItemsToBasket(items); viewModel.clearStagedPhoto(); selectedMealTypeForAdd = type; showSearchDialog = true },
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
            onConfirm = { items, type -> aiBasket = viewModel.aiItemsToBasket(items); viewModel.closeTextAiDialog(); selectedMealTypeForAdd = type; showSearchDialog = true },
            onDismiss = { viewModel.closeTextAiDialog() }
        )
    }

    // Diálogo de confirmación de alimento detectado por QR o Código de Barras (1-tap add)
    state.scannedFoodItem?.let { food ->
        FoodDetailSheet(
            food = food,
            initialGrams = food.servingSizeGrams.takeIf { it > 0f } ?: 100f,
            remainingKcal = state.targetCalories - state.totalCalories,
            remainingProtein = state.targetProtein - state.totalProtein,
            isFavorite = NutritionViewModel.foodKey(food.name, food.brand) in state.favoriteKeys,
            primaryLabel = "Añadir a ${MealSectionType.labelFor(state.activeMealType).lowercase()}",
            onPrimary = { grams ->
                viewModel.addFoodFromCatalog(food, grams, state.activeMealType)
                viewModel.dismissScannedItem()
                Toast.makeText(context, "Añadido: ${food.name}", Toast.LENGTH_SHORT).show()
            },
            onToggleFavorite = { viewModel.toggleFavorite(food) },
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
        AddFoodScreen(
            state = state,
            initialMealType = selectedMealTypeForAdd,
            onQuery = { viewModel.onSearchQueryChange(it) },
            onCategory = { viewModel.onCategorySelect(it) },
            onAddBasket = { items, type -> viewModel.addBasket(items, type) },
            onAddSavedMeal = { saved, type -> viewModel.addSavedMeal(saved, type); showSearchDialog = false },
            onToggleFavorite = { viewModel.toggleFavorite(it) },
            onScan = { type -> showSearchDialog = false; launchBarcodeScanner(type) },
            onPhoto = { type -> showSearchDialog = false; selectedMealTypeForAdd = type; cameraLauncher.launch(null) },
            onLabel = { showSearchDialog = false; labelLauncher.launch(null) },
            onDescribe = { type -> showSearchDialog = false; selectedMealTypeForAdd = type; viewModel.openTextAiDialog(type) },
            onDismiss = { showSearchDialog = false; aiBasket = emptyList() },
            initialBasket = aiBasket,
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
        val per = if (meal.grams > 0f) 100f / meal.grams else 1f
        val food = com.lifedashboard.data.remote.food.FoodCatalogItem(
            id = "meal_${meal.id}", name = meal.name, brand = meal.brand,
            caloriesPer100g = meal.calories * per, proteinPer100g = meal.protein * per,
            carbsPer100g = meal.carbs * per, fatPer100g = meal.fat * per,
            servingSizeGrams = meal.grams, servingDescription = "${meal.grams.toInt()} g",
            imageUrl = meal.imageUrl, fiberPer100g = meal.fiber * per, sugarPer100g = meal.sugar * per, saltPer100g = meal.salt * per
        )
        FoodDetailSheet(
            food = food,
            initialGrams = meal.grams,
            remainingKcal = state.targetCalories - state.totalCalories + meal.calories,
            remainingProtein = state.targetProtein - state.totalProtein + meal.protein,
            isFavorite = NutritionViewModel.foodKey(meal.name, meal.brand) in state.favoriteKeys,
            primaryLabel = "Guardar",
            onPrimary = { g -> viewModel.updateMealGrams(meal, g); editingMealItem = null },
            onToggleFavorite = { viewModel.toggleFavorite(food) },
            onDelete = { viewModel.deleteMeal(meal.id); editingMealItem = null },
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

    // Aviso con "Deshacer"
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.undo?.id) {
        val info = state.undo ?: return@LaunchedEffect
        val result = snackbar.showSnackbar(info.message, actionLabel = "Deshacer", duration = SnackbarDuration.Short)
        if (result == SnackbarResult.ActionPerformed) viewModel.undo() else viewModel.consumeUndo()
    }

    Box(Modifier.fillMaxSize()) {
    NutritionHome(
        state = state,
        a = NutritionHomeActions(
            selectDate = { viewModel.selectDate(it) },
            add = { type -> selectedMealTypeForAdd = type; showSearchDialog = true },
            quickAddFood = { food, type ->
                viewModel.addFoodFromCatalog(food, food.servingSizeGrams.takeIf { it > 0f } ?: 100f, type)
                Toast.makeText(context, "Añadido: ${food.name}", Toast.LENGTH_SHORT).show()
            },
            repeatYesterday = { viewModel.repeatYesterday(it) },
            editMeal = { editingMealItem = it },
            deleteMeal = { viewModel.deleteMeal(it.id) },
            duplicateMeal = { viewModel.duplicateMeal(it) },
            addWater = { viewModel.addWater(250) },
            copyYesterday = { viewModel.copyPreviousDay() },
            saveMeal = { showSaveMeal = true },
            targets = { showCustomTargetsDialog = true },
            calculator = { viewModel.setCalculatorDialogVisible(true) },
            gemini = { showApiKeyModal = true },
        )
    )
    SnackbarHost(
        snackbar,
        modifier = Modifier.align(Alignment.BottomCenter).padding(start = 16.dp, end = 16.dp, bottom = 176.dp),
    ) { data ->
        Snackbar(
            data,
            containerColor = PanelHigh,
            contentColor = TextHigh,
            actionColor = Ion,
            shape = RoundedCornerShape(16.dp),
        )
    }
    }
}

// ─── Header con selector de fecha y estado de IA ─────────────────────────────
