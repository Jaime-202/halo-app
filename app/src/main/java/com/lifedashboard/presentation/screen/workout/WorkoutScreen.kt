package com.lifedashboard.presentation.screen.workout

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.lifedashboard.core.worker.WorkManagerScheduler
import com.lifedashboard.data.importer.SymmetryCsvImporter
import com.lifedashboard.data.local.dao.WorkoutDao
import com.lifedashboard.data.local.entity.ExerciseSetEntity
import com.lifedashboard.data.local.entity.WorkoutSessionEntity
import com.lifedashboard.data.repository.UserPreferencesRepository
import com.lifedashboard.presentation.theme.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import javax.inject.Inject
import kotlin.math.roundToInt

// ─── Modelo ───────────────────────────────────────────────────────────────────

/** Un día con entreno: sesiones de Health Connect y, si se importaron, ejercicios del CSV. */
data class ActivityDay(
    val date: LocalDate,
    val sessions: List<WorkoutSessionEntity>,      // con minutos (Health Connect)
    val exercises: Map<String, List<ExerciseSetEntity>>, // del CSV de Symmetry
) {
    val minutes: Int get() = sessions.sumOf { it.durationMinutes }
    val kcal: Float get() = sessions.sumOf { it.activeKcal.toDouble() }.toFloat()
}

data class ActivityUiState(
    val days: List<ActivityDay> = emptyList(),          // últimos 56 días, recientes primero
    val weekSessions: Int = 0,
    val weekMinutes: Int = 0,
    val weekKcal: Float = 0f,
    val weekTrainedDates: Set<LocalDate> = emptySet(),
    val weeklyCounts: List<Pair<LocalDate, Int>> = emptyList(), // lunes → sesiones, 8 semanas
    val daysTarget: Int = 4,
    val isSyncing: Boolean = false,
    val isLoading: Boolean = true,
)

@HiltViewModel
class WorkoutViewModel @Inject constructor(
    private val workoutDao: WorkoutDao,
    private val scheduler: WorkManagerScheduler,
    private val userPreferences: UserPreferencesRepository,
    private val importer: SymmetryCsvImporter,
) : ViewModel() {

    private val zone = ZoneId.systemDefault()
    private val _state = MutableStateFlow(ActivityUiState())
    val state: StateFlow<ActivityUiState> = _state.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) { workoutDao.deduplicateSessions() }
        val today = LocalDate.now()
        val monday = today.with(DayOfWeek.MONDAY)
        val since = monday.minusWeeks(7).atStartOfDay(zone).toInstant().toEpochMilli()

        combine(workoutDao.observeSessionsSince(since), userPreferences.userGoals) { sessions, goals ->
            val real = sessions.filter { it.type != "REST" }
            val setsBySession = real.filter { it.sourceFile == "symmetry_csv" }.map { it.id }
                .takeIf { it.isNotEmpty() }?.let { workoutDao.getSetsForSessions(it) }?.groupBy { it.sessionId }
                ?: emptyMap()

            val days = real.groupBy { toDate(it.dateMillis) }.map { (date, list) ->
                val timed = list.filter { it.sourceFile != "symmetry_csv" }
                val csv = list.filter { it.sourceFile == "symmetry_csv" }
                ActivityDay(
                    date = date,
                    sessions = timed.sortedBy { it.startMillis },
                    exercises = csv.flatMap { setsBySession[it.id].orEmpty() }.groupBy { it.exerciseName },
                )
            }.sortedByDescending { it.date }

            val week = days.filter { !it.date.isBefore(monday) }
            val weekly = (0 until 8).map { i ->
                val start = monday.minusWeeks((7 - i).toLong())
                val end = start.plusDays(7)
                start to days.count { !it.date.isBefore(start) && it.date.isBefore(end) }
            }
            ActivityUiState(
                days = days,
                weekSessions = week.sumOf { d -> d.sessions.size.coerceAtLeast(1) },
                weekMinutes = week.sumOf { it.minutes },
                weekKcal = week.sumOf { it.kcal.toDouble() }.toFloat(),
                weekTrainedDates = week.map { it.date }.toSet(),
                weeklyCounts = weekly,
                daysTarget = goals.workoutDaysTarget,
                isLoading = false,
                isSyncing = _state.value.isSyncing,
            )
        }
            .flowOn(Dispatchers.IO)
            .onEach { _state.value = it }
            .launchIn(viewModelScope)
    }

    private fun toDate(millis: Long) = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

    fun triggerSync() {
        viewModelScope.launch {
            _state.update { it.copy(isSyncing = true) }
            scheduler.runHealthSyncNow()
            kotlinx.coroutines.delay(1500)
            _state.update { it.copy(isSyncing = false) }
        }
    }

    fun importCsv(uri: Uri) {
        viewModelScope.launch {
            _message.value = try {
                val n = importer.import(uri)
                if (n == 0) "No se han encontrado entrenos en el archivo. Comprueba que tiene columnas de fecha y ejercicio."
                else "$n días de entreno importados"
            } catch (e: Exception) {
                "No se pudo importar: ${e.message}"
            }
        }
    }

    fun consumeMessage() { _message.value = null }
}

