package com.lifedashboard.presentation.screen.nutrition

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lifedashboard.data.local.dao.HealthDao
import com.lifedashboard.data.local.dao.WeightDao
import com.lifedashboard.data.local.entity.MealEntryEntity
import com.lifedashboard.data.remote.food.DetectedFoodItem
import com.lifedashboard.data.remote.food.FoodAiResult
import com.lifedashboard.data.remote.food.FoodCatalogItem
import com.lifedashboard.data.remote.food.FoodCatalogService
import com.lifedashboard.data.remote.food.FoodSourceCategory
import com.lifedashboard.data.remote.food.RealFoodImageResolver
import com.lifedashboard.data.repository.NutritionRepository
import com.lifedashboard.data.repository.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

enum class MealSectionType(val key: String, val title: String, val iconEmoji: String) {
    DESAYUNO("DESAYUNO", "Desayuno", "☀️"),
    COMIDA("COMIDA", "Comida", "🍲"),
    MERIENDA("MERIENDA", "Merienda", "☕"),
    CENA("CENA", "Cena", "🌙"),
    SNACK("SNACK", "Snacks / Otros", "🍎");

    companion object {
        fun labelFor(key: String): String = entries.firstOrNull { it.key == key }?.title ?: "Comida"
    }
}

data class CalculatedIntake(
    val calories: Float,
    val protein: Float,
    val carbs: Float,
    val fat: Float,
    val bmr: Float = 0f,
    val tdee: Float = 0f
)

/** Aviso que se puede deshacer; `id` distingue avisos con el mismo texto. */
data class UndoInfo(val message: String, val id: Long = System.nanoTime())

/** Resumen de un día para la vista semanal de Comida. */
data class NutritionDay(val date: LocalDate, val kcal: Float, val protein: Float)

/** Un alimento en la cesta: qué es y cuántos gramos. */
data class BasketItem(val food: FoodCatalogItem, val grams: Float) {
    val kcal: Float get() = food.caloriesPer100g * grams / 100f
}

data class NutritionUiState(
    val selectedDate: LocalDate = LocalDate.now(),
    val meals: List<MealEntryEntity> = emptyList(),
    val targetCalories: Float = 2200f,
    val targetProtein: Float = 140f,
    val targetCarbs: Float = 250f,
    val targetFat: Float = 70f,
    val activeBurnedCalories: Float = 0f,

    // Perfil y cálculo de ingesta
    val userWeightKg: Float = 75f,
    val userHeightCm: Int = 175,
    val userGoal: String = "PERDIDA_PESO", // "PERDIDA_PESO", "MANTENIMIENTO", "VOLUMEN"
    val showCalculatorDialog: Boolean = false,

    // Búsqueda de alimentos y supermercados
    val searchQuery: String = "",
    val selectedCategory: FoodSourceCategory = FoodSourceCategory.TODOS,
    val searchResults: List<FoodCatalogItem> = emptyList(),
    val isSearching: Boolean = false,

    // Análisis de foto con IA
    val stagedBitmap: Bitmap? = null,
    val stagedDescription: String = "",
    val activeMealType: String = "COMIDA",
    val isAiAnalyzing: Boolean = false,
    val aiResultItems: List<DetectedFoodItem>? = null,
    val aiErrorMessage: String? = null,
    val aiExplanation: String? = null,

    // Registro de comida por texto con IA (sin foto)
    val showTextAiDialog: Boolean = false,
    val textAiInput: String = "",
    val isTextAiAnalyzing: Boolean = false,
    val textAiResultItems: List<DetectedFoodItem>? = null,
    val textAiErrorMessage: String? = null,
    val textAiExplanation: String? = null,
    val textAiMealType: String = "COMIDA",

    // Coach Nutricional IA
    val nutritionAdvice: String? = null,
    val isAdviceLoading: Boolean = false,

    // Escáner de Códigos de Barras y QR
    val scannedFoodItem: FoodCatalogItem? = null,
    val scannedBarcode: String? = null,
    val isScanningBarcode: Boolean = false,
    val scannedUnknownBarcode: String? = null,

    // Estado de Clave Gemini y Prueba de Conexión
    val hasGeminiApiKey: Boolean = false,
    val geminiApiKey: String = "",
    val geminiModel: String = "gemini-3.8-flash",
    val isTestingApiKey: Boolean = false,
    val apiKeyTestMessage: String? = null,
    val apiKeyTestSuccess: Boolean? = null,

    // Biblioteca: favoritos, recientes y comidas guardadas
    val favorites: List<FoodCatalogItem> = emptyList(),
    val favoriteKeys: Set<String> = emptySet(),
    val recents: List<FoodCatalogItem> = emptyList(),
    val savedMeals: List<com.lifedashboard.data.local.entity.SavedMealEntity> = emptyList(),

    // Lectura de etiqueta nutricional
    val isReadingLabel: Boolean = false,
    val labelValues: com.lifedashboard.domain.food.LabelValues? = null,
    val labelMealType: String = "COMIDA",
    val libraryMessage: String? = null,

    // Comida (diseño nuevo)
    val recentEntries: List<MealEntryEntity> = emptyList(),
    val waterMl: Int = 0,
    val waterTargetMl: Int = 2500,
    val weekDays: List<NutritionDay> = emptyList(),
    /** Aviso con "Deshacer" tras borrar, duplicar o copiar. */
    val undo: UndoInfo? = null
) {
    /** Lo que sueles comer en ese momento del día, por frecuencia (máx. 6). */
    fun suggestionsFor(mealType: String): List<FoodCatalogItem> = recentEntries
        .filter { it.mealType == mealType && it.grams > 0f && it.brand != "Manual" }
        .groupBy { "${it.name.trim().lowercase()}|${it.brand.trim().lowercase()}" }
        .entries.sortedByDescending { it.value.size }
        .take(6)
        .map { (_, list) ->
            val e = list.first()
            val f = 100f / e.grams
            FoodCatalogItem(
                id = "sug_${e.name}", name = e.name, brand = e.brand,
                caloriesPer100g = e.calories * f, proteinPer100g = e.protein * f,
                carbsPer100g = e.carbs * f, fatPer100g = e.fat * f,
                servingSizeGrams = e.grams, servingDescription = "${e.grams.toInt()} g",
                imageUrl = e.imageUrl, fiberPer100g = e.fiber * f, sugarPer100g = e.sugar * f, saltPer100g = e.salt * f
            )
        }

    val totalFiber: Float get() = meals.sumOf { it.fiber.toDouble() }.toFloat()
    val totalSugar: Float get() = meals.sumOf { it.sugar.toDouble() }.toFloat()
    val totalSalt: Float get() = meals.sumOf { it.salt.toDouble() }.toFloat()

    val totalCalories: Float get() = meals.sumOf { it.calories.toDouble() }.toFloat()
    val totalProtein: Float get() = meals.sumOf { it.protein.toDouble() }.toFloat()
    val totalCarbs: Float get() = meals.sumOf { it.carbs.toDouble() }.toFloat()
    val totalFat: Float get() = meals.sumOf { it.fat.toDouble() }.toFloat()

    val caloriesProgress: Float get() = if (targetCalories > 0) (totalCalories / targetCalories).coerceIn(0f, 1f) else 0f
    val proteinProgress: Float get() = if (targetProtein > 0) (totalProtein / targetProtein).coerceIn(0f, 1f) else 0f
    val carbsProgress: Float get() = if (targetCarbs > 0) (totalCarbs / targetCarbs).coerceIn(0f, 1f) else 0f
    val fatProgress: Float get() = if (targetFat > 0) (totalFat / targetFat).coerceIn(0f, 1f) else 0f

    val remainingCalories: Float get() = targetCalories - totalCalories

    fun mealsForType(type: String): List<MealEntryEntity> = meals.filter { it.mealType == type }
}

