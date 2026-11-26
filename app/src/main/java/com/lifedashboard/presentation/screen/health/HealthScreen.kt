package com.lifedashboard.presentation.screen.health

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.lifedashboard.core.worker.WorkManagerScheduler
import com.lifedashboard.data.local.dao.HealthDao
import com.lifedashboard.data.local.entity.HealthDataEntity
import com.lifedashboard.data.repository.UserPreferencesRepository
import com.lifedashboard.presentation.theme.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

enum class HealthTab { PASOS, SUENO, CALORIAS }

// ─── ViewModel ────────────────────────────────────────────────────────────────

data class HealthUiState(
    val last14Days: List<HealthDataEntity> = emptyList(),
    val stepsTarget: Int = 10000,
    val sleepTarget: Float = 8.0f,
    val caloriesTarget: Int = 500, // Meta de calorías activas quemadas
    val bmrKcal: Int = 1650,       // Metabolismo basal según tu perfil
    val isSyncing: Boolean = false
)

@HiltViewModel
class HealthViewModel @Inject constructor(
    private val healthDao: HealthDao,
    private val userPreferences: UserPreferencesRepository,
    private val scheduler: WorkManagerScheduler
) : ViewModel() {

    private val _uiState = MutableStateFlow(HealthUiState())
    val uiState: StateFlow<HealthUiState> = _uiState.asStateFlow()

    init {
        healthDao.observeLastNDays(14)
            .onEach { list ->
                _uiState.update { it.copy(last14Days = list) }
            }
            .flowOn(Dispatchers.IO)
            .launchIn(viewModelScope)

        userPreferences.userGoals
            .onEach { goals ->
                _uiState.update {
                    it.copy(
                        stepsTarget    = goals.stepsTarget,
                        sleepTarget    = goals.sleepTargetHours,
                        caloriesTarget = goals.activeCaloriesTarget.toInt(),
                        bmrKcal = com.lifedashboard.domain.energy.EnergyCalculator.bmr(
                            com.lifedashboard.domain.energy.Sex.from(goals.sex),
                            goals.weightKg, goals.heightCm, goals.ageYears
                        ).toInt()
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    fun triggerSync() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncing = true) }
            scheduler.runHealthSyncNow()
            kotlinx.coroutines.delay(1200)
            _uiState.update { it.copy(isSyncing = false) }
        }
    }
}

// ─── Pantalla ─────────────────────────────────────────────────────────────────

private val es = java.util.Locale("es", "ES")
private val mono11 = androidx.compose.ui.text.TextStyle(fontFamily = MonoFamily, fontSize = 11.sp, letterSpacing = 1.2.sp)