// ─── Pantalla ─────────────────────────────────────────────────────────────────

private val mono11 = TextStyle(fontFamily = MonoFamily, fontSize = 11.sp, letterSpacing = 1.2.sp)
private val es = Locale("es", "ES")

/**
 * Actividad: lee tus entrenos de Symmetry (u otra app) desde Health Connect.
 * No se registra nada a mano. Los días sin entreno cuentan como descanso, no como fallo.
 */
@Composable
fun WorkoutScreen(
    onBack: () -> Unit = {},
    viewModel: WorkoutViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importCsv(uri)
    }
    LaunchedEffect(message) {
        message?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show(); viewModel.consumeMessage() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Obsidian)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 56.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("DESDE HEALTH CONNECT", color = TextMute, style = mono11)
                Text("Actividad", color = TextHigh, style = MaterialTheme.typography.headlineMedium)
            }
            IconButton(onClick = viewModel::triggerSync, enabled = !state.isSyncing) {
                if (state.isSyncing) CircularProgressIndicator(Modifier.size(20.dp), color = Ion, strokeWidth = 2.dp)
                else Icon(Icons.Outlined.Sync, contentDescription = "Sincronizar", tint = Ion)
            }
        }

        WeekCard(state)
        FrequencyCard(state)

        Text("SESIONES", color = TextMute, style = mono11)
        if (state.days.isEmpty() && !state.isLoading) {
            EmptyCard()
        } else {
            state.days.take(20).forEach { DayCard(it) }
        }

        ImportCard(onImport = { csvLauncher.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain", "*/*")) })
    }
}

@Composable
private fun Panel(content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(Panel).border(1.dp, Line, shape).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        content = content,
    )
}

@Composable
private fun WeekCard(state: ActivityUiState) {
    val today = LocalDate.now()
    val monday = today.with(DayOfWeek.MONDAY)
    Panel {
        Text("ESTA SEMANA", color = TextMute, style = mono11)
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Stat("${state.weekTrainedDates.size}/${state.daysTarget}", "días")
            Stat("${state.weekMinutes}", "min")
            if (state.weekKcal > 0f) Stat("${state.weekKcal.roundToInt()}", "kcal")
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            (0..6).forEach { i ->
                val d = monday.plusDays(i.toLong())
                val trained = d in state.weekTrainedDates
                val past = d.isBefore(today)
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(d.dayOfWeek.getDisplayName(java.time.format.TextStyle.NARROW, es).uppercase(), color = if (d == today) TextHigh else TextMute, style = mono11)
                    Box(
                        Modifier.size(28.dp).clip(CircleShape)
                            .background(if (trained) Ion else Color.Transparent)
                            .border(1.dp, when { trained -> Ion; d == today -> TextHigh; else -> LineStrong }, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (!trained && past) Box(Modifier.size(4.dp).clip(CircleShape).background(TextDim))
                    }
                }
            }
        }
        val rest = (0..6).count { i -> val d = monday.plusDays(i.toLong()); d.isBefore(today) && d !in state.weekTrainedDates }
        Text(
            if (rest > 0) "$rest días de descanso: también son parte del plan." else "Sin días de descanso todavía esta semana.",
            color = TextMute,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun Stat(value: String, unit: String) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(value, color = TextHigh, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.width(4.dp))
        Text(unit, color = TextMute, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(bottom = 4.dp))
    }
}

@Composable
private fun FrequencyCard(state: ActivityUiState) {
    if (state.weeklyCounts.isEmpty()) return
    val max = maxOf(state.weeklyCounts.maxOf { it.second }, state.daysTarget, 1)
    val avg = state.weeklyCounts.dropLast(1).map { it.second }.average().takeIf { !it.isNaN() } ?: 0.0
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("ÚLTIMAS 8 SEMANAS", color = TextMute, style = mono11, modifier = Modifier.weight(1f))
            Text("media ${"%.1f".format(es, avg)} días/sem", color = TextMute, style = MaterialTheme.typography.labelSmall)
        }
        Row(Modifier.fillMaxWidth().height(90.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
            state.weeklyCounts.forEachIndexed { i, (_, count) ->
                val current = i == state.weeklyCounts.lastIndex
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$count", color = if (current) Ion else TextMute, style = mono11)
                    Spacer(Modifier.height(4.dp))
                    Box(
                        Modifier.fillMaxWidth().height((60f * count / max).coerceAtLeast(3f).dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (current) Ion else Ion.copy(alpha = if (count >= state.daysTarget) 0.6f else 0.3f))
                    )
                }
            }
        }
    }
}

@Composable
private fun DayCard(day: ActivityDay) {
    var expanded by remember { mutableStateOf(false) }
    val fmt = DateTimeFormatter.ofPattern("EEEE d MMM", es)
    val time = DateTimeFormatter.ofPattern("HH:mm")
    val zone = ZoneId.systemDefault()
    val daysAgo = ChronoUnit.DAYS.between(day.date, LocalDate.now())
    val title = when (daysAgo) { 0L -> "Hoy"; 1L -> "Ayer"; else -> day.date.format(fmt).replaceFirstChar { it.uppercase() } }
    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(Panel).border(1.dp, Line, shape)
            .clickable(enabled = day.exercises.isNotEmpty()) { expanded = !expanded }
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = TextHigh, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (day.minutes > 0) Text("${day.minutes} min", color = Ion, style = TextStyle(fontFamily = MonoFamily, fontSize = 13.sp))
        }
        day.sessions.forEach { s ->
            val parts = buildList {
                if (s.startMillis > 0) add(Instant.ofEpochMilli(s.startMillis).atZone(zone).format(time) +
                    if (s.endMillis > 0) "–" + Instant.ofEpochMilli(s.endMillis).atZone(zone).format(time) else "")
                add(if (s.type == "CARDIO") "Cardio" else "Fuerza")
                if (s.activeKcal > 0f) add("${s.activeKcal.roundToInt()} kcal")
                if (s.avgHeartRate > 0) add("${s.avgHeartRate} lpm media" + if (s.maxHeartRate > 0) " · máx ${s.maxHeartRate}" else "")
            }
            Text(parts.joinToString(" · "), color = TextMute, style = MaterialTheme.typography.bodySmall)
            s.notes?.takeIf { it.isNotBlank() }?.let { Text(it, color = TextDim, style = MaterialTheme.typography.labelSmall) }
        }
        if (day.exercises.isNotEmpty()) {
            Text(
                "${day.exercises.size} ejercicios · ${day.exercises.values.sumOf { it.size }} series" + if (expanded) "" else " · ver detalle",
                color = Flux,
                style = MaterialTheme.typography.labelMedium,
            )
            AnimatedVisibility(expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    day.exercises.forEach { (name, sets) ->
                        Row {
                            Text(name, color = TextHigh, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            Text(
                                sets.sortedBy { it.setNumber }.joinToString("  ") { "${fmtKg(it.weightKg)}×${it.reps}" },
                                color = TextMute,
                                style = TextStyle(fontFamily = MonoFamily, fontSize = 11.sp),
                                textAlign = TextAlign.End,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun fmtKg(kg: Float) = if (kg % 1f == 0f) kg.roundToInt().toString() else "%.1f".format(es, kg)

@Composable
private fun EmptyCard() {
    Panel {
        Text("Aún no hay entrenos", color = TextHigh, style = MaterialTheme.typography.titleMedium)
        Text(
            "En Symmetry, activa la conexión con Health Connect. Halo leerá cada sesión con su duración, " +
                "y las calorías y el pulso si tu reloj los registra. Comprueba también en Ajustes que Halo tiene permiso de leer ejercicio.",
            color = TextMute,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun ImportCard(onImport: () -> Unit) {
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Ejercicios, series y pesos", color = TextHigh, style = MaterialTheme.typography.titleSmall)
                Text(
                    "Opcional: si Symmetry te deja exportar el historial en CSV, impórtalo para ver el detalle de cada día. Reimportar no duplica.",
                    color = TextMute,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Spacer(Modifier.width(12.dp))
            OutlinedButton(
                onClick = onImport,
                border = androidx.compose.foundation.BorderStroke(1.dp, Ion),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Ion),
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(Icons.Outlined.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("CSV")
            }
        }
    }
}