@HiltViewModel
class NutritionViewModel @Inject constructor(
    private val repository: NutritionRepository,
    private val userPreferences: UserPreferencesRepository,
    private val weightDao: WeightDao,
    private val healthDao: HealthDao,
    private val foodLibraryDao: com.lifedashboard.data.local.dao.FoodLibraryDao,
    private val waterIntakeDao: com.lifedashboard.data.local.dao.WaterIntakeDao,
    private val mealDao: com.lifedashboard.data.local.dao.MealDao,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(NutritionUiState())
    val uiState: StateFlow<NutritionUiState> = _uiState.asStateFlow()

    private var mealsJob: Job? = null
    private var searchJob: Job? = null

    init {
        // Observar objetivos del usuario y perfil antropométrico
        userPreferences.userGoals
            .onEach { goals ->
                val normalizedGoal = when (goals.primaryGoal) {
                    "GRASA" -> "PERDIDA_PESO"
                    "MUSCULO" -> "VOLUMEN"
                    else -> "MANTENIMIENTO"
                }

                // Si las calorías objetivo están desactualizadas o por debajo de un mínimo realista (< 1200 kcal),
                // calcular automáticamente los valores óptimos basados en peso, altura y objetivo.
                if (goals.caloriesTarget < 1200f) {
                    val intake = calculateIntake(goals.weightKg, goals.heightCm, normalizedGoal)
                    userPreferences.updateCalorieAndMacroTargets(
                        calories = intake.calories,
                        protein = intake.protein,
                        carbs = intake.carbs,
                        fat = intake.fat
                    )
                }

                _uiState.update {
                    it.copy(
                        targetCalories = if (goals.caloriesTarget < 1200f) 1850f else goals.caloriesTarget,
                        targetProtein  = goals.proteinTargetGrams,
                        targetCarbs    = goals.carbsTargetGrams,
                        targetFat      = goals.fatTargetGrams,
                        userWeightKg   = goals.weightKg,
                        userHeightCm   = goals.heightCm,
                        userGoal       = normalizedGoal,
                        hasGeminiApiKey = goals.geminiApiKey.isNotBlank(),
                        geminiApiKey   = goals.geminiApiKey,
                        geminiModel    = goals.geminiModel
                    )
                }
            }
            .launchIn(viewModelScope)

        // Cargar peso más reciente registrado si existe
        viewModelScope.launch(Dispatchers.IO) {
            val latest = weightDao.getLatestWeight()
            if (latest != null && latest.weightKg > 0) {
                _uiState.update { it.copy(userWeightKg = latest.weightKg) }
            }
        }

        // Cargar comidas y gasto calórico de hoy
        observeMealsForDate(LocalDate.now())
        loadHealthDataForDate(LocalDate.now())

        // Cargar catálogo inicial
        viewModelScope.launch {
            executeSearch("", FoodSourceCategory.TODOS)
        }

        // Solicitar consejo inicial del coach
        viewModelScope.launch {
            delay(400)
            requestNutritionAdvice()
        }
    }

    fun selectDate(date: LocalDate) {
        _uiState.update { it.copy(selectedDate = date) }
        observeDayExtras(date)
        observeMealsForDate(date)
        loadHealthDataForDate(date)
        viewModelScope.launch {
            delay(300)
            requestNutritionAdvice()
        }
    }

    private fun observeMealsForDate(date: LocalDate) {
        mealsJob?.cancel()
        mealsJob = repository.observeMealsForDate(date)
            .onEach { list ->
                _uiState.update { it.copy(meals = list) }
            }
            .flowOn(Dispatchers.IO)
            .launchIn(viewModelScope)
    }

    private fun loadHealthDataForDate(date: LocalDate) {
        viewModelScope.launch(Dispatchers.IO) {
            val zone = ZoneId.systemDefault()
            val millis = date.atStartOfDay(zone).toInstant().toEpochMilli()
            val health = healthDao.getHealthForDay(millis)
            val burned = health?.activeCalories ?: 0f
            _uiState.update { it.copy(activeBurnedCalories = burned) }
        }
    }

    // ─── Búsqueda de Alimentos y Supermercados ────────────────────────────────

    fun onSearchQueryChange(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(250) // debounce
            executeSearch(query, _uiState.value.selectedCategory)
        }
    }

    fun onCategorySelect(category: FoodSourceCategory) {
        _uiState.update { it.copy(selectedCategory = category) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            executeSearch(_uiState.value.searchQuery, category)
        }
    }

    private suspend fun executeSearch(query: String, category: FoodSourceCategory) {
        _uiState.update { it.copy(isSearching = true) }
        // 1) Al instante: lo que ya está en el móvil (genéricos y catálogos)
        val local = repository.searchFoods(query, category, includeRemote = false)
        _uiState.update { it.copy(searchResults = local, isSearching = query.trim().length >= 2 && local.size < 12) }
        // 2) Después: productos de supermercado de Open Food Facts
        if (query.trim().length >= 2 && local.size < 12) {
            val full = repository.searchFoods(query, category)
            _uiState.update { it.copy(searchResults = full, isSearching = false) }
        }
    }


    fun onBarcodeDetected(rawCode: String, mealType: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isScanningBarcode = true, activeMealType = mealType) }
            val item = repository.findFoodByBarcode(rawCode)
            if (item != null) {
                _uiState.update {
                    it.copy(
                        isScanningBarcode = false,
                        scannedFoodItem = item,
                        scannedBarcode = rawCode,
                        scannedUnknownBarcode = null
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isScanningBarcode = false,
                        scannedFoodItem = null,
                        scannedBarcode = rawCode,
                        scannedUnknownBarcode = rawCode
                    )
                }
            }
        }
    }

    fun dismissScannedItem() {
        _uiState.update { it.copy(scannedFoodItem = null, scannedUnknownBarcode = null, isScanningBarcode = false) }
    }

    fun addFoodFromCatalog(
        item: FoodCatalogItem,
        grams: Float,
        mealType: String
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val factor = grams / 100f
            val date = _uiState.value.selectedDate
            val zone = ZoneId.systemDefault()
            val dateMillis = date.atStartOfDay(zone).toInstant().toEpochMilli()

            var calories = item.caloriesPer100g * factor
            var protein = item.proteinPer100g * factor
            var carbs = item.carbsPer100g * factor
            var fat = item.fatPer100g * factor

            if (calories <= 0f && (protein > 0f || carbs > 0f || fat > 0f)) {
                calories = (protein * 4f) + (carbs * 4f) + (fat * 9f)
            }
            if (calories <= 0f && !item.name.contains("agua", ignoreCase = true)) {
                val inf = FoodCatalogService.inferMacrosIfZero(item.name, 0f, protein, carbs, fat)
                calories = inf.calories * factor
                if (protein <= 0f) protein = inf.protein * factor
                if (carbs <= 0f) carbs = inf.carbs * factor
                if (fat <= 0f) fat = inf.fat * factor
            }

            val entry = MealEntryEntity(
                dateMillis = dateMillis,
                mealType = mealType,
                name = item.name,
                brand = item.brand,
                grams = grams,
                calories = calories,
                protein = protein,
                carbs = carbs,
                fat = fat,
                imageUrl = RealFoodImageResolver.resolve(item.name, item.brand, item.imageUrl),
                fiber = item.fiberPer100g * factor,
                sugar = item.sugarPer100g * factor,
                salt = item.saltPer100g * factor
            )
            repository.insertMeal(entry)
            delay(200)
            requestNutritionAdvice()
        }
    }

    // ─── Biblioteca: favoritos, recientes, comidas guardadas ──────────────────

    init {
        foodLibraryDao.observeFavorites()
            .onEach { list ->
                _uiState.update { st ->
                    st.copy(
                        favorites = list.map { it.toCatalogItem() },
                        favoriteKeys = list.map { it.key }.toSet()
                    )
                }
            }
            .launchIn(viewModelScope)

        foodLibraryDao.observeSavedMeals()
            .onEach { list -> _uiState.update { it.copy(savedMeals = list) } }
            .launchIn(viewModelScope)

        foodLibraryDao.observeRecentEntries()
            .onEach { entries ->
                val recents = entries
                    .filter { it.grams > 0f && it.brand != "Manual" }
                    .distinctBy { foodKey(it.name, it.brand) }
                    .take(15)
                    .map { it.toPer100CatalogItem() }
                _uiState.update { it.copy(recents = recents, recentEntries = entries) }
            }
            .launchIn(viewModelScope)
    }

    fun isFavorite(item: FoodCatalogItem): Boolean = foodKey(item.name, item.brand) in _uiState.value.favoriteKeys

    fun toggleFavorite(item: FoodCatalogItem) {
        viewModelScope.launch(Dispatchers.IO) {
            val key = foodKey(item.name, item.brand)
            if (key in _uiState.value.favoriteKeys) {
                foodLibraryDao.deleteFavorite(key)
            } else {
                foodLibraryDao.upsertFavorite(
                    com.lifedashboard.data.local.entity.FavoriteFoodEntity(
                        key = key,
                        name = item.name,
                        brand = item.brand,
                        caloriesPer100g = item.caloriesPer100g,
                        proteinPer100g = item.proteinPer100g,
                        carbsPer100g = item.carbsPer100g,
                        fatPer100g = item.fatPer100g,
                        fiberPer100g = item.fiberPer100g,
                        sugarPer100g = item.sugarPer100g,
                        saltPer100g = item.saltPer100g,
                        servingSizeGrams = item.servingSizeGrams,
                        servingDescription = item.servingDescription,
                        imageUrl = item.imageUrl,
                        barcode = item.barcode
                    )
                )
            }
        }
    }

    /** Guarda los alimentos de un momento del día como comida reutilizable. */
    fun saveMealGroup(mealType: String, name: String) {
        val items = _uiState.value.mealsForType(mealType)
        if (items.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val array = org.json.JSONArray()
            items.forEach { m ->
                array.put(
                    org.json.JSONObject()
                        .put("name", m.name).put("brand", m.brand).put("grams", m.grams.toDouble())
                        .put("calories", m.calories.toDouble()).put("protein", m.protein.toDouble())
                        .put("carbs", m.carbs.toDouble()).put("fat", m.fat.toDouble())
                        .put("fiber", m.fiber.toDouble()).put("sugar", m.sugar.toDouble())
                        .put("salt", m.salt.toDouble()).put("imageUrl", m.imageUrl ?: "")
                )
            }
            foodLibraryDao.insertSavedMeal(
                com.lifedashboard.data.local.entity.SavedMealEntity(
                    name = name.ifBlank { MealSectionType.labelFor(mealType) },
                    itemsJson = array.toString(),
                    totalCalories = items.sumOf { it.calories.toDouble() }.toFloat()
                )
            )
            _uiState.update { it.copy(libraryMessage = "Comida guardada") }
        }
    }

    /** Añade todos los alimentos de una comida guardada al día seleccionado. */
    fun addSavedMeal(saved: com.lifedashboard.data.local.entity.SavedMealEntity, mealType: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val dateMillis = _uiState.value.selectedDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val array = org.json.JSONArray(saved.itemsJson)
            val entries = (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                MealEntryEntity(
                    dateMillis = dateMillis,
                    mealType = mealType,
                    name = o.optString("name"),
                    brand = o.optString("brand"),
                    grams = o.optDouble("grams", 100.0).toFloat(),
                    calories = o.optDouble("calories", 0.0).toFloat(),
                    protein = o.optDouble("protein", 0.0).toFloat(),
                    carbs = o.optDouble("carbs", 0.0).toFloat(),
                    fat = o.optDouble("fat", 0.0).toFloat(),
                    fiber = o.optDouble("fiber", 0.0).toFloat(),
                    sugar = o.optDouble("sugar", 0.0).toFloat(),
                    salt = o.optDouble("salt", 0.0).toFloat(),
                    imageUrl = o.optString("imageUrl").takeIf { it.isNotBlank() }
                )
            }
            repository.insertMeals(entries)
            _uiState.update { it.copy(libraryMessage = "${saved.name} añadida") }
        }
    }

    fun deleteSavedMeal(id: Long) {
        viewModelScope.launch(Dispatchers.IO) { foodLibraryDao.deleteSavedMeal(id) }
    }

    /** Copia todas las comidas del día anterior al día seleccionado. */
    fun copyPreviousDay() {
        viewModelScope.launch(Dispatchers.IO) {
            val zone = ZoneId.systemDefault()
            val target = _uiState.value.selectedDate
            val prev = target.minusDays(1)
            val prevStart = prev.atStartOfDay(zone).toInstant().toEpochMilli()
            val prevEnd = target.atStartOfDay(zone).toInstant().toEpochMilli() - 1
            val targetMillis = target.atStartOfDay(zone).toInstant().toEpochMilli()
            val source = foodLibraryDaoMealsBetween(prevStart, prevEnd)
            if (source.isEmpty()) {
                _uiState.update { it.copy(libraryMessage = "Ayer no hay comidas registradas") }
                return@launch
            }
            val now = System.currentTimeMillis()
            val ids = repository.insertMeals(source.map { it.copy(id = 0, dateMillis = targetMillis, createdAt = now) })
            offerUndo("${source.size} alimentos copiados de ayer") { ids.forEach { repository.deleteMeal(it) } }
        }
    }

    private suspend fun foodLibraryDaoMealsBetween(start: Long, end: Long) =
        repository.mealsBetween(start, end)

    private var undoAction: (suspend () -> Unit)? = null

    private fun offerUndo(message: String, action: suspend () -> Unit) {
        undoAction = action
        _uiState.update { it.copy(undo = UndoInfo(message)) }
    }

    /** Deshace la última acción (borrar, duplicar, copiar o repetir). */
    fun undo() {
        val action = undoAction ?: return
        undoAction = null
        viewModelScope.launch(Dispatchers.IO) { action() }
        _uiState.update { it.copy(undo = null) }
    }

    fun consumeUndo() = _uiState.update { it.copy(undo = null) }

    fun consumeLibraryMessage() = _uiState.update { it.copy(libraryMessage = null) }

    // ─── Lectura de etiqueta nutricional ──────────────────────────────────────

    fun readNutritionLabel(bitmap: Bitmap, mealType: String) {
        _uiState.update { it.copy(isReadingLabel = true, labelMealType = mealType, labelValues = null) }
        val recognizer = com.google.mlkit.vision.text.TextRecognition.getClient(
            com.google.mlkit.vision.text.latin.TextRecognizerOptions.DEFAULT_OPTIONS
        )
        recognizer.process(com.google.mlkit.vision.common.InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { result ->
                val values = com.lifedashboard.domain.food.NutritionLabelParser.parse(result.text)
                _uiState.update {
                    it.copy(
                        isReadingLabel = false,
                        labelValues = values,
                        libraryMessage = if (values.foundCount == 0) "No se ha podido leer la etiqueta. Escribe los valores a mano." else null
                    )
                }
                recognizer.close()
            }
            .addOnFailureListener {
                _uiState.update {
                    it.copy(
                        isReadingLabel = false,
                        labelValues = com.lifedashboard.domain.food.LabelValues(),
                        libraryMessage = "No se ha podido leer la etiqueta. Escribe los valores a mano."
                    )
                }
                recognizer.close()
            }
    }

    fun dismissLabel() = _uiState.update { it.copy(labelValues = null, isReadingLabel = false) }

    /** Registra un producto leído de su etiqueta (valores por 100 g) y lo guarda en favoritos. */
    fun addFromLabel(item: FoodCatalogItem, grams: Float, saveAsFavorite: Boolean) {
        val mealType = _uiState.value.labelMealType
        addFoodFromCatalog(item, grams, mealType)
        if (saveAsFavorite && foodKey(item.name, item.brand) !in _uiState.value.favoriteKeys) toggleFavorite(item)
        dismissLabel()
    }

    private fun com.lifedashboard.data.local.entity.FavoriteFoodEntity.toCatalogItem() = FoodCatalogItem(
        id = "fav_$key",
        name = name,
        brand = brand,
        caloriesPer100g = caloriesPer100g,
        proteinPer100g = proteinPer100g,
        carbsPer100g = carbsPer100g,
        fatPer100g = fatPer100g,
        servingSizeGrams = servingSizeGrams,
        servingDescription = servingDescription,
        barcode = barcode,
        imageUrl = imageUrl,
        fiberPer100g = fiberPer100g,
        sugarPer100g = sugarPer100g,
        saltPer100g = saltPer100g
    )

    private fun MealEntryEntity.toPer100CatalogItem(): FoodCatalogItem {
        val f = if (grams > 0f) 100f / grams else 1f
        return FoodCatalogItem(
            id = "rec_${foodKey(name, brand)}",
            name = name,
            brand = brand,
            caloriesPer100g = calories * f,
            proteinPer100g = protein * f,
            carbsPer100g = carbs * f,
            fatPer100g = fat * f,
            servingSizeGrams = grams,
            servingDescription = "Última vez: ${grams.toInt()} g",
            imageUrl = imageUrl,
            fiberPer100g = fiber * f,
            sugarPer100g = sugar * f,
            saltPer100g = salt * f
        )
    }

    companion object {
        fun foodKey(name: String, brand: String): String =
            "${name.trim().lowercase()}|${brand.trim().lowercase()}"
    }

    // ─── Comida: cesta, duplicar, repetir, agua y semana ──────────────────────

    /** Añade varios alimentos de golpe al momento indicado. */
    fun addBasket(items: List<BasketItem>, mealType: String) {
        items.forEach { addFoodFromCatalog(it.food, it.grams, mealType) }
        if (items.isNotEmpty()) _uiState.update { it.copy(libraryMessage = if (items.size == 1) "Añadido" else "${items.size} alimentos añadidos") }
    }

    fun duplicateMeal(meal: MealEntryEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            val id = repository.insertMeal(meal.copy(id = 0, createdAt = System.currentTimeMillis()))
            offerUndo("${meal.name} duplicado") { repository.deleteMeal(id) }
        }
    }

    /** Repite lo que comiste ayer en ese mismo momento del día. */
    fun repeatYesterday(mealType: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val zone = ZoneId.systemDefault()
            val target = _uiState.value.selectedDate
            val start = target.minusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val end = target.atStartOfDay(zone).toInstant().toEpochMilli() - 1
            val src = repository.mealsBetween(start, end).filter { it.mealType == mealType }
            if (src.isEmpty()) {
                _uiState.update { it.copy(libraryMessage = "Ayer no apuntaste ${MealSectionType.labelFor(mealType).lowercase()}") }
                return@launch
            }
            val day = target.atStartOfDay(zone).toInstant().toEpochMilli()
            val now = System.currentTimeMillis()
            val ids = repository.insertMeals(src.map { it.copy(id = 0, dateMillis = day, createdAt = now) })
            offerUndo("${MealSectionType.labelFor(mealType)} de ayer repetido") { ids.forEach { repository.deleteMeal(it) } }
        }
    }

    fun addWater(ml: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val day = _uiState.value.selectedDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            waterIntakeDao.insert(
                com.lifedashboard.data.local.entity.WaterIntakeEntity(dateMillis = day, timestampMillis = System.currentTimeMillis(), amountMl = ml)
            )
        }
    }

    private var waterJob: kotlinx.coroutines.Job? = null
    private var weekJob: kotlinx.coroutines.Job? = null

    /** Agua del día y resumen de los 7 días que terminan en el día elegido. */
    private fun observeDayExtras(date: LocalDate) {
        val zone = ZoneId.systemDefault()
        val day = date.atStartOfDay(zone).toInstant().toEpochMilli()
        waterJob?.cancel()
        waterJob = waterIntakeDao.getTotalForDay(day)
            .onEach { ml -> _uiState.update { it.copy(waterMl = ml ?: 0) } }
            .launchIn(viewModelScope)
        weekJob?.cancel()
        val from = date.minusDays(6)
        weekJob = mealDao.observeMealsForDay(from.atStartOfDay(zone).toInstant().toEpochMilli(), date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1)
            .onEach { meals ->
                val byDay = meals.groupBy { java.time.Instant.ofEpochMilli(it.dateMillis).atZone(zone).toLocalDate() }
                val days = (0..6).map { i ->
                    val d = from.plusDays(i.toLong())
                    val l = byDay[d].orEmpty()
                    NutritionDay(d, l.sumOf { it.calories.toDouble() }.toFloat(), l.sumOf { it.protein.toDouble() }.toFloat())
                }
                _uiState.update { it.copy(weekDays = days) }
            }
            .launchIn(viewModelScope)
    }

    init {
        viewModelScope.launch {
            userPreferences.userGoals.collect { g -> _uiState.update { it.copy(waterTargetMl = g.waterTargetMl) } }
        }
        observeDayExtras(LocalDate.now())
    }

    fun deleteMeal(mealId: Long) {
        val removed = _uiState.value.meals.firstOrNull { it.id == mealId }
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteMeal(mealId)
            if (removed != null) offerUndo("${removed.name} borrado") { repository.insertMeal(removed) }
            delay(200)
            requestNutritionAdvice()
        }
    }

    fun updateMealGrams(meal: MealEntryEntity, newGrams: Float) {
        if (meal.grams <= 0f || newGrams <= 0f) return
        val ratio = newGrams / meal.grams
        val updated = meal.copy(
            grams = newGrams,
            calories = (meal.calories * ratio).coerceAtLeast(0f),
            protein = (meal.protein * ratio).coerceAtLeast(0f),
            carbs = (meal.carbs * ratio).coerceAtLeast(0f),
            fat = (meal.fat * ratio).coerceAtLeast(0f),
            fiber = (meal.fiber * ratio).coerceAtLeast(0f),
            sugar = (meal.sugar * ratio).coerceAtLeast(0f),
            salt = (meal.salt * ratio).coerceAtLeast(0f)
        )
        viewModelScope.launch(Dispatchers.IO) {
            repository.insertMeal(updated)
            delay(200)
            requestNutritionAdvice()
        }
    }

    fun addQuickMeal(
        name: String,
        calories: Float,
        protein: Float,
        carbs: Float,
        fat: Float,
        mealType: String
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val date = _uiState.value.selectedDate
            val zone = ZoneId.systemDefault()
            val dateMillis = date.atStartOfDay(zone).toInstant().toEpochMilli()
            val cleanName = name.ifBlank { "Comida rápida" }

            var safeCalories = calories.coerceAtLeast(0f)
            var safeP = protein.coerceAtLeast(0f)
            var safeC = carbs.coerceAtLeast(0f)
            var safeF = fat.coerceAtLeast(0f)

            if (safeCalories <= 0f && (safeP > 0f || safeC > 0f || safeF > 0f)) {
                safeCalories = (safeP * 4f) + (safeC * 4f) + (safeF * 9f)
            }
            if (safeCalories <= 0f && !cleanName.contains("agua", ignoreCase = true)) {
                val inf = FoodCatalogService.inferMacrosIfZero(cleanName, 0f, safeP, safeC, safeF)
                safeCalories = inf.calories
                if (safeP <= 0f) safeP = inf.protein
                if (safeC <= 0f) safeC = inf.carbs
                if (safeF <= 0f) safeF = inf.fat
            }

            val entry = MealEntryEntity(
                dateMillis = dateMillis,
                mealType = mealType,
                name = cleanName,
                brand = "Manual",
                grams = 100f,
                calories = safeCalories,
                protein = safeP,
                carbs = safeC,
                fat = safeF,
                imageUrl = RealFoodImageResolver.resolve(cleanName, "Manual")
            )
            repository.insertMeal(entry)
            delay(200)
            requestNutritionAdvice()
        }
    }

    fun updateCustomTargets(calories: Float, protein: Float, carbs: Float, fat: Float) {
        viewModelScope.launch {
            userPreferences.updateCalorieAndMacroTargets(
                calories = calories.coerceAtLeast(500f),
                protein = protein.coerceAtLeast(0f),
                carbs = carbs.coerceAtLeast(0f),
                fat = fat.coerceAtLeast(0f)
            )
            com.lifedashboard.core.worker.WidgetUpdateWorker.updateAllWidgets(context)
            _uiState.update {
                it.copy(
                    targetCalories = calories.coerceAtLeast(500f),
                    targetProtein = protein.coerceAtLeast(0f),
                    targetCarbs = carbs.coerceAtLeast(0f),
                    targetFat = fat.coerceAtLeast(0f)
                )
            }
            delay(200)
            requestNutritionAdvice()
        }
    }

    // ─── Cálculo y Ajuste de Ingesta Calórica Basada en Peso y Altura ─────────

    fun setCalculatorDialogVisible(visible: Boolean) {
        _uiState.update { it.copy(showCalculatorDialog = visible) }
    }

    /** Últimos datos de perfil (sexo, edad, actividad) para la calculadora. */
    private var energyGoals = com.lifedashboard.data.repository.UserGoals()

    init {
        viewModelScope.launch { userPreferences.userGoals.collect { energyGoals = it } }
    }

    fun calculateIntake(weightKg: Float, heightCm: Int, goal: String): CalculatedIntake {
        val weightGoal = when (goal) {
            "PERDIDA_PESO", "GRASA" -> com.lifedashboard.domain.energy.WeightGoal.PERDER_RAPIDO
            "VOLUMEN", "MUSCULO" -> com.lifedashboard.domain.energy.WeightGoal.GANAR
            else -> com.lifedashboard.domain.energy.WeightGoal.MANTENER
        }
        val profile = com.lifedashboard.domain.energy.EnergyProfile(
            sex = com.lifedashboard.domain.energy.Sex.from(energyGoals.sex),
            ageYears = energyGoals.ageYears,
            heightCm = heightCm,
            weightKg = weightKg,
            activity = com.lifedashboard.domain.energy.ActivityLevel.from(energyGoals.activityLevel),
            goal = weightGoal,
        )
        val t = com.lifedashboard.domain.energy.EnergyCalculator.targets(
            profile, energyGoals.adaptiveTdeeKcal.takeIf { it > 0f }
        )
        val targetCalories = t.caloriesKcal
        val protein = t.proteinG
        val fat = t.fatG
        val carbs = t.carbsG
        val bmr = t.bmrKcal
        val tdee = t.tdeeKcal

        return CalculatedIntake(
            calories = targetCalories,
            protein = protein,
            carbs = carbs,
            fat = fat,
            bmr = bmr,
            tdee = tdee
        )
    }

    fun recalculateFromProfile() {
        viewModelScope.launch {
            val goals = userPreferences.userGoals.first()
            val normalizedGoal = when (goals.primaryGoal) {
                "GRASA" -> "PERDIDA_PESO"
                "MUSCULO" -> "VOLUMEN"
                else -> "MANTENIMIENTO"
            }
            val intake = calculateIntake(goals.weightKg, goals.heightCm, normalizedGoal)
            applyCalculatedIntake(intake, goals.weightKg, goals.heightCm, normalizedGoal)
        }
    }

    fun applyCalculatedIntake(intake: CalculatedIntake, weightKg: Float, heightCm: Int, goal: String) {
        viewModelScope.launch {
            userPreferences.updateCalorieAndMacroTargets(
                calories = intake.calories,
                protein = intake.protein,
                carbs = intake.carbs,
                fat = intake.fat
            )
            val currentFitnessLevel = userPreferences.userGoals.first().fitnessLevel
            userPreferences.updateFitnessProfile(
                level = currentFitnessLevel,
                goal = goal,
                weight = weightKg,
                height = heightCm
            )
            com.lifedashboard.core.worker.WidgetUpdateWorker.updateAllWidgets(context)
            _uiState.update {
                it.copy(
                    targetCalories = intake.calories,
                    targetProtein = intake.protein,
                    targetCarbs = intake.carbs,
                    targetFat = intake.fat,
                    userWeightKg = weightKg,
                    userHeightCm = heightCm,
                    userGoal = goal,
                    showCalculatorDialog = false
                )
            }
            delay(200)
            requestNutritionAdvice()
        }
    }

    // ─── Análisis de Foto con Gemini AI ───────────────────────────────────────

    fun setStagedPhoto(bitmap: Bitmap, mealType: String) {
        _uiState.update {
            it.copy(
                stagedBitmap = bitmap,
                stagedDescription = "",
                activeMealType = mealType,
                aiResultItems = null,
                aiErrorMessage = null,
                aiExplanation = null
            )
        }
    }

    fun onStagedDescriptionChange(desc: String) {
        _uiState.update { it.copy(stagedDescription = desc) }
    }

    fun clearStagedPhoto() {
        _uiState.update {
            it.copy(
                stagedBitmap = null,
                stagedDescription = "",
                aiResultItems = null,
                aiErrorMessage = null,
                aiExplanation = null,
                isAiAnalyzing = false
            )
        }
    }

    fun analyzeStagedPhoto() {
        val bitmap = _uiState.value.stagedBitmap ?: return
        val desc = _uiState.value.stagedDescription
        val targetMeal = _uiState.value.activeMealType
        val existingItemsForMeal = _uiState.value.meals
            .filter { it.mealType.equals(targetMeal, ignoreCase = true) }
            .map { "${it.name} (${it.grams.toInt()}g)" }

        _uiState.update { it.copy(isAiAnalyzing = true, aiErrorMessage = null) }

        viewModelScope.launch {
            when (val result = repository.analyzeFoodPhoto(bitmap, desc, targetMeal, existingItemsForMeal)) {
                is FoodAiResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isAiAnalyzing = false,
                            aiResultItems = result.items,
                            aiExplanation = result.explanation
                        )
                    }
                }
                is FoodAiResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isAiAnalyzing = false,
                            aiErrorMessage = result.message
                        )
                    }
                }
            }
        }
    }

    fun updateAiResultItem(index: Int, item: DetectedFoodItem) {
        val current = _uiState.value.aiResultItems?.toMutableList() ?: return
        if (index in current.indices) {
            current[index] = item
            _uiState.update { it.copy(aiResultItems = current) }
        }
    }

    fun removeAiResultItem(index: Int) {
        val current = _uiState.value.aiResultItems?.toMutableList() ?: return
        if (index in current.indices) {
            current.removeAt(index)
            _uiState.update { it.copy(aiResultItems = current) }
        }
    }

    /** Convierte lo detectado por la IA en elementos de la cesta para revisarlos antes de guardar. */
    fun aiItemsToBasket(items: List<DetectedFoodItem>): List<BasketItem> = items.map { item ->
        val brand = if (!item.isGeneric && item.brand.isNotBlank() && !item.brand.equals("genérico", true) && !item.brand.equals("casero", true)) item.brand else "Genérico"
        var kcal = item.calories; var p = item.protein; var c = item.carbs; var f = item.fat
        if (kcal <= 0f && (p > 0f || c > 0f || f > 0f)) kcal = p * 4f + c * 4f + f * 9f
        val grams = item.grams.takeIf { it > 0f } ?: 100f
        if (kcal <= 0f && !item.name.contains("agua", true)) {
            val factor = grams / 100f
            val inf = FoodCatalogService.inferMacrosIfZero(item.name, 0f, p, c, f)
            kcal = inf.calories * factor
            if (p <= 0f) p = inf.protein * factor
            if (c <= 0f) c = inf.carbs * factor
            if (f <= 0f) f = inf.fat * factor
        }
        val per = 100f / grams
        BasketItem(
            FoodCatalogItem(
                id = "ai_${item.name.hashCode()}_${System.nanoTime()}",
                name = item.name, brand = brand,
                caloriesPer100g = kcal * per, proteinPer100g = p * per, carbsPer100g = c * per, fatPer100g = f * per,
                servingSizeGrams = grams, servingDescription = "${grams.toInt()} g",
                imageUrl = RealFoodImageResolver.resolve(item.name, brand), isGeneric = brand == "Genérico",
            ),
            grams,
        )
    }

    fun confirmAiItems(items: List<DetectedFoodItem>, mealType: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val date = _uiState.value.selectedDate
            val zone = ZoneId.systemDefault()
            val dateMillis = date.atStartOfDay(zone).toInstant().toEpochMilli()

            val entities = items.map { item ->
                val finalBrand = if (!item.isGeneric && item.brand.isNotBlank() && !item.brand.equals("genérico", ignoreCase = true) && !item.brand.equals("casero", ignoreCase = true)) {
                    item.brand
                } else {
                    "Genérico"
                }
                var safeKcal = item.calories
                var safeP = item.protein
                var safeC = item.carbs
                var safeF = item.fat
                if (safeKcal <= 0f && (safeP > 0f || safeC > 0f || safeF > 0f)) {
                    safeKcal = (safeP * 4f) + (safeC * 4f) + (safeF * 9f)
                }
                if (safeKcal <= 0f && !item.name.contains("agua", ignoreCase = true)) {
                    val factor = if (item.grams > 0f) item.grams / 100f else 1f
                    val inf = FoodCatalogService.inferMacrosIfZero(item.name, 0f, safeP, safeC, safeF)
                    safeKcal = inf.calories * factor
                    if (safeP <= 0f) safeP = inf.protein * factor
                    if (safeC <= 0f) safeC = inf.carbs * factor
                    if (safeF <= 0f) safeF = inf.fat * factor
                }

                MealEntryEntity(
                    dateMillis = dateMillis,
                    mealType = mealType,
                    name = item.name,
                    brand = finalBrand,
                    grams = item.grams,
                    calories = safeKcal,
                    protein = safeP,
                    carbs = safeC,
                    fat = safeF,
                    imageUrl = RealFoodImageResolver.resolve(item.name, finalBrand)
                )
            }
            repository.insertMeals(entities)
            clearStagedPhoto()
            delay(200)
            requestNutritionAdvice()
        }
    }

    // ─── Registro de Comida por Texto con IA (Sin Foto) ───────────────────────

    fun openTextAiDialog(mealType: String) {
        _uiState.update {
            it.copy(
                showTextAiDialog = true,
                textAiMealType = mealType,
                textAiInput = "",
                textAiResultItems = null,
                textAiErrorMessage = null,
                textAiExplanation = null,
                isTextAiAnalyzing = false
            )
        }
    }

    fun closeTextAiDialog() {
        _uiState.update { it.copy(showTextAiDialog = false) }
    }

    fun onTextAiInputChange(text: String) {
        _uiState.update { it.copy(textAiInput = text, textAiResultItems = null, textAiErrorMessage = null) }
    }

    fun analyzeTextAiMeal() {
        val input = _uiState.value.textAiInput.trim()
        if (input.isBlank()) return

        val targetMeal = _uiState.value.textAiMealType.ifBlank { _uiState.value.activeMealType }
        val existingItemsForMeal = _uiState.value.meals
            .filter { it.mealType.equals(targetMeal, ignoreCase = true) }
            .map { "${it.name} (${it.grams.toInt()}g)" }

        _uiState.update { it.copy(isTextAiAnalyzing = true, textAiErrorMessage = null) }
        viewModelScope.launch {
            when (val result = repository.analyzeFoodText(input, targetMeal, existingItemsForMeal)) {
                is FoodAiResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isTextAiAnalyzing = false,
                            textAiResultItems = result.items,
                            textAiExplanation = result.explanation
                        )
                    }
                }
                is FoodAiResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isTextAiAnalyzing = false,
                            textAiErrorMessage = result.message
                        )
                    }
                }
            }
        }
    }

    fun updateTextAiResultItem(index: Int, item: DetectedFoodItem) {
        val current = _uiState.value.textAiResultItems?.toMutableList() ?: return
        if (index in current.indices) {
            current[index] = item
            _uiState.update { it.copy(textAiResultItems = current) }
        }
    }

    fun removeTextAiResultItem(index: Int) {
        val current = _uiState.value.textAiResultItems?.toMutableList() ?: return
        if (index in current.indices) {
            current.removeAt(index)
            _uiState.update { it.copy(textAiResultItems = current) }
        }
    }

    fun confirmTextAiItems(items: List<DetectedFoodItem>, mealType: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val date = _uiState.value.selectedDate
            val zone = ZoneId.systemDefault()
            val dateMillis = date.atStartOfDay(zone).toInstant().toEpochMilli()

            val entities = items.map { item ->
                val finalBrand = if (!item.isGeneric && item.brand.isNotBlank() && !item.brand.equals("genérico", ignoreCase = true) && !item.brand.equals("casero", ignoreCase = true)) {
                    item.brand
                } else {
                    "Genérico"
                }
                var safeKcal = item.calories
                var safeP = item.protein
                var safeC = item.carbs
                var safeF = item.fat
                if (safeKcal <= 0f && (safeP > 0f || safeC > 0f || safeF > 0f)) {
                    safeKcal = (safeP * 4f) + (safeC * 4f) + (safeF * 9f)
                }
                if (safeKcal <= 0f && !item.name.contains("agua", ignoreCase = true)) {
                    val factor = if (item.grams > 0f) item.grams / 100f else 1f
                    val inf = FoodCatalogService.inferMacrosIfZero(item.name, 0f, safeP, safeC, safeF)
                    safeKcal = inf.calories * factor
                    if (safeP <= 0f) safeP = inf.protein * factor
                    if (safeC <= 0f) safeC = inf.carbs * factor
                    if (safeF <= 0f) safeF = inf.fat * factor
                }

                MealEntryEntity(
                    dateMillis = dateMillis,
                    mealType = mealType,
                    name = item.name,
                    brand = finalBrand,
                    grams = item.grams,
                    calories = safeKcal,
                    protein = safeP,
                    carbs = safeC,
                    fat = safeF,
                    imageUrl = RealFoodImageResolver.resolve(item.name, finalBrand)
                )
            }
            repository.insertMeals(entities)
            closeTextAiDialog()
            delay(200)
            requestNutritionAdvice()
        }
    }

    // ─── Asesor / Coach Nutricional ──────────────────────────────────────────

    private var adviceJob: kotlinx.coroutines.Job? = null

    fun requestNutritionAdvice(force: Boolean = false) {
        adviceJob?.cancel()
        adviceJob = viewModelScope.launch {
            try {
                val state = _uiState.value
                val advice = repository.getNutritionAdvice(
                    consumedCalories = state.totalCalories,
                    targetCalories = state.targetCalories,
                    burnedCalories = state.activeBurnedCalories,
                    protein = state.totalProtein,
                    targetProtein = state.targetProtein,
                    carbs = state.totalCarbs,
                    targetCarbs = state.targetCarbs,
                    fat = state.totalFat,
                    targetFat = state.targetFat,
                    goal = state.userGoal
                )
                _uiState.update { it.copy(nutritionAdvice = advice, isAdviceLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isAdviceLoading = false) }
            }
        }
    }

    // ─── Gestión de Clave Gemini y Prueba de Conexión ─────────────────────────

    fun updateGeminiApiKey(apiKey: String) {
        viewModelScope.launch {
            userPreferences.updateGeminiApiKey(apiKey.trim())
            delay(200)
            requestNutritionAdvice()
        }
    }

    fun updateGeminiModel(model: String) {
        viewModelScope.launch {
            userPreferences.updateGeminiModel(model.trim())
        }
    }

    suspend fun testApiKey(key: String, preferredModel: String? = null): Result<String> {
        return repository.testApiKeyConnection(key, preferredModel ?: _uiState.value.geminiModel)
    }

    fun testApiKeyConnection(key: String, onComplete: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isTestingApiKey = true, apiKeyTestMessage = null, apiKeyTestSuccess = null) }
            val result = repository.testApiKeyConnection(key, _uiState.value.geminiModel)
            if (result.isSuccess) {
                val msg = result.getOrNull() ?: "Conexión exitosa"
                _uiState.update { it.copy(isTestingApiKey = false, apiKeyTestMessage = msg, apiKeyTestSuccess = true) }
                onComplete(true, msg)
            } else {
                val err = result.exceptionOrNull()?.message ?: "Error al conectar con Gemini"
                _uiState.update { it.copy(isTestingApiKey = false, apiKeyTestMessage = err, apiKeyTestSuccess = false) }
                onComplete(false, err)
            }
        }
    }
}
