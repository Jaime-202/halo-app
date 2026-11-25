package com.lifedashboard.presentation.screen.dashboard.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifedashboard.domain.energy.BalanceState
import com.lifedashboard.domain.energy.EnergyCalculator
import com.lifedashboard.domain.score.DayInputs
import com.lifedashboard.domain.score.HaloScores
import com.lifedashboard.domain.score.RecoveryInputs
import com.lifedashboard.presentation.screen.dashboard.DashboardUiState
import com.lifedashboard.presentation.theme.*
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/** Redondeo que no falla con valores no numéricos (NaN) ni infinitos. */
private fun Float.safeRound(): Int = if (isNaN()) 0 else roundToInt()
private fun Float.finiteOr0(): Float = if (isNaN() || isInfinite()) 0f else this

private val mono11 = TextStyle(fontFamily = MonoFamily, fontSize = 11.sp, letterSpacing = 1.2.sp)

// ─── Cálculos de la pantalla ─────────────────────────────────────────────────

data class TodayNumbers(
    val dayScore: Int?,
    val recovery: Int?,
    val intake: Float,
    val expenditure: Float,
    val balance: Float,
    val balanceState: BalanceState,
    val remaining: Float,
)

fun todayNumbers(state: DashboardUiState): TodayNumbers {
    val dayFraction = (LocalTime.now().toSecondOfDay() / 86_400f).coerceIn(0.05f, 1f)
    val expenditure = if (state.bmrKcal > 0f) EnergyCalculator.expenditureForDay(
        state.bmrKcal, state.tdeeKcal, state.todayCalories, dayFraction
    ).finiteOr0() else 0f
    val intake = state.todayMealsCalories.finiteOr0()
    val balance = EnergyCalculator.balance(intake, expenditure)
    val done = state.todayReminders.count { it.isCompleted }
    return TodayNumbers(
        dayScore = HaloScores.dayScore(
            DayInputs(
                steps = state.todaySteps, stepsTarget = state.stepsTarget,
                sleepHours = state.todaySleepHours, sleepTarget = state.sleepTargetHours,
                intakeKcal = intake.takeIf { it > 0f }, intakeTargetKcal = state.intakeTargetKcal,
                remindersDone = done, remindersTotal = state.todayReminders.size,
            )
        ),
        recovery = HaloScores.recovery(
            RecoveryInputs(
                sleepHours = state.todaySleepHours, sleepTarget = state.sleepTargetHours,
                restingHr = state.restingHr.takeIf { it > 0 }, restingHrBaseline = state.restingHrBaseline,
                hrvMs = state.hrvMs.takeIf { it > 0f }, hrvBaseline = state.hrvBaseline,
            )
        ),
        intake = intake,
        expenditure = expenditure,
        balance = balance,
        balanceState = EnergyCalculator.balanceState(balance),
        remaining = (state.intakeTargetKcal - intake).coerceAtLeast(0f),
    )
}

/** Una sola frase útil para hoy, elegida por prioridad. Sin IA: no gasta cuota. */
fun todayInsight(state: DashboardUiState, n: TodayNumbers): String? {
    val hour = LocalTime.now().hour
    n.recovery?.let { r ->
        val level = HaloScores.recoveryLevel(r)
        if (hour < 14) return "Recuperación $r. ${level.advice}."
    }
    val proteinLeft = (state.proteinTargetG - state.todayProteinG).safeRound()
    if (hour >= 14 && proteinLeft > 25 && state.proteinTargetG > 0f) return "Te faltan $proteinLeft g de proteína para tu objetivo."
    if (state.todaySleepHours in 0.5f..(state.sleepTargetHours - 1f)) {
        return "Has dormido ${"%.1f".format(state.todaySleepHours)} h. Intenta acostarte un poco antes hoy."
    }
    val stepsLeft = state.stepsTarget - state.todaySteps
    if (hour >= 17 && stepsLeft in 1..3000) return "Te faltan ${stepsLeft} pasos: un paseo de ${(stepsLeft / 100).coerceAtLeast(5)} minutos y lo tienes."
    if (n.intake > 0f && n.balanceState == BalanceState.DEFICIT) return "Vas en déficit de ${(-n.balance).safeRound()} kcal. Sigue así."
    return null
}

// ─── Cabecera ────────────────────────────────────────────────────────────────

@Composable
fun HaloTodayHeader(userName: String, onProfile: () -> Unit) {
    val date = LocalDate.now().format(DateTimeFormatter.ofPattern("EEE d MMM", Locale("es", "ES"))).uppercase()
    val greeting = when (LocalTime.now().hour) {
        in 6..11 -> "Buenos días"
        in 12..19 -> "Buenas tardes"
        else -> "Buenas noches"
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text(date, color = TextMute, style = mono11)
            Spacer(Modifier.height(2.dp))
            Text("$greeting, $userName", color = TextHigh, style = MaterialTheme.typography.titleLarge)
        }
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Panel)
                .border(1.dp, LineStrong, CircleShape)
                .clickable(role = Role.Button, onClickLabel = "Perfil y ajustes", onClick = onProfile)
                .semantics { contentDescription = "Perfil y ajustes" },
            contentAlignment = Alignment.Center,
        ) {
            val initial = userName.trim().firstOrNull()?.uppercase()
            if (initial != null) {
                Text(initial, color = Ion, style = MaterialTheme.typography.titleMedium)
            } else {
                Icon(Icons.Outlined.Person, contentDescription = "Perfil y ajustes", tint = Ion)
            }
        }
    }
}

