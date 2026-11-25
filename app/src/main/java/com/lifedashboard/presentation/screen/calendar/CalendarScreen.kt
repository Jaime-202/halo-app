package com.lifedashboard.presentation.screen.calendar

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.lifedashboard.data.repository.HistoryRepository
import com.lifedashboard.data.repository.UserPreferencesRepository
import com.lifedashboard.domain.history.CalendarMetric
import com.lifedashboard.domain.history.DaySummary
import com.lifedashboard.domain.history.HistoryMath
import com.lifedashboard.domain.history.StreakInfo
import com.lifedashboard.presentation.theme.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as JTextStyle
import java.util.Locale
import javax.inject.Inject
import kotlin.math.roundToInt

enum class CalendarView { SEMANA, MES, AÑO }

data class CalendarUiState(
    val days: Map<LocalDate, DaySummary> = emptyMap(),
    val stepsTarget: Int = 10000,
    val sleepTarget: Float = 8f,
    val intakeTarget: Float = 2200f,
    val streak: StreakInfo = StreakInfo(0, 0, false),
    val insights: List<com.lifedashboard.domain.insights.Insight> = emptyList(),
)

@HiltViewModel
class CalendarViewModel @Inject constructor(
    history: HistoryRepository,
    prefs: UserPreferencesRepository,
) : ViewModel() {
    val state: StateFlow<CalendarUiState> = combine(
        history.observe(LocalDate.now().minusDays(400), LocalDate.now()),
        prefs.userGoals,
    ) { days, g ->
        CalendarUiState(
            days = days,
            stepsTarget = g.stepsTarget,
            sleepTarget = g.sleepTargetHours,
            intakeTarget = g.caloriesTarget,
            streak = HistoryMath.streak(days, LocalDate.now()),
            insights = com.lifedashboard.domain.insights.InsightEngine.analyze(
                days.filterKeys { it.isAfter(LocalDate.now().minusDays(90)) }, g.sleepTargetHours, g.stepsTarget
            ),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CalendarUiState())
}

private val es = Locale("es", "ES")
private val mono11 = TextStyle(fontFamily = MonoFamily, fontSize = 11.sp, letterSpacing = 1.2.sp)

/**
 * Calendario: cómo lo has ido haciendo por semana, mes y año.
 * Cada día se colorea según el filtro elegido (Halo, kcal, pasos, sueño, actividad o recordatorios).
 */
@Composable
fun CalendarScreen(viewModel: CalendarViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var view by rememberSaveable { mutableStateOf(CalendarView.MES) }
    var metric by rememberSaveable { mutableStateOf(CalendarMetric.HALO) }
    var anchor by remember { mutableStateOf(LocalDate.now()) }
    var selected by remember { mutableStateOf<LocalDate?>(LocalDate.now()) }

    Column(
        Modifier.fillMaxSize().background(Obsidian).verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp).padding(top = 56.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("TU HISTORIAL", color = TextMute, style = mono11)
                Text("Calendario", color = TextHigh, style = MaterialTheme.typography.headlineMedium)
            }
            StreakBadge(state.streak)
        }

        Segmented(CalendarView.entries, view, { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } }) {
            view = it; anchor = LocalDate.now()
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CalendarMetric.entries.forEach { m -> Chip(m.label, m == metric) { metric = m } }
        }

        when (view) {
            CalendarView.SEMANA -> WeekView(state, metric, anchor, onShift = { anchor = anchor.plusWeeks(it.toLong()) }, onSelect = { selected = it })
            CalendarView.MES -> MonthView(state, metric, YearMonth.from(anchor), selected, onShift = { anchor = anchor.plusMonths(it.toLong()) }, onSelect = { selected = it })
            CalendarView.AÑO -> YearView(state, metric, anchor.year, onShift = { anchor = anchor.plusYears(it.toLong()) }, onSelect = { selected = it; view = CalendarView.MES; anchor = it })
        }

        if (view != CalendarView.AÑO) selected?.let { d -> DayDetail(d, state.days[d], state) }

        InsightsCard(state.insights)
    }
}

