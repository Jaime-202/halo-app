package com.lifedashboard.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import android.os.Build
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.lifedashboard.core.worker.WidgetUpdateWorker
import com.lifedashboard.core.worker.WorkManagerScheduler
import com.lifedashboard.presentation.navigation.*
import com.lifedashboard.presentation.theme.*
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var workManagerScheduler: WorkManagerScheduler
    @Inject lateinit var userPreferences: com.lifedashboard.data.repository.UserPreferencesRepository
    @Inject lateinit var energyTargets: com.lifedashboard.data.repository.EnergyTargetsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Iniciar workers en background
        workManagerScheduler.scheduleAll()
        workManagerScheduler.runHealthSyncNow()

        // Refrescar el gasto adaptativo y los objetivos con los datos más recientes
        lifecycleScope.launch(Dispatchers.IO) {
            if (userPreferences.userGoals.first().onboardingCompleted) energyTargets.recalculate()
        }

        setContent {
            // null hasta leer las preferencias, para no mostrar la bienvenida por error
            val goals by userPreferences.userGoals.collectAsState(initial = null as com.lifedashboard.data.repository.UserGoals?)
            LifeDashboardTheme(themeMode = goals?.themeMode ?: "OSCURO") {
                val loaded = goals
                when {
                    loaded == null -> Box(Modifier.fillMaxSize().background(Obsidian))
                    !loaded.onboardingCompleted ->
                        com.lifedashboard.presentation.screen.onboarding.OnboardingScreen()
                    else -> LifeDashboardApp()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch(Dispatchers.IO) {
            WidgetUpdateWorker.updateAllWidgets(applicationContext)
        }
    }
}

@Composable
private fun LifeDashboardApp() {
    val navController = rememberNavController()
    val navBackStack by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStack?.destination?.route
    val context = LocalContext.current

    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { /* El usuario responde al permiso */ }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!hasPermission) {
                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            val navigateTo: (Screen) -> Unit = { screen ->
                if (screen == Screen.Dashboard) {
                    navController.popBackStack(Screen.Dashboard.route, inclusive = false)
                } else {
                    val targetRoute = if (screen is Screen.Health) Screen.Health.createRoute("PASOS") else screen.route
                    navController.navigate(targetRoute) {
                        popUpTo(Screen.Dashboard.route) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            }
            OrbitNavBar(
                items          = bottomNavItems,
                currentRoute   = currentRoute,
                onNavigate     = navigateTo,
                actionSelected = currentRoute == Screen.Settings.route,
                onAction       = { navigateTo(Screen.Settings) },
            )
        }
    ) { paddingValues ->
        Box(Modifier.padding(paddingValues)) {
            NavGraph(navController = navController)
        }
    }
}