/**
 * Salud: pasos, sueño y energía de Health Connect. Para cada uno, el dato de hoy
 * frente al objetivo, los últimos 14 días y unas cifras de contexto.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HealthScreen(
    initialTabName: String = "PASOS",
    onBack: () -> Unit = {},
    viewModel: HealthViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    fun parse(n: String) = when (n.uppercase()) {
        "SUENO", "SUEÑO" -> HealthTab.SUENO
        "CALORIAS", "CALORÍAS" -> HealthTab.CALORIAS
        else -> HealthTab.PASOS
    }
    var tab by remember(initialTabName) { mutableStateOf(parse(initialTabName)) }
    val days = remember(state.last14Days) { state.last14Days.sortedBy { it.dateMillis } }
    val today = days.lastOrNull()

    androidx.compose.material3.pulltorefresh.PullToRefreshBox(
        isRefreshing = state.isSyncing,
        onRefresh = viewModel::triggerSync,
        modifier = Modifier.fillMaxSize().background(Obsidian),
    ) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(top = 44.dp, bottom = 110.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.offset(x = (-12).dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver", tint = TextHigh)
                }
                Column(Modifier.weight(1f)) {
                    Text("DESDE HEALTH CONNECT", color = TextMute, style = mono11)
                    Text("Salud", color = TextHigh, style = MaterialTheme.typography.headlineMedium)
                }
            }

            // Selector
            val shape = RoundedCornerShape(14.dp)
            Row(Modifier.fillMaxWidth().clip(shape).border(1.dp, LineStrong, shape).padding(3.dp)) {
                listOf(HealthTab.PASOS to "Pasos", HealthTab.SUENO to "Sueño", HealthTab.CALORIAS to "Energía").forEach { (t, label) ->
                    val on = t == tab
                    Box(
                        Modifier.weight(1f).clip(RoundedCornerShape(11.dp)).background(if (on) PanelHigh else Color.Transparent)
                            .clickable { tab = t }.padding(vertical = 9.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text(label, color = if (on) TextHigh else TextMute, style = MaterialTheme.typography.labelLarge) }
                }
            }

            AnimatedContent(
                targetState = tab,
                transitionSpec = { fadeIn(tween(HaloMotion.MEDIUM)) togetherWith fadeOut(tween(HaloMotion.FAST)) },
                label = "healthTab",
            ) { t ->
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    when (t) {
                        HealthTab.PASOS -> {
                            Hero("Hoy", "%,d".format(es, today?.steps ?: 0), "pasos", (today?.steps ?: 0).toFloat() / state.stepsTarget.coerceAtLeast(1), "Objetivo %,d".format(es, state.stepsTarget), Ion)
                            Bars(days, { it.steps.toFloat() }, state.stepsTarget.toFloat(), Ion) { "%,d".format(es, it.toInt()) }
                            Facts(listOf(
                                "Distancia hoy" to "%.1f km".format(es, (today?.distanceMeters ?: 0f) / 1000f),
                                "Media 14 días" to "%,d".format(es, days.map { it.steps }.average().takeIf { !it.isNaN() }?.toInt() ?: 0),
                                "Mejor día" to "%,d".format(es, days.maxOfOrNull { it.steps } ?: 0),
                                "Días en objetivo" to "${days.count { it.steps >= state.stepsTarget }} / ${days.size}",
                            ))
                        }
                        HealthTab.SUENO -> {
                            val h = today?.sleepHours ?: 0f
                            Hero("Anoche", if (h > 0f) "%dh %02d".format(h.toInt(), ((h - h.toInt()) * 60).toInt()) else "—", "", h / state.sleepTarget.coerceAtLeast(1f), "Objetivo %.0f h".format(es, state.sleepTarget), Flux)
                            Bars(days, { it.sleepHours }, state.sleepTarget, Flux) { "%.1f h".format(es, it) }
                            Facts(listOfNotNull(
                                ("Sueño profundo" to "%.1f h".format(es, today?.sleepDeepHours ?: 0f)).takeIf { (today?.sleepDeepHours ?: 0f) > 0f },
                                ("Sueño REM" to "%.1f h".format(es, today?.sleepRemHours ?: 0f)).takeIf { (today?.sleepRemHours ?: 0f) > 0f },
                                ("Pulso en reposo" to "${today?.restingHeartRate} lpm").takeIf { (today?.restingHeartRate ?: 0) > 0 },
                                ("Variabilidad cardiaca" to "%.0f ms".format(es, today?.hrvMs ?: 0f)).takeIf { (today?.hrvMs ?: 0f) > 0f },
                                "Media 14 días" to "%.1f h".format(es, days.map { it.sleepHours }.filter { it > 0f }.average().takeIf { !it.isNaN() } ?: 0.0),
                                "Noches en objetivo" to "${days.count { it.sleepHours >= state.sleepTarget - 0.25f }} / ${days.size}",
                            ))
                        }
                        HealthTab.CALORIAS -> {
                            val active = today?.activeCalories ?: 0f
                            Hero("Hoy en movimiento", "%,d".format(es, active.toInt()), "kcal", active / state.caloriesTarget.coerceAtLeast(1), "Objetivo %,d kcal".format(es, state.caloriesTarget), Ember)
                            Bars(days, { it.activeCalories }, state.caloriesTarget.toFloat(), Ember) { "%,d".format(es, it.toInt()) }
                            Facts(listOf(
                                "Metabolismo basal" to "%,d kcal".format(es, state.bmrKcal),
                                "Gasto total hoy (aprox.)" to "%,d kcal".format(es, state.bmrKcal + active.toInt()),
                                "Media 14 días en movimiento" to "%,d kcal".format(es, days.map { it.activeCalories }.average().takeIf { !it.isNaN() }?.toInt() ?: 0),
                            ))
                        }
                    }
                }
            }
            if (days.isEmpty()) {
                Text("Sin datos todavía. Conecta Health Connect en Ajustes → Conexiones y tira hacia abajo para sincronizar.", color = TextMute, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun Hero(label: String, value: String, unit: String, progress: Float, target: String, color: Color) {
    val shape = RoundedCornerShape(26.dp)
    val p = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(progress) { p.animateTo(progress.coerceIn(0f, 1f).let { if (it.isNaN()) 0f else it }, HaloMotion.fill()) }
    Row(
        Modifier.fillMaxWidth().clip(shape).background(Panel).border(1.dp, Line, shape).padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label.uppercase(), color = TextMute, style = mono11)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, color = TextHigh, style = MaterialTheme.typography.displaySmall)
                if (unit.isNotEmpty()) Text(" $unit", color = TextMute, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 8.dp))
            }
            Text(target, color = TextMute, style = MaterialTheme.typography.labelMedium)
        }
        val track = Line
        Box(contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(84.dp)) {
                val st = 9.dp.toPx()
                val o = androidx.compose.ui.geometry.Offset(st / 2, st / 2)
                val sz = androidx.compose.ui.geometry.Size(size.width - st, size.height - st)
                drawArc(track, 0f, 360f, false, o, sz, style = androidx.compose.ui.graphics.drawscope.Stroke(st))
                drawArc(color, -90f, 360f * p.value, false, o, sz, style = androidx.compose.ui.graphics.drawscope.Stroke(st, cap = androidx.compose.ui.graphics.StrokeCap.Round))
            }
            Text("${(progress.coerceIn(0f, 9.99f) * 100).toInt().coerceAtLeast(0)}%", color = TextHigh, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun Bars(days: List<HealthDataEntity>, value: (HealthDataEntity) -> Float, target: Float, color: Color, fmt: (Float) -> String) {
    val shape = RoundedCornerShape(22.dp)
    val max = (days.maxOfOrNull(value) ?: 0f).coerceAtLeast(target) * 1.1f
    var selected by remember(days) { mutableStateOf(days.lastOrNull()) }
    Column(
        Modifier.fillMaxWidth().clip(shape).background(Panel).border(1.dp, Line, shape).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row {
            Text("ÚLTIMOS 14 DÍAS", color = TextMute, style = mono11, modifier = Modifier.weight(1f))
            selected?.let {
                val d = java.time.Instant.ofEpochMilli(it.dateMillis).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                Text("${d.format(java.time.format.DateTimeFormatter.ofPattern("EEE d", es))} · ${fmt(value(it))}", color = TextHigh, style = MaterialTheme.typography.labelMedium)
            }
        }
        Box(Modifier.fillMaxWidth().height(120.dp)) {
            val dash = TextDim
            Canvas(Modifier.fillMaxSize()) {
                if (max > 0f) {
                    val y = size.height * (1f - target / max)
                    drawLine(dash, androidx.compose.ui.geometry.Offset(0f, y), androidx.compose.ui.geometry.Offset(size.width, y), 1.dp.toPx(),
                        pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))
                }
            }
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
                days.forEach { d ->
                    val v = value(d)
                    val on = d == selected
                    Box(
                        Modifier.weight(1f).fillMaxHeight(if (max > 0f) (v / max).coerceIn(0.02f, 1f) else 0.02f)
                            .clip(RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp, bottomStart = 2.dp, bottomEnd = 2.dp))
                            .background(if (on) color else color.copy(alpha = if (v >= target) 0.65f else 0.3f))
                            .clickable { selected = d }
                    )
                }
            }
        }
    }
}

@Composable
private fun Facts(rows: List<Pair<String, String>>) {
    val shape = RoundedCornerShape(22.dp)
    Column(Modifier.fillMaxWidth().clip(shape).background(Panel).border(1.dp, Line, shape).padding(horizontal = 16.dp, vertical = 6.dp)) {
        rows.forEachIndexed { i, (k, v) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                Text(k, color = TextMute, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text(v, color = TextHigh, style = androidx.compose.ui.text.TextStyle(fontFamily = MonoFamily, fontSize = 14.sp))
            }
            if (i < rows.lastIndex) HorizontalDivider(color = Line)
        }
    }
}
