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
    val displayWeight = latestWeightKg ?: 75.0f
    val imc = if (userHeightCm > 0) {
        val hM = userHeightCm / 100f
        displayWeight / (hM * hM)
    } else 24.0f

    val imcCategory = when {
        imc < 18.5f -> "Bajo peso" to Cyan400
        imc < 25.0f -> "Saludable" to Emerald400
        imc < 30.0f -> "Sobrepeso" to Amber400
        else        -> "Obesidad" to Rose400
    }

    val deltaTextAndColor: Triple<String, Color, androidx.compose.ui.graphics.vector.ImageVector?> = if (weightHistory.size >= 2) {
        val diff = displayWeight - weightHistory[1].weightKg
        when {
            diff < -0.05f -> Triple(String.format(Locale.US, "-%.1f kg", Math.abs(diff)), Emerald400, Icons.Default.ArrowDownward)
            diff > 0.05f  -> Triple(String.format(Locale.US, "+%.1f kg", diff), Amber400, Icons.Default.ArrowUpward)
            else          -> Triple("0.0 kg", Gray400, Icons.Default.Remove)
        }
    } else if (weightHistory.size == 1) {
        Triple("1er registro", Violet300, null)
    } else {
        Triple("Sin registros", Gray400, null)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        Indigo800,
                        PanelHigh
                    )
                )
            )
            .border(1.dp, Indigo700, RoundedCornerShape(22.dp))
            .padding(18.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // Cabecera
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Violet400.copy(alpha = 0.15f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.FitnessCenter,
                            contentDescription = null,
                            tint = Violet300,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Evolución de Peso",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Historial y tendencia",
                            style = MaterialTheme.typography.labelSmall,
                            color = Gray400,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(Modifier.width(8.dp))

                Button(
                    onClick = onAddWeight,
                    colors = ButtonDefaults.buttonColors(containerColor = Violet400),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = Indigo900, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Pesar", color = Indigo900, fontWeight = FontWeight.Bold, fontSize = 12.sp, softWrap = false, maxLines = 1)
                }
            }

            // Fila central: Peso Actual + Variación + IMC
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Indigo900.copy(alpha = 0.55f))
                    .border(1.dp, Indigo700.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Column {
                    Text(
                        text = "ÚLTIMO PESO",
                        style = MaterialTheme.typography.labelSmall,
                        color = Gray400,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = if (latestWeightKg != null) String.format(Locale.US, "%.1f", displayWeight) else "--",
                            style = MaterialTheme.typography.headlineMedium,
                            color = White,
                            fontWeight = FontWeight.Black
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "kg",
                            style = MaterialTheme.typography.titleMedium,
                            color = Violet300,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 3.dp)
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "VARIACIÓN",
                        style = MaterialTheme.typography.labelSmall,
                        color = Gray400,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        deltaTextAndColor.third?.let { icon ->
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = deltaTextAndColor.second,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(2.dp))
                        }
                        Text(
                            text = deltaTextAndColor.first,
                            style = MaterialTheme.typography.bodyMedium,
                            color = deltaTextAndColor.second,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "IMC ${String.format(Locale.US, "%.1f", imc)} · ${imcCategory.first}",
                        style = MaterialTheme.typography.labelSmall,
                        color = imcCategory.second,
                        fontSize = 11.sp
                    )
                }
            }

            // Gráfico interactivo de evolución de peso
            if (weightHistory.isNotEmpty()) {
                WeightEvolutionChart(
                    weightHistory = weightHistory,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                )
            } else {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Indigo900.copy(alpha = 0.4f))
                        .padding(vertical = 20.dp, horizontal = 16.dp)
                ) {
                    Text(
                        text = "Toca en 'Pesar' para registrar tu primer peso y ver tu gráfica de evolución aquí.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Gray400,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
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
