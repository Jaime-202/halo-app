package com.lifedashboard.presentation.screen.dashboard

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.window.Dialog
import com.lifedashboard.data.local.entity.WeightEntryEntity
import com.lifedashboard.presentation.common.components.WeightEvolutionChart
import com.lifedashboard.domain.model.CalendarEvent
import com.lifedashboard.domain.model.WorkoutType
import com.lifedashboard.presentation.screen.dashboard.components.*
import com.lifedashboard.presentation.theme.*
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = hiltViewModel(),
    onNavigateToHealth: (String) -> Unit = {},
    onNavigateToWorkout: () -> Unit = {},
    onNavigateToNutrition: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showAddWeightModal by remember { mutableStateOf(false) }
    var showEditMetrics by remember { mutableStateOf(false) }
    val haptics = rememberHaloHaptics()
    val context = androidx.compose.ui.platform.LocalContext.current

    DashboardContent(
        state                 = state,
        onSelectDate          = { viewModel.selectDate(it) },
        onAddWeightClick      = { showAddWeightModal = true },
        onToggleReminder      = { id, completed ->
            if (completed) haptics.confirm() else haptics.tick()
            viewModel.toggleReminder(id, completed)
        },
        onNavigateToHealth    = onNavigateToHealth,
        onNavigateToWorkout   = onNavigateToWorkout,
        onNavigateToNutrition = onNavigateToNutrition,
        onNavigateToSettings  = onNavigateToSettings,
        onEditMetrics         = { showEditMetrics = true },
        isRefreshing          = state.isRefreshing,
        onRefresh             = { viewModel.refresh() },
        onSaveMood            = { m, e ->
            haptics.confirm()
            viewModel.saveMood(m, e)
        },
        onAddWater            = {
            viewModel.addWater(250)
            android.widget.Toast.makeText(context, "+250 ml de agua", android.widget.Toast.LENGTH_SHORT).show()
        }
    )

    if (showEditMetrics) {
        EditMetricsDialog(
            current = state.homeMetrics,
            onSave = {
                viewModel.updateHomeMetrics(it)
                showEditMetrics = false
            },
            onDismiss = { showEditMetrics = false }
        )
    }

    if (showAddWeightModal) {
        DashboardAddWeightModal(
            currentWeight = state.latestWeightKg ?: 75.0f,
            onDismiss     = { showAddWeightModal = false },
            onSave        = { weight, note ->
                viewModel.addWeightEntry(weight, note)
                showAddWeightModal = false
            }
        )
    }
}

