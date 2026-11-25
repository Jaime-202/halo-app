package com.lifedashboard.presentation.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.*
import androidx.glance.action.clickable
import androidx.glance.appwidget.*
import androidx.glance.layout.*
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.lifedashboard.data.local.dao.HealthDao
import com.lifedashboard.data.local.dao.MealDao
import com.lifedashboard.data.local.dao.ReminderDao
import com.lifedashboard.data.local.dao.WaterIntakeDao
import com.lifedashboard.data.repository.UserPreferencesRepository
import com.lifedashboard.domain.energy.ActivityLevel
import com.lifedashboard.domain.energy.EnergyCalculator
import com.lifedashboard.domain.energy.Sex
import com.lifedashboard.domain.score.DayInputs
import com.lifedashboard.domain.score.HaloScores
import com.lifedashboard.presentation.MainActivity
import com.lifedashboard.presentation.theme.*
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale
import kotlin.math.roundToInt

/*
 * Widgets de Halo (Jetpack Glance):
 *  - Anillo (2x2): Halo Score y lo que te queda por comer.
 *  - Hoy (4x2): anillo + balance + pasos, sueño, agua y proteína.
 * Mantienen los nombres de receptor anteriores para no romper los widgets ya colocados.
 */

// ─── Datos ───────────────────────────────────────────────────────────────────

data class WidgetData(
    val dayScore: Int? = null,
    val balance: Int? = null,
    val remainingKcal: Int = 0,
    val steps: Int = 0,
    val stepsProgress: Float = 0f,
    val sleepHours: Float = 0f,
    val waterMl: Int = 0,
    val waterTargetMl: Int = 2500,
    val proteinG: Int = 0,
    val proteinTargetG: Int = 0,
)

@dagger.hilt.EntryPoint
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
interface WidgetEntryPoint {
    fun healthDao(): HealthDao
    fun mealDao(): MealDao
    fun waterIntakeDao(): WaterIntakeDao
    fun reminderDao(): ReminderDao
    fun userPreferences(): UserPreferencesRepository
}

suspend fun loadWidgetData(context: Context): WidgetData = withContext(Dispatchers.IO) {
    try {
        val ep = EntryPointAccessors.fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
        val g = ep.userPreferences().userGoals.first()
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()
        val start = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1

        val health = ep.healthDao().getHealthForDay(start)
        val meals = ep.mealDao().getMealsForDay(start, end)
        val intake = meals.sumOf { it.calories.toDouble() }.toFloat()
        val protein = meals.sumOf { it.protein.toDouble() }.toFloat()
        val water = ep.waterIntakeDao().getTotalForDay(start).first() ?: 0
        val steps = health?.steps ?: 0
        val sleep = health?.sleepHours ?: 0f

        val bmr = EnergyCalculator.bmr(Sex.from(g.sex), g.weightKg, g.heightCm, g.ageYears)
        val tdee = g.adaptiveTdeeKcal.takeIf { it > bmr } ?: bmr * ActivityLevel.from(g.activityLevel).factor
        val fraction = (LocalTime.now().toSecondOfDay() / 86_400f).coerceIn(0.05f, 1f)
        val expenditure = EnergyCalculator.expenditureForDay(bmr, tdee, health?.activeCalories ?: 0f, fraction)

        WidgetData(
            dayScore = HaloScores.dayScore(
                DayInputs(steps, g.stepsTarget, sleep, g.sleepTargetHours, intake.takeIf { it > 0f }, g.caloriesTarget, 0, 0)
            ),
            balance = if (intake > 0f) (intake - expenditure).roundToInt() else null,
            remainingKcal = (g.caloriesTarget - intake).coerceAtLeast(0f).roundToInt(),
            steps = steps,
            stepsProgress = if (g.stepsTarget > 0) (steps.toFloat() / g.stepsTarget).coerceIn(0f, 1f) else 0f,
            sleepHours = sleep,
            waterMl = water,
            waterTargetMl = g.waterTargetMl,
            proteinG = protein.roundToInt(),
            proteinTargetG = g.proteinTargetGrams.roundToInt(),
        )
    } catch (e: Exception) {
        WidgetData()
    }
}

// ─── Anillo dibujado como imagen (Glance no tiene anillos de progreso) ───────

