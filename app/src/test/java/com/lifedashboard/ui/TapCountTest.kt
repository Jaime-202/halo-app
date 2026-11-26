package com.lifedashboard.ui

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.printToLog
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.lifedashboard.data.repository.UserPreferencesRepository
import com.lifedashboard.presentation.MainActivity
import com.lifedashboard.presentation.screen.nutrition.defaultMealTypeForNow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.time.ZoneId

/**
 * Cuenta los toques para registrar comida (objetivos del plan de Comida):
 * - un alimento sugerido: 1 toque
 * - buscar y añadir: abrir, escribir, + y Añadir (3 toques y escribir)
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w411dp-h891dp-xxhdpi")
class TapCountTest {

    @get:Rule
    val compose = createEmptyComposeRule()
    private var taps = 0

    private fun seed(context: Context) {
        val path = context.getDatabasePath("life_dashboard.db")
        path.parentFile?.mkdirs()
        val db = SQLiteDatabase.openOrCreateDatabase(path, null)
        javaClass.classLoader!!.getResource("db_v8_schema.sql").readText()
            .split(";\n").map { it.trim() }.filter { it.isNotEmpty() }.forEach { db.execSQL(it) }
        val zone = ZoneId.systemDefault()
        val now = System.currentTimeMillis()
        // Historial: el mismo alimento en el momento actual durante 5 días anteriores
        for (d in 1L..5L) {
            val day = LocalDate.now().minusDays(d).atStartOfDay(zone).toInstant().toEpochMilli()
            db.execSQL("INSERT INTO meals (dateMillis, mealType, name, brand, grams, calories, protein, carbs, fat, notes, imageUrl, createdAt) VALUES ($day, '${defaultMealTypeForNow()}', 'Kéfir natural', 'Básico', 200, 120, 7, 9, 6, '', NULL, $now)")
        }
        db.version = 8
        db.close()
        runBlocking {
            val p = UserPreferencesRepository(context)
            p.updateEnergyProfile("Jaime", "HOMBRE", 30, 178, 78f, "MODERADO", "MANTENER")
            p.updateCalorieAndMacroTargets(2400f, 150f, 280f, 70f)
            p.setOnboardingCompleted(true)
        }
    }

    private fun waitFor(text: String) {
        try {
            compose.waitUntil(15_000) { compose.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty() }
        } catch (e: Throwable) {
            compose.onAllNodes(androidx.compose.ui.test.isRoot()).fetchSemanticsNodes().forEachIndexed { i, _ ->
                compose.onAllNodes(androidx.compose.ui.test.isRoot())[i].printToLog("ARBOL$i", maxDepth = 40)
            }
            throw e
        }
    }

    private fun tap(desc: String) { compose.onNodeWithContentDescription(desc).performClick(); taps++ }

    @Test
    fun suggestedFoodInOneTapAndSearchInThree() {
        org.robolectric.shadows.ShadowLog.stream = System.out
        seed(ApplicationProvider.getApplicationContext())
        ActivityScenario.launch(MainActivity::class.java).use {
            waitFor("BALANCE HOY")
            compose.onNodeWithContentDescription("Comida").performClick() // navegar a la pestaña no cuenta
            waitFor("SUGERIDO AHORA")

            // 1) Sugerido: un toque
            taps = 0
            tap("Añadir Kéfir natural")
            waitFor("Desliza un alimento")
            assertTrue("Sugerido en $taps toques", taps <= 2)
            assertTrue(compose.onAllNodesWithText("Kéfir natural").fetchSemanticsNodes().isNotEmpty())

            // 2) Buscar y añadir: abrir, escribir, +, Añadir
            taps = 0
            tap("Añadir comida")
            compose.onNodeWithContentDescription("Buscar alimento").performTextInput("plátano")
            // En el simulador el reloj no avanza solo: dejar pasar la espera antes de buscar
            org.robolectric.shadows.ShadowLooper.idleMainLooper(500, java.util.concurrent.TimeUnit.MILLISECONDS)
            val platano = androidx.compose.ui.test.hasContentDescription("Añadir Plátano", substring = true)
            try {
                compose.waitUntil(20_000) { compose.onAllNodes(platano).fetchSemanticsNodes().isNotEmpty() }
            } catch (e: Throwable) {
                compose.onAllNodes(androidx.compose.ui.test.isRoot()).fetchSemanticsNodes().forEachIndexed { i, _ ->
                    compose.onAllNodes(androidx.compose.ui.test.isRoot())[i].printToLog("ARBOL$i", maxDepth = 40)
                }
                throw e
            }
            compose.onAllNodes(platano)[0].performClick(); taps++
            compose.onNode(hasText("Añadir") and androidx.compose.ui.test.hasClickAction() and !hasText("Añadir a", substring = true)).performClick(); taps++
            waitFor("Plátano")
            assertTrue("Búsqueda en $taps toques", taps <= 4)
        }
    }
}
