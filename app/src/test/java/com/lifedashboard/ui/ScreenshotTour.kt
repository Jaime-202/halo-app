package com.lifedashboard.ui

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.lifedashboard.data.repository.UserPreferencesRepository
import com.lifedashboard.presentation.MainActivity
import kotlinx.coroutines.runBlocking
import org.junit.Assume
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

/**
 * Capturas de pantalla de la app con datos de ejemplo (para revisar el diseño).
 * Solo se ejecuta si la variable de entorno HALO_SHOTS indica una carpeta de salida.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w411dp-h891dp-xxhdpi")
class ScreenshotTour {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val outDir = System.getenv("HALO_SHOTS")
    private val theme = System.getenv("HALO_THEME") ?: "OSCURO"

    private fun seed(context: Context) {
        val path = context.getDatabasePath("life_dashboard.db")
        path.parentFile?.mkdirs()
        val db = SQLiteDatabase.openOrCreateDatabase(path, null)
        javaClass.classLoader!!.getResource("db_v8_schema.sql").readText()
            .split(";\n").map { it.trim() }.filter { it.isNotEmpty() }.forEach { db.execSQL(it) }
        val zone = ZoneId.systemDefault()
        fun day(ago: Long) = LocalDate.now().minusDays(ago).atStartOfDay(zone).toInstant().toEpochMilli()
        val now = System.currentTimeMillis()
        val foods = listOf(
            listOf("DESAYUNO", "Copos de avena integral", "Hacendado", 60, 225, 8, 36, 4),
            listOf("DESAYUNO", "Plátano", "Básico", 120, 107, 1, 27, 0),
            listOf("DESAYUNO", "Leche semidesnatada", "Básico", 250, 115, 8, 12, 4),
            listOf("COMIDA", "Pechuga de pollo a la plancha", "Básico", 180, 297, 56, 0, 7),
            listOf("COMIDA", "Arroz blanco cocido", "Básico", 200, 260, 5, 57, 1),
            listOf("MERIENDA", "Yogur griego natural", "Hacendado", 125, 150, 7, 5, 11),
            listOf("CENA", "Tortilla francesa", "Básico", 120, 190, 13, 1, 15),
            listOf("SNACK", "Almendras crudas", "Básico", 30, 175, 6, 3, 15),
        )
        for (d in 0L..120L) {
            db.execSQL("INSERT INTO health_data VALUES (${day(d)}, ${5000 + (d * 377) % 7000}, 4000.0, ${250 + (d * 53) % 300}.0, ${6.0 + (d % 5) * 0.5}, 1.1, 1.5, $now)")
            if (d % 7 != 6L) for (f in foods) {
                if (d == 0L && f[0] in setOf("CENA", "SNACK")) continue
                db.execSQL("INSERT INTO meals (dateMillis, mealType, name, brand, grams, calories, protein, carbs, fat, notes, imageUrl, createdAt) VALUES (${day(d)}, '${f[0]}', '${f[1]}', '${f[2]}', ${f[3]}, ${f[4]}, ${f[5]}, ${f[6]}, ${f[7]}, '', NULL, $now)")
            }
            if (d % 4 == 0L) db.execSQL("INSERT INTO weight_entries (dateMillis, weightKg, note) VALUES (${day(d) + 3600000}, ${78.0 + d * 0.03}, NULL)")
            if (d % 2 == 0L) db.execSQL("INSERT INTO workout_sessions (dateMillis, type, durationMinutes, notes, sourceFile, muscleGroupAllocation) VALUES (${day(d)}, 'STRENGTH', 55, 'Pierna', 'health_connect', NULL)")
        }
        db.execSQL("INSERT INTO reminders (title, type, daysOfWeek, timeOfDay, iconEmoji, isEnabled, createdAt) VALUES ('Creatina 5 g', 'DIARIO', '', '09:00', '💊', 1, ${day(130)})")
        db.execSQL("INSERT INTO reminders (title, type, daysOfWeek, timeOfDay, iconEmoji, isEnabled, createdAt) VALUES ('Pesarme', 'SEMANAL', '1,4', '08:00', '⚖️', 1, ${day(130)})")
        db.version = 8
        db.close()
        runBlocking {
            val p = UserPreferencesRepository(context)
            p.updateEnergyProfile("Jaime", "HOMBRE", 30, 178, 78f, "MODERADO", "PERDER_RAPIDO")
            p.updateCalorieAndMacroTargets(2210f, 155f, 240f, 62f)
            p.updateThemeMode(theme)
            p.setOnboardingCompleted(true)
        }
    }

    private fun waitFor(text: String) = compose.waitUntil(15_000) {
        compose.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty()
    }

    private var scenario: ActivityScenario<MainActivity>? = null

    private fun shot(name: String) {
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(2_500)
        compose.waitForIdle()
        scenario!!.onActivity { act ->
            // Ventana de más arriba (incluye diálogos y hojas a pantalla completa)
            val roots = try {
                val wmg = Class.forName("android.view.WindowManagerGlobal").getMethod("getInstance").invoke(null)
                val f = wmg.javaClass.getDeclaredField("mViews").apply { isAccessible = true }
                @Suppress("UNCHECKED_CAST") (f.get(wmg) as List<android.view.View>).filter { it.isShown && it.width > 0 }
            } catch (e: Exception) { emptyList() }
            val v = roots.lastOrNull() ?: act.window.decorView
            val bmp = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
            v.draw(android.graphics.Canvas(bmp))
            File(outDir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    @Test
    fun tour() {
        Assume.assumeTrue(outDir != null)
        File(outDir!!).mkdirs()
        val context = ApplicationProvider.getApplicationContext<Context>()
        seed(context)
        ActivityScenario.launch(MainActivity::class.java).use {
            scenario = it
            waitFor("BALANCE HOY"); shot("01-hoy")
            compose.onNodeWithContentDescription("Comida").performClick(); shot("02-comida")
            compose.onNodeWithContentDescription("Ver semana").performClick(); shot("02c-comida-semana")
            compose.onRoot().performTouchInput { swipeUp(startY = bottom * 0.7f, endY = top + 10f, durationMillis = 400) }
            shot("02b-comida-dia")
            compose.onNodeWithContentDescription("Añadir comida").performClick(); shot("02d-anadir")
            compose.onNodeWithContentDescription("Añadir Copos de avena integral a la cesta").performClick()
            compose.onNodeWithContentDescription("Añadir Plátano a la cesta").performClick(); shot("02e-anadir-cesta")
            compose.onAllNodes(hasText("Leche semidesnatada", substring = true)).let { it[it.fetchSemanticsNodes().size - 1] }.performClick(); shot("02f-ficha")
            compose.onNodeWithText("Añadir a la cesta").performClick()
            compose.waitForIdle()
            compose.onNodeWithContentDescription("Cerrar").performClick()
            compose.onNodeWithContentDescription("Calendario").performClick(); shot("03-calendario-mes")
            compose.onNodeWithText("Año").performClick(); shot("04-calendario-ano")
            compose.onNodeWithText("Semana").performClick(); shot("05-calendario-semana")
            compose.onNodeWithContentDescription("Actividad").performClick(); shot("06-actividad")
            compose.onNodeWithContentDescription("Hoy").performClick()
            waitFor("BALANCE HOY")
            compose.onAllNodes(hasText("PASOS")).onFirst().performClick()
            waitFor("DESDE HEALTH CONNECT"); shot("10-salud")
            compose.onNodeWithText("Sueño").performClick(); shot("11-salud-sueno")
            it.onActivity { a -> a.onBackPressedDispatcher.onBackPressed() }
            waitFor("BALANCE HOY")
            compose.onNodeWithContentDescription("Perfil y ajustes").performClick(); shot("07-ajustes")
            compose.onNodeWithText("Mis recordatorios").performClick(); shot("08-ajustes-recordatorios")
            compose.onNodeWithContentDescription("Volver a Ajustes").performClick()
            compose.onNodeWithText("Calorías").performClick(); shot("09-ajustes-calorias")
        }
    }
}