private fun ringBitmap(progress: Float, sizePx: Int = 220): Bitmap {
    val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val c = android.graphics.Canvas(bmp)
    val stroke = sizePx * 0.09f
    val rect = RectF(stroke / 2, stroke / 2, sizePx - stroke / 2, sizePx - stroke / 2)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = stroke; strokeCap = Paint.Cap.ROUND }
    paint.color = Line.toArgb()
    c.drawArc(rect, 0f, 360f, false, paint)
    if (progress > 0f) {
        paint.color = Ion.toArgb()
        c.drawArc(rect, -90f, 360f * progress.coerceIn(0f, 1f), false, paint)
    }
    return bmp
}

private fun cp(c: Color) = ColorProvider(c)
private val es = Locale("es", "ES")

@Composable
private fun Ring(data: WidgetData, size: Int) {
    Box(GlanceModifier.size(size.dp), contentAlignment = Alignment.Center) {
        Image(
            provider = ImageProvider(ringBitmap((data.dayScore ?: 0) / 100f)),
            contentDescription = "Halo ${data.dayScore ?: 0}",
            modifier = GlanceModifier.size(size.dp),
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(data.dayScore?.toString() ?: "—", style = TextStyle(color = cp(TextHigh), fontSize = (size / 3.4).sp, fontWeight = FontWeight.Medium))
            Text("HALO", style = TextStyle(color = cp(TextMute), fontSize = 8.sp, fontFamily = FontFamily.Monospace))
        }
    }
}

private fun GlanceModifier.haloBackground(context: Context) = this
    .fillMaxSize()
    .background(cp(Obsidian))
    .cornerRadius(24.dp)
    .clickable(androidx.glance.action.actionStartActivity<MainActivity>())

// ─── Widget Anillo (2x2) ─────────────────────────────────────────────────────

class LifeDashboard2x2Widget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = loadWidgetData(context)
        provideContent {
            Column(
                modifier = GlanceModifier.haloBackground(context).padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Ring(data, 88)
                Spacer(GlanceModifier.height(6.dp))
                Text("${data.remainingKcal} kcal", style = TextStyle(color = cp(TextHigh), fontSize = 13.sp, fontFamily = FontFamily.Monospace))
                Text("te quedan", style = TextStyle(color = cp(TextMute), fontSize = 10.sp))
            }
        }
    }
}

// ─── Widget Hoy (4x2) ────────────────────────────────────────────────────────

class LifeDashboardWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = loadWidgetData(context)
        provideContent {
            Row(
                modifier = GlanceModifier.haloBackground(context).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Ring(data, 92)
                Spacer(GlanceModifier.width(14.dp))
                Column(GlanceModifier.defaultWeight()) {
                    Text("BALANCE HOY", style = TextStyle(color = cp(TextMute), fontSize = 9.sp, fontFamily = FontFamily.Monospace))
                    Text(
                        data.balance?.let { (if (it > 0) "+" else "") + it + " kcal" } ?: "Sin comidas",
                        style = TextStyle(color = cp(if ((data.balance ?: 0) < 0) Ion else TextHigh), fontSize = 18.sp, fontWeight = FontWeight.Medium),
                    )
                    Spacer(GlanceModifier.height(6.dp))
                    MetricLine("Pasos", "%,d".format(es, data.steps), data.stepsProgress)
                    MetricLine("Sueño", if (data.sleepHours > 0f) "%.1f h".format(es, data.sleepHours) else "—", null)
                    MetricLine("Agua", "%.1f L".format(es, data.waterMl / 1000f), if (data.waterTargetMl > 0) data.waterMl.toFloat() / data.waterTargetMl else null)
                    if (data.proteinTargetG > 0) MetricLine("Proteína", "${data.proteinG} g", data.proteinG.toFloat() / data.proteinTargetG)
                }
            }
        }
    }
}

@Composable
private fun MetricLine(label: String, value: String, progress: Float?) {
    Column(GlanceModifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Row(GlanceModifier.fillMaxWidth()) {
            Text(label, style = TextStyle(color = cp(TextMute), fontSize = 11.sp), modifier = GlanceModifier.defaultWeight())
            Text(value, style = TextStyle(color = cp(TextHigh), fontSize = 11.sp, fontFamily = FontFamily.Monospace))
        }
        if (progress != null) {
            LinearProgressIndicator(
                progress = progress.coerceIn(0f, 1f),
                modifier = GlanceModifier.fillMaxWidth().height(2.dp),
                color = cp(Ion),
                backgroundColor = cp(Line),
            )
        }
    }
}