// ─── Piezas comunes ──────────────────────────────────────────────────────────

@Composable
private fun StreakBadge(s: StreakInfo) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier.clip(shape).background(Panel).border(1.dp, Line, shape).padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.End,
    ) {
        Text("${s.current} días", color = Ion, style = MaterialTheme.typography.titleMedium)
        Text(if (s.wildcardUsedThisWeek) "racha · comodín usado" else "racha · máx ${s.longest}", color = TextMute, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun <T> Segmented(items: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Row(Modifier.fillMaxWidth().clip(shape).border(1.dp, LineStrong, shape).padding(3.dp)) {
        items.forEach { item ->
            val on = item == selected
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(9.dp)).background(if (on) PanelHigh else Color.Transparent)
                    .clickable(role = Role.Tab) { onSelect(item) }.padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) { Text(label(item), color = if (on) TextHigh else TextMute, style = TextStyle(fontFamily = MonoFamily, fontSize = 12.sp)) }
        }
    }
}

@Composable
private fun Chip(label: String, on: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Box(
        Modifier.clip(shape).border(1.dp, if (on) Ion else LineStrong, shape).clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) { Text(label, color = if (on) Ion else TextMute, style = MaterialTheme.typography.labelMedium) }
}

@Composable
private fun Card(content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(Panel).border(1.dp, Line, shape).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp), content = content,
    )
}

@Composable
private fun Navigator(title: String, onShift: (Int) -> Unit, canForward: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onShift(-1) }) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, "Anterior", tint = TextMute) }
        Text(title, color = TextHigh, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
        IconButton(onClick = { onShift(1) }, enabled = canForward) {
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, "Siguiente", tint = if (canForward) TextMute else TextDim)
        }
    }
}

private fun intensity(state: CalendarUiState, d: LocalDate, metric: CalendarMetric): Float? =
    state.days[d]?.let { HistoryMath.intensity(it, metric, state.stepsTarget, state.sleepTarget, state.intakeTarget) }

/** Color del día: tonos de Ion según intensidad; sin datos = pista apagada. */
private fun heat(v: Float?, metric: CalendarMetric): Color = when {
    v == null -> Line
    metric == CalendarMetric.ACTIVIDAD -> Ion
    v < 0.25f -> Ion.copy(alpha = 0.26f)
    v < 0.5f -> Ion.copy(alpha = 0.38f)
    v < 0.75f -> Ion.copy(alpha = 0.6f)
    v < 0.95f -> Ion.copy(alpha = 0.82f)
    else -> Ion
}

@Composable
private fun Row2(label: String, value: String, valueColor: Color = TextHigh) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, color = TextMute, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        Text(value, color = valueColor, style = TextStyle(fontFamily = MonoFamily, fontSize = 13.sp))
    }
    HorizontalDivider(color = Line)
}

private fun fmtBalance(v: Float) = (if (v > 0) "+" else "") + v.roundToInt() + " kcal"

@Composable
private fun PeriodSummary(state: CalendarUiState, range: List<LocalDate>) {
    val ds = range.mapNotNull { state.days[it] }
    val balances = ds.mapNotNull { it.balanceKcal }
    val scores = ds.mapNotNull { it.dayScore }
    val weights = ds.mapNotNull { d -> d.weightKg?.let { d.date to it } }.sortedBy { it.first }
    val remTotal = ds.sumOf { it.remindersTotal }
    if (balances.isNotEmpty()) Row2("Balance medio", fmtBalance(balances.average().toFloat()), if (balances.average() < 0) Ion else TextHigh)
    if (scores.isNotEmpty()) Row2("Halo medio", "${scores.average().roundToInt()}")
    Row2("Días en objetivo", "${ds.count { HistoryMath.isGoodDay(it) }} / ${range.count { !it.isAfter(LocalDate.now()) }}")
    Row2("Entrenos", "${ds.count { it.trained }} · ${ds.sumOf { it.workoutMinutes }} min")
    if (remTotal > 0) Row2("Recordatorios cumplidos", "${ds.sumOf { it.remindersDone }} / $remTotal")
    if (weights.size >= 2) {
        val diff = weights.last().second - weights.first().second
        Row2("Peso", "%.1f kg · %s%.1f".format(es, weights.last().second, if (diff > 0) "+" else "", diff))
    } else if (weights.size == 1) Row2("Peso", "%.1f kg".format(es, weights.single().second))
}