@Composable
fun DashboardContent(
    state: DashboardUiState,
    onSelectDate: (LocalDate) -> Unit = {},
    onRefreshAi: () -> Unit = {},
    onAddWeightClick: () -> Unit = {},
    onToggleReminder: (Long, Boolean) -> Unit = { _, _ -> },
    onNavigateToHealth: (String) -> Unit = {},
    onNavigateToWorkout: () -> Unit = {},
    onNavigateToNutrition: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onEditMetrics: () -> Unit = {},
    onAddWater: () -> Unit = {},
    onSaveMood: (Int, Int) -> Unit = { _, _ -> },
    isRefreshing: Boolean = false,
    onRefresh: () -> Unit = {}
) {
    val scrollState = rememberScrollState()
    val greeting = remember(state.userName) {
        val hour = LocalDateTime.now().hour
        val timeGreeting = when {
            hour < 12 -> "Buenos días"
            hour < 20 -> "Buenas tardes"
            else      -> "Buenas noches"
        }
        "$timeGreeting, ${state.userName}"
    }

    // Tirar hacia abajo para sincronizar con Health Connect
    @OptIn(ExperimentalMaterial3Api::class)
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = Modifier
            .fillMaxSize()
            .background(Obsidian)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp)
                .padding(top = 56.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // ── Hoy, por capas: de lo más importante a lo menos ──
            val numbers = todayNumbers(state)
            HaloTodayHeader(userName = state.userName, onProfile = onNavigateToSettings)

            // 1–2. Halo Score + recuperación, balance y comido frente a gastado
            if (state.isDataLoading) {
                com.lifedashboard.presentation.common.components.SkeletonBlock(250.dp, radius = 26.dp)
                com.lifedashboard.presentation.common.components.SkeletonBlock(170.dp)
            } else {
                HaloHeroCard(state = state, n = numbers, onClick = onNavigateToNutrition)
            }

            // 3. Cuatro métricas elegidas por ti
            if (!state.isDataLoading) MetricTilesGrid(
                state = state,
                onEdit = onEditMetrics,
                onTile = { metric ->
                    when (metric) {
                        HomeMetric.PASOS -> onNavigateToHealth("PASOS")
                        HomeMetric.SUENO, HomeMetric.PULSO -> onNavigateToHealth("SUENO")
                        HomeMetric.KCAL_ACTIVAS -> onNavigateToHealth("CALORIAS")
                        HomeMetric.PROTEINA -> onNavigateToNutrition()
                        HomeMetric.AGUA -> onAddWater()
                        HomeMetric.PESO -> onAddWeightClick()
                        HomeMetric.ACTIVIDAD -> onNavigateToWorkout()
                    }
                }
            )

            // 4. Recordatorios del día
            TodayRemindersCard(
                reminders            = state.todayReminders,
                selectedDate         = state.selectedDate,
                onToggleReminder     = onToggleReminder,
                onNavigateToSettings = onNavigateToSettings
            )

            // Ánimo y energía: por la tarde, o si ya está registrado
            if (state.todayMood != null || java.time.LocalTime.now().hour >= 17) {
                com.lifedashboard.presentation.common.components.MoodCheckInCard(
                    mood = state.todayMood,
                    energy = state.todayEnergy,
                    onSave = onSaveMood
                )
            }

            // 5. Una sola frase
            todayInsight(state, numbers)?.let { InsightLine(it) }

            // ── Debajo del pliegue: detalle ──
            // Tarjeta de Evolución y Seguimiento de Peso en el Panel
            DashboardWeightCard(
                latestWeightKg = state.latestWeightKg,
                userHeightCm   = state.userHeightCm,
                weightHistory  = state.weightHistory,
                onAddWeight    = onAddWeightClick
            )

            // 5. Próximos Eventos - controlado por preferencia
            if (state.showUpcomingEvents) {
                SectionTitle("Próximos Eventos")
                UpcomingEventsList(events = state.upcomingEvents)
            }

        }
    }
}

@Composable
private fun UpcomingEventsList(events: List<CalendarEvent>) {
    if (events.isEmpty()) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Indigo800)
                .border(1.dp, Indigo700, RoundedCornerShape(18.dp))
                .padding(vertical = 24.dp, horizontal = 16.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector        = Icons.Default.EventAvailable,
                    contentDescription = null,
                    tint               = Violet300,
                    modifier           = Modifier.size(28.dp)
                )
                Text(
                    text  = "No hay eventos próximos",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Gray300,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text  = "Tu agenda está despejada para el resto del día",
                    style = MaterialTheme.typography.bodySmall,
                    color = Gray400
                )
            }
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            events.forEach { event ->
                EventChip(event = event)
            }
        }
    }
}

@Composable
private fun EventChip(event: CalendarEvent) {
    val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    val dateFormatter = DateTimeFormatter.ofPattern("EEE d", Locale("es"))

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Indigo800)
            .border(1.dp, Indigo700, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(Color(event.calendarColor))
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text     = event.title,
                style    = MaterialTheme.typography.labelLarge,
                color    = White,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text  = "${event.startTime.format(dateFormatter)} · ${event.startTime.format(timeFormatter)}",
                style = MaterialTheme.typography.bodySmall,
                color = Gray400
            )
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text       = title,
        style      = MaterialTheme.typography.titleMedium,
        color      = White,
        fontWeight = FontWeight.Bold
    )
}

// ─── Tarjeta de Evolución y Seguimiento de Peso en el Panel ────────────────────

