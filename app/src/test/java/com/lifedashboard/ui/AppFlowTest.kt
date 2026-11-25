package com.lifedashboard.ui

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.lifedashboard.data.repository.UserPreferencesRepository
import kotlinx.coroutines.runBlocking
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import com.lifedashboard.presentation.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Recorre la app completa como un usuario nuevo: bienvenida, pantalla Hoy, pestañas
 * y botón "+". Si algo hace que la app se cierre, la prueba falla con el error.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w411dp-h891dp-xxhdpi")
class AppFlowTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private fun waitFor(text: String) = compose.waitUntil(10_000) {
        compose.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty()
    }

    @Test
    fun onboardingThenTabsDoNotCrash() {
        // Instalación nueva: la bienvenida aún no se ha completado
        runBlocking {
            UserPreferencesRepository(ApplicationProvider.getApplicationContext()).setOnboardingCompleted(false)
        }
        ActivityScenario.launch(MainActivity::class.java).use { flow() }
    }

    private fun flow() {
        waitFor("Empezar")
        compose.onNodeWithText("Cómo te llamas").performTextReplacement("Jaime")
        compose.onNodeWithText("Empezar").performClick()
        waitFor("Sexo y edad")
        compose.onNodeWithText("Continuar").performClick()
        waitFor("Altura y peso")
        compose.onNodeWithContentDescription("Más Peso").performClick()
        compose.onNodeWithText("Continuar").performClick()
        waitFor("¿Cuánto te mueves?")
        compose.onNodeWithText("Continuar").performClick()
        waitFor("¿Qué quieres conseguir?")
        compose.onNodeWithText("Continuar").performClick()
        waitFor("Tu objetivo diario")
        compose.onNodeWithText("Entrar en Halo").performClick()

        // Pantalla Hoy
        waitFor("BALANCE HOY")
        compose.mainClock.advanceTimeBy(3_000)

        // Pestañas
        for (tab in listOf("Comida", "Calendario", "Actividad", "Hoy")) {
            compose.onNodeWithContentDescription(tab).performClick()
            compose.waitForIdle()
            compose.mainClock.advanceTimeBy(1_500)
        }

        // Botón +
        compose.onNodeWithContentDescription("Registrar").performClick()
        waitFor("REGISTRAR")

        // Ajustes desde el avatar
        compose.onNodeWithContentDescription("Perfil y ajustes", useUnmergedTree = true)
        compose.onNodeWithText("Agua +250 ml").performClick()
        compose.waitForIdle()
    }
}