// ─── Semana ──────────────────────────────────────────────────────────────────

@Composable
private fun InsightsCard(insights: List<com.lifedashboard.domain.insights.Insight>) {
    Card {
        Text("CONCLUSIONES", color = TextMute, style = mono11)
        if (insights.isEmpty()) {
            Text(
                "Cuando lleves unas semanas registrando sueño, comida, entrenos y ánimo, aquí verás qué cosas se relacionan en tu caso.",
                color = TextMute, style = MaterialTheme.typography.bodySmall,
            )
        } else {
            insights.take(4).forEach { ins ->
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                    Box(Modifier.width(2.dp).fillMaxHeight().background(Ion))
                    Spacer(Modifier.width(10.dp))
                    Text(ins.text, color = TextHigh, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun WeekSummaryHeader(state: CalendarUiState, monday: LocalDate) {
    val w = com.lifedashboard.domain.insights.WeeklySummary.build(state.days, monday)
    Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        Column {
            Text("HALO MEDIO", color = TextMute, style = mono11)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(w.avgHalo?.toString() ?: "—", color = TextHigh, style = MaterialTheme.typography.headlineSmall)
                w.haloDelta?.let {
                    Spacer(Modifier.width(6.dp))
                    Text((if (it >= 0) "+" else "") + it + " vs anterior", color = if (it >= 0) Ion else TextMute, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(bottom = 4.dp))
                }
            }
        }
        w.avgSleep?.let {
            Column {
                Text("SUEÑO", color = TextMute, style = mono11)
                Text("%.1f h".format(es, it), color = TextHigh, style = MaterialTheme.typography.headlineSmall)
            }
        }
    }
}

@Composable
private fun WeekView(state: CalendarUiState, metric: CalendarMetric, anchor: LocalDate, onShift: (Int) -> Unit, onSelect: (LocalDate) -> Unit) {
    val monday = HistoryMath.mondayOf(anchor)
    val days = (0..6).map { monday.plusDays(it.toLong()) }
    val f = DateTimeFormatter.ofPattern("d MMM", es)
    Card {
        Navigator("${monday.format(f)} – ${days.last().format(f)}", onShift, canForward = days.last().isBefore(LocalDate.now()))
        WeekSummaryHeader(state, monday)
        Row(Modifier.fillMaxWidth().height(130.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
            days.forEach { d ->
                val v = intensity(state, d, metric)
                Column(Modifier.weight(1f).clickable { onSelect(d) }, horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.fillMaxWidth().height((100f * (v ?: 0f)).coerceAtLeast(4f).dp)
                            .clip(RoundedCornerShape(6.dp)).background(if (v == null) Line else heat(v, metric))
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        d.dayOfWeek.getDisplayName(JTextStyle.NARROW, es).uppercase(),
                        color = if (d == LocalDate.now()) TextHigh else TextMute, style = mono11,
                    )
                }
            }
        }
        PeriodSummary(state, days)
    }
}

// ─── Mes ─────────────────────────────────────────────────────────────────────

@Composable
private fun MonthView(state: CalendarUiState, metric: CalendarMetric, month: YearMonth, selected: LocalDate?, onShift: (Int) -> Unit, onSelect: (LocalDate) -> Unit) {
    val first = month.atDay(1)
    val lead = first.dayOfWeek.value - 1
    val cells = List(lead) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }
    val today = LocalDate.now()
    Card {
        Navigator(
            month.month.getDisplayName(JTextStyle.FULL_STANDALONE, es).replaceFirstChar { it.uppercase() } + " " + month.year,
            onShift, canForward = month.isBefore(YearMonth.from(today)),
        )
        Row(Modifier.fillMaxWidth()) {
            DayOfWeek.entries.forEach {
                Text(it.getDisplayName(JTextStyle.NARROW, es).uppercase(), color = TextDim, style = mono11, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            }
        }
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                week.forEach { d ->
                    if (d == null) Spacer(Modifier.weight(1f).aspectRatio(1f)) else {
                        val v = if (d.isAfter(today)) null else intensity(state, d, metric)
                        val shape = RoundedCornerShape(8.dp)
                        Box(
                            Modifier.weight(1f).aspectRatio(1f).clip(shape)
                                .background(if (d.isAfter(today)) Color.Transparent else heat(v, metric))
                                .border(
                                    if (d == selected || d == today) 1.dp else 0.dp,
                                    if (d == selected) TextHigh else if (d == today) TextMute else Color.Transparent, shape,
                                )
                                .clickable(enabled = !d.isAfter(today)) { onSelect(d) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "${d.dayOfMonth}",
                                color = if (v != null && v >= 0.6f) Obsidian else if (d.isAfter(today)) TextDim else TextMute,
                                style = TextStyle(fontFamily = MonoFamily, fontSize = 11.sp),
                            )
                        }
                    }
                }
                repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        PeriodSummary(state, (1..month.lengthOfMonth()).map { month.atDay(it) })
    }
}

// ─── Año ─────────────────────────────────────────────────────────────────────

@Composable
private fun YearView(state: CalendarUiState, metric: CalendarMetric, year: Int, onShift: (Int) -> Unit, onSelect: (LocalDate) -> Unit) {
    val today = LocalDate.now()
    val end = LocalDate.of(year, 12, 31)
    val yearDays = generateSequence(LocalDate.of(year, 1, 1)) { it.plusDays(1) }.takeWhile { !it.isAfter(minOf(end, today)) }.toList()
    Card {
        Navigator("$year", onShift, canForward = year < today.year)
        Text("${yearDays.count { state.days[it]?.hasData == true }} días con datos", color = TextMute, style = MaterialTheme.typography.labelSmall)
        // Año en píxeles: una fila por mes y una columna por día; cabe entero en pantalla
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val labelW = 22.dp
            val gap = 2.dp
            val cell = ((maxWidth - labelW - gap * 30) / 31).coerceAtMost(14.dp)
            Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    Spacer(Modifier.width(labelW - gap))
                    (1..31).forEach { day ->
                        Box(Modifier.width(cell), contentAlignment = Alignment.Center) {
                            if (day == 1 || day % 5 == 0) Text("$day", color = TextDim, style = TextStyle(fontFamily = MonoFamily, fontSize = 7.sp), maxLines = 1)
                        }
                    }
                }
                (1..12).forEach { m ->
                    val ym = YearMonth.of(year, m)
                    Row(horizontalArrangement = Arrangement.spacedBy(gap), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            java.time.Month.of(m).getDisplayName(JTextStyle.NARROW_STANDALONE, es).uppercase(),
                            color = if (ym == YearMonth.from(today)) TextHigh else TextMute,
                            style = mono11,
                            modifier = Modifier.width(labelW - gap),
                        )
                        (1..31).forEach { day ->
                            val exists = day <= ym.lengthOfMonth()
                            val d = if (exists) ym.atDay(day) else null
                            val inRange = d != null && !d.isAfter(today)
                            Box(
                                Modifier.size(cell).clip(RoundedCornerShape(2.dp))
                                    .background(
                                        when {
                                            d == null -> Color.Transparent
                                            !inRange -> Line.copy(alpha = 0.35f)
                                            else -> heat(intensity(state, d, metric), metric)
                                        }
                                    )
                                    .then(if (d == today) Modifier.border(1.dp, TextHigh, RoundedCornerShape(2.dp)) else Modifier)
                                    .clickable(enabled = inRange) { d?.let(onSelect) }
                            )
                        }
                    }
                }
            }
        }
        WeightChart(state, yearDays)
        // Mejor mes por Halo medio
        val byMonth = yearDays.mapNotNull { state.days[it] }.filter { it.dayScore != null }.groupBy { it.date.monthValue }
        byMonth.maxByOrNull { (_, l) -> l.mapNotNull { it.dayScore }.average() }?.let { (m, l) ->
            Row2(
                "Mejor mes",
                java.time.Month.of(m).getDisplayName(JTextStyle.FULL_STANDALONE, es).replaceFirstChar { it.uppercase() } +
                    " · " + l.mapNotNull { it.dayScore }.average().roundToInt(),
            )
        }
        Row2("Racha más larga", "${state.streak.longest} días")
        PeriodSummary(state, yearDays)
    }
}

