package com.lifedashboard.ui

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToLog
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.lifedashboard.data.repository.UserPreferencesRepository
import com.lifedashboard.presentation.MainActivity
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.time.ZoneId

/**
 * Simula actualizar desde la versión anterior (base de datos v8 con datos y preferencias
 * antiguas) y recorre la bienvenida y las pestañas.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w411dp-h891dp-xxhdpi")
class UpgradeFlowTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private fun seedOldInstall(context: Context) {
        val path = context.getDatabasePath("life_dashboard.db")
        path.parentFile?.mkdirs()
        val db = SQLiteDatabase.openOrCreateDatabase(path, null)
        val schema = javaClass.classLoader!!.getResource("db_v8_schema.sql").readText()
        schema.split(";\n").map { it.trim() }.filter { it.isNotEmpty() }.forEach { db.execSQL(it) }
        val zone = ZoneId.systemDefault()
        fun day(ago: Long) = LocalDate.now().minusDays(ago).atStartOfDay(zone).toInstant().toEpochMilli()
        val now = System.currentTimeMillis()
        for (d in 0L..20L) {
            db.execSQL("INSERT INTO health_data VALUES (${day(d)}, ${6000 + d * 100}, 4000.0, 350.0, 7.2, 1.1, 1.5, $now)")
            db.execSQL("INSERT INTO meals (dateMillis, mealType, name, brand, grams, calories, protein, carbs, fat, notes, imageUrl, createdAt) VALUES (${day(d)}, 'COMIDA', 'Pollo con arroz', 'Básico', 300.0, 520.0, 40.0, 60.0, 10.0, '', NULL, $now)")
            if (d % 3 == 0L) db.execSQL("INSERT INTO weight_entries (dateMillis, weightKg, note) VALUES (${day(d) + 3600000}, ${78.0 + d * 0.05}, NULL)")
            if (d % 2 == 0L) db.execSQL("INSERT INTO workout_sessions (dateMillis, type, durationMinutes, notes, sourceFile, muscleGroupAllocation) VALUES (${day(d)}, 'STRENGTH', 55, 'Pierna', 'health_connect', NULL)")
        }
        db.execSQL("INSERT INTO reminders (title, type, daysOfWeek, timeOfDay, iconEmoji, isEnabled, createdAt) VALUES ('Creatina', 'DIARIO', '', '09:00', '💊', 1, ${day(30)})")
        db.execSQL("INSERT INTO reminder_logs (reminderId, dateMillis, isCompleted, completedAt) VALUES (1, ${day(1)}, 1, $now)")
        db.version = 8
        db.close()

        runBlocking {
            val prefs = UserPreferencesRepository(context)
            prefs.updateUserName("Jaime")
            prefs.updateFitnessProfile("INTERMEDIO", "GRASA", 78f, 178)
            prefs.updateThemeMode("MIDNIGHT")
            prefs.updateCalorieAndMacroTargets(2100f, 150f, 220f, 65f)
            prefs.setOnboardingCompleted(false)
        }
    }

    private fun waitFor(text: String) {
        try {
            compose.waitUntil(15_000) {
                compose.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty()
            }
        } catch (e: Throwable) {
            println("=== ÁRBOL ===")
            try { compose.onRoot(useUnmergedTree = true).printToLog("ARBOL", maxDepth = 30) } catch (x: Throwable) { println("sin raíz: $x") }
            throw e
        }
    }

    @Test
    fun upgradeFromPreviousVersion() {
        org.robolectric.shadows.ShadowLog.stream = System.out
        val context = ApplicationProvider.getApplicationContext<Context>()
        seedOldInstall(context)
        ActivityScenario.launch(MainActivity::class.java).use {
            waitFor("Empezar")
            compose.onNodeWithText("Empezar").performClick()
            waitFor("Sexo y edad")
            compose.onNodeWithText("Continuar").performClick()
            waitFor("Altura y peso")
            compose.onNodeWithText("Continuar").performClick()
            waitFor("¿Cuánto te mueves?")
            compose.onNodeWithText("Continuar").performClick()
            waitFor("¿Qué quieres conseguir?")
            compose.onNodeWithText("Continuar").performClick()
            waitFor("Tu objetivo diario")
            compose.onNodeWithText("Entrar en Halo").performClick()
            waitFor("BALANCE HOY")
            compose.mainClock.advanceTimeBy(3_000)
            for (tab in listOf("Comida", "Calendario", "Actividad", "Hoy")) {
                compose.onNodeWithContentDescription(tab).performClick()
                compose.waitForIdle()
                compose.mainClock.advanceTimeBy(1_500)
            }
        }
    }
}