// ─── Capa 1 y 2: anillo + balance ────────────────────────────────────────────

@Composable
fun HaloHeroCard(state: DashboardUiState, n: TodayNumbers, onClick: () -> Unit) {
    val shape = RoundedCornerShape(26.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Panel)
            .border(1.dp, Line, shape)
            .clickable(onClick = onClick)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            HaloRings(dayScore = n.dayScore, recovery = n.recovery, modifier = Modifier.size(132.dp))
            Spacer(Modifier.width(18.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("BALANCE HOY", color = TextMute, style = mono11)
                // El número cuenta hasta su valor al abrir
                val shown by androidx.compose.animation.core.animateIntAsState(n.balance.safeRound(), androidx.compose.animation.core.tween(900), label = "balance")
                Text(
                    text = if (n.intake <= 0f) "—" else (if (shown > 0) "+" else "") + shown,
                    style = MaterialTheme.typography.headlineLarge,
                    color = if (n.balanceState == BalanceState.DEFICIT && n.intake > 0f) Ion else TextHigh,
                )
                Text(
                    when {
                        n.intake <= 0f -> "Registra tu primera comida"
                        n.balanceState == BalanceState.DEFICIT -> "kcal · en déficit"
                        n.balanceState == BalanceState.SUPERAVIT -> "kcal · en superávit"
                        else -> "kcal · en mantenimiento"
                    },
                    color = TextMute,
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(6.dp))
                Text("${n.remaining.safeRound()} kcal", color = TextHigh, style = TextStyle(fontFamily = MonoFamily, fontSize = 14.sp))
                Text("te quedan por comer", color = TextMute, style = MaterialTheme.typography.labelSmall)
            }
        }
        val max = maxOf(n.intake, n.expenditure, 1f)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            HaloBar("Comido", n.intake, max, Flux)
            HaloBar("Gastado", n.expenditure, max, Ion)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Legend(Ion, "Halo ${n.dayScore ?: "—"}")
            Legend(Flux, "Recuperación ${n.recovery ?: "—"}")
        }
    }
}

@Composable
private fun Legend(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(text, color = TextMute, style = MaterialTheme.typography.labelSmall)
    }
}

/** Anillo exterior: Halo Score. Interior: recuperación. Se llenan al abrir. */
@Composable
fun HaloRings(dayScore: Int?, recovery: Int?, modifier: Modifier = Modifier) {
    val outer = remember { Animatable(0f) }
    val inner = remember { Animatable(0f) }
    LaunchedEffect(dayScore) { outer.animateTo((dayScore ?: 0) / 100f, tween(1100)) }
    LaunchedEffect(recovery) { inner.animateTo((recovery ?: 0) / 100f, tween(1300)) }

    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val outerStroke = size.minDimension * 0.075f
            val innerStroke = outerStroke * 0.55f
            val gap = outerStroke * 0.9f
            fun ring(inset: Float, stroke: Float, progress: Float, color: Color) {
                val o = Offset(inset + stroke / 2, inset + stroke / 2)
                val s = Size(size.width - 2 * inset - stroke, size.height - 2 * inset - stroke)
                drawArc(Line, 0f, 360f, false, o, s, style = Stroke(stroke))
                if (progress > 0f) drawArc(color, -90f, 360f * progress, false, o, s, style = Stroke(stroke, cap = StrokeCap.Round))
            }
            ring(0f, outerStroke, outer.value, Ion)
            ring(outerStroke + gap, innerStroke, inner.value, Flux)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val shownScore by androidx.compose.animation.core.animateIntAsState(dayScore ?: 0, androidx.compose.animation.core.tween(1100), label = "score")
            Text(if (dayScore == null) "—" else shownScore.toString(), style = MaterialTheme.typography.headlineLarge, color = TextHigh)
            Text("HALO", color = TextMute, style = mono11)
        }
    }
}

@Composable
private fun HaloBar(label: String, value: Float, max: Float, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, color = TextMute, style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(64.dp))
        Box(Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Line)) {
            Box(Modifier.fillMaxWidth((value / max).coerceIn(0f, 1f)).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(color))
        }
        Text(
            value.safeRound().toString(),
            color = TextHigh,
            style = TextStyle(fontFamily = MonoFamily, fontSize = 12.sp),
            modifier = Modifier.width(52.dp),
            textAlign = TextAlign.End,
        )
    }
}

// ─── Capa 3: métricas elegibles ──────────────────────────────────────────────

/** Métricas que se pueden poner en la pantalla Hoy. */
enum class HomeMetric(val key: String, val label: String) {
    PASOS("PASOS", "Pasos"),
    SUENO("SUENO", "Sueño"),
    PROTEINA("PROTEINA", "Proteína"),
    AGUA("AGUA", "Agua"),
    PESO("PESO", "Peso"),
    ACTIVIDAD("ACTIVIDAD", "Actividad semana"),
    KCAL_ACTIVAS("KCAL_ACTIVAS", "Kcal activas"),
    PULSO("PULSO", "Pulso en reposo");