@Composable
private fun WeightChart(state: CalendarUiState, range: List<LocalDate>) {
    val pts = range.mapNotNull { d -> state.days[d]?.weightKg?.let { d to it } }
    if (pts.size < 2) return
    val min = pts.minOf { it.second }
    val max = pts.maxOf { it.second }
    val span = (max - min).coerceAtLeast(0.5f)
    val first = range.first().toEpochDay().toFloat()
    val total = (range.last().toEpochDay() - range.first().toEpochDay()).coerceAtLeast(1).toFloat()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row {
            Text("PESO", color = TextMute, style = mono11, modifier = Modifier.weight(1f))
            Text("%.1f → %.1f kg".format(es, pts.first().second, pts.last().second), color = TextHigh, style = TextStyle(fontFamily = MonoFamily, fontSize = 12.sp))
        }
        Canvas(Modifier.fillMaxWidth().height(70.dp)) {
            val path = Path()
            pts.forEachIndexed { i, (d, w) ->
                val x = (d.toEpochDay() - first) / total * size.width
                val y = size.height - (w - min) / span * (size.height - 8f) - 4f
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, Ion, style = Stroke(width = 2.dp.toPx()))
            val (ld, lw) = pts.last()
            drawCircle(Ion, 3.5.dp.toPx(), Offset((ld.toEpochDay() - first) / total * size.width, size.height - (lw - min) / span * (size.height - 8f) - 4f))
        }
    }
}