@Composable
private fun DashboardWeightCard(
    latestWeightKg: Float?,
    userHeightCm: Int,
    weightHistory: List<WeightEntryEntity>,
    onAddWeight: () -> Unit
) {
    val es = Locale("es", "ES")
    val sorted = weightHistory.sortedBy { it.dateMillis }
    val last = latestWeightKg
    val monthAgo = System.currentTimeMillis() - 30L * 86_400_000L
    val base = sorted.firstOrNull { it.dateMillis >= monthAgo }?.weightKg
    val delta = if (last != null && base != null && sorted.size >= 2) last - base else null
    val imc = if (last != null && userHeightCm > 0) last / ((userHeightCm / 100f) * (userHeightCm / 100f)) else null
    val imcLabel = imc?.let { when { it < 18.5f -> "Bajo peso"; it < 25f -> "Saludable"; it < 30f -> "Sobrepeso"; else -> "Obesidad" } }
    val shape = RoundedCornerShape(22.dp)

    Column(
        Modifier.fillMaxWidth().clip(shape).background(Panel).border(1.dp, Line, shape).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("PESO", color = TextMute, style = androidx.compose.ui.text.TextStyle(fontFamily = MonoFamily, fontSize = 11.sp, letterSpacing = 1.2.sp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(last?.let { "%.1f".format(es, it) } ?: "—", color = TextHigh, style = MaterialTheme.typography.headlineMedium)
                    Text(" kg", color = TextMute, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 5.dp))
                    delta?.let {
                        Spacer(Modifier.width(10.dp))
                        Text(
                            (if (it > 0) "+" else "") + "%.1f".format(es, it) + " en 30 días",
                            color = if (it <= 0f) Ion else TextMute, style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(bottom = 6.dp),
                        )
                    }
                }
                if (imc != null) Text("IMC %.1f · %s".format(es, imc, imcLabel), color = TextMute, style = MaterialTheme.typography.labelSmall)
            }
            Button(
                onClick = onAddWeight,
                colors = ButtonDefaults.buttonColors(containerColor = Ion, contentColor = OnAccent),
                shape = RoundedCornerShape(14.dp),
            ) { Text("Pesarme") }
        }
        // Línea de los últimos pesajes
        val pts = sorted.takeLast(30)
        if (pts.size >= 2) {
            val line = Ion
            Canvas(Modifier.fillMaxWidth().height(56.dp)) {
                val min = pts.minOf { it.weightKg }; val max = pts.maxOf { it.weightKg }
                val span = (max - min).coerceAtLeast(0.5f)
                val t0 = pts.first().dateMillis.toFloat(); val t1 = pts.last().dateMillis.toFloat()
                val dt = (t1 - t0).coerceAtLeast(1f)
                val path = androidx.compose.ui.graphics.Path()
                pts.forEachIndexed { i, p ->
                    val x = (p.dateMillis - t0) / dt * size.width
                    val y = size.height - (p.weightKg - min) / span * (size.height - 8f) - 4f
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, line, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()))
                val lp = pts.last()
                drawCircle(line, 3.5.dp.toPx(), androidx.compose.ui.geometry.Offset(size.width, size.height - (lp.weightKg - min) / span * (size.height - 8f) - 4f))
            }
        } else {
            Text("Pésate un par de veces y aquí verás tu evolución.", color = TextMute, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun DashboardAddWeightModal(
    currentWeight: Float,
    onDismiss: () -> Unit,
    onSave: (Float, String?) -> Unit
) {
    var weightInput by remember { mutableStateOf(String.format(Locale.US, "%.1f", currentWeight)) }
    var noteInput by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Indigo800)
                .border(1.dp, Violet400.copy(alpha = 0.4f), RoundedCornerShape(24.dp))
                .padding(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.FitnessCenter, contentDescription = null, tint = Violet300, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Registrar Pesaje", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = White)
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Gray400, modifier = Modifier.size(18.dp))
                    }
                }

                Text(
                    text = "Introduce tu peso actual para actualizar tu progreso e IMC.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Gray300
                )

                OutlinedTextField(
                    value = weightInput,
                    onValueChange = { weightInput = it },
                    label = { Text("Peso (kg)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Violet400,
                        unfocusedBorderColor = Indigo600,
                        focusedTextColor = White,
                        unfocusedTextColor = White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = noteInput,
                    onValueChange = { noteInput = it },
                    label = { Text("Nota opcional (ej: en ayunas)") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Violet400,
                        unfocusedBorderColor = Indigo600,
                        focusedTextColor = White,
                        unfocusedTextColor = White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = onDismiss,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Gray300),
                        border = BorderStroke(1.dp, Indigo600),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancelar")
                    }
                    Button(
                        onClick = {
                            val w = weightInput.replace(',', '.').toFloatOrNull()
                            if (w != null && w in 30f..300f) {
                                onSave(w, noteInput.ifBlank { null })
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Violet400),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Guardar", color = Indigo900, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}


// ─── Balance de energía ───────────────────────────────────────────────────────

/**
 * Comido frente a gastado hoy. El gasto es el basal proporcional a la hora del día
 * más las calorías activas de Health Connect (o el TDEE estimado si no hay datos).
 */