    companion object {
        fun from(key: String) = entries.firstOrNull { it.key == key }
    }
}

private data class TileData(val value: String, val unit: String, val progress: Float?, val color: Color)

private fun tileData(metric: HomeMetric, s: DashboardUiState): TileData = when (metric) {
    HomeMetric.PASOS -> TileData("%,d".format(Locale("es", "ES"), s.todaySteps), "", s.stepsProgress, Ion)
    HomeMetric.SUENO -> {
        val h = s.todaySleepHours.toInt(); val m = ((s.todaySleepHours - h) * 60).safeRound()
        TileData(if (s.todaySleepHours > 0f) "${h}h ${m}" else "—", "", s.sleepProgress, Flux)
    }
    HomeMetric.PROTEINA -> TileData(s.todayProteinG.safeRound().toString(), "g", if (s.proteinTargetG > 0f) (s.todayProteinG / s.proteinTargetG).coerceIn(0f, 1f) else null, Ion)
    HomeMetric.AGUA -> TileData("%.1f".format(Locale("es", "ES"), s.todayWaterMl / 1000f), "L", if (s.waterTargetMl > 0) (s.todayWaterMl.toFloat() / s.waterTargetMl).coerceIn(0f, 1f) else null, Flux)
    HomeMetric.PESO -> TileData(s.latestWeightKg?.let { "%.1f".format(Locale("es", "ES"), it) } ?: "—", "kg", null, Ion)
    HomeMetric.ACTIVIDAD -> TileData(s.weeklyActiveMinutes.toString(), "min", null, Ion)
    HomeMetric.KCAL_ACTIVAS -> TileData(s.todayCalories.safeRound().toString(), "kcal", s.caloriesProgress, Ion)
    HomeMetric.PULSO -> TileData(if (s.restingHr > 0) s.restingHr.toString() else "—", "lpm", null, Flux)
}

@Composable
fun MetricTilesGrid(state: DashboardUiState, onEdit: () -> Unit, onTile: (HomeMetric) -> Unit) {
    val metrics = state.homeMetrics.mapNotNull { HomeMetric.from(it) }.ifEmpty {
        listOf(HomeMetric.PASOS, HomeMetric.SUENO, HomeMetric.PROTEINA, HomeMetric.AGUA)
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("HOY", color = TextMute, style = mono11, modifier = Modifier.weight(1f))
            Text(
                "Editar",
                color = Ion,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onEdit).padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
        metrics.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { metric ->
                    val d = tileData(metric, state)
                    val shape = RoundedCornerShape(16.dp)
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(shape)
                            .background(Panel)
                            .border(1.dp, Line, shape)
                            .clickable { onTile(metric) }
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(metric.label.uppercase(), color = TextMute, style = mono11, maxLines = 1)
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(d.value, color = TextHigh, style = MaterialTheme.typography.titleLarge, maxLines = 1)
                            if (d.unit.isNotEmpty()) {
                                Spacer(Modifier.width(4.dp))
                                Text(d.unit, color = TextMute, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(bottom = 3.dp))
                            }
                        }
                        Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(if (d.progress != null) Line else Color.Transparent)) {
                            if (d.progress != null) Box(Modifier.fillMaxWidth(d.progress).fillMaxHeight().background(d.color))
                        }
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun EditMetricsDialog(current: List<String>, onSave: (List<String>) -> Unit, onDismiss: () -> Unit) {
    var selected by remember { mutableStateOf(current.filter { HomeMetric.from(it) != null }.take(4)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Panel,
        title = { Text("Métricas de Hoy", color = TextHigh) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Elige hasta 4. El orden es el de selección.", color = TextMute, style = MaterialTheme.typography.bodySmall)
                HomeMetric.entries.forEach { m ->
                    val idx = selected.indexOf(m.key)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                selected = when {
                                    idx >= 0 -> selected - m.key
                                    selected.size < 4 -> selected + m.key
                                    else -> selected
                                }
                            }
                            .padding(vertical = 10.dp, horizontal = 6.dp),
                    ) {
                        Box(
                            Modifier.size(22.dp).clip(CircleShape)
                                .background(if (idx >= 0) Ion else Color.Transparent)
                                .border(1.dp, if (idx >= 0) Ion else LineStrong, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (idx >= 0) Text("${idx + 1}", color = Obsidian, style = MaterialTheme.typography.labelSmall)
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(m.label, color = TextHigh, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(selected) }, enabled = selected.isNotEmpty()) { Text("Guardar", color = Ion) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar", color = TextMute) } },
    )
}

// ─── Capa 5: una frase ───────────────────────────────────────────────────────

@Composable
fun InsightLine(text: String) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Box(Modifier.width(2.dp).fillMaxHeight().background(Ion))
        Spacer(Modifier.width(10.dp))
        Text(text, color = TextHigh, style = MaterialTheme.typography.bodyMedium)
    }
}