// ─── Detalle del día ─────────────────────────────────────────────────────────

@Composable
private fun DayDetail(date: LocalDate, d: DaySummary?, state: CalendarUiState) {
    val f = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", es)
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(date.format(f).replaceFirstChar { it.uppercase() }, color = TextHigh, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            d?.dayScore?.let {
                Box(Modifier.clip(CircleShape).background(PanelHigh).padding(horizontal = 10.dp, vertical = 4.dp)) {
                    Text("Halo $it", color = Ion, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        if (d == null || !d.hasData) {
            Text("Sin datos este día.", color = TextMute, style = MaterialTheme.typography.bodySmall)
            return@Card
        }
        d.balanceKcal?.let { Row2("Balance", fmtBalance(it), if (it < 0) Ion else TextHigh) }
        if (d.intakeKcal > 0f) Row2("Comido / gastado", "${d.intakeKcal.roundToInt()} / ${d.expenditureKcal.roundToInt()} kcal")
        if (d.steps > 0) Row2("Pasos", "%,d".format(es, d.steps))
        if (d.sleepHours > 0f) Row2("Sueño", "%.1f h".format(es, d.sleepHours))
        Row2("Entreno", if (d.trained) "${d.workoutMinutes} min" else "Descanso", if (d.trained) Ion else TextMute)
        if (d.remindersTotal > 0) Row2("Recordatorios", "${d.remindersDone} / ${d.remindersTotal}")
        d.weightKg?.let { Row2("Peso", "%.1f kg".format(es, it)) }
        if (d.mood != null && d.energy != null) Row2(
            "Ánimo y energía",
            com.lifedashboard.presentation.common.components.moodLabel(d.mood) + " · " +
                com.lifedashboard.presentation.common.components.energyLabel(d.energy).lowercase()
        )
    }
}
