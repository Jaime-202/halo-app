package com.lifedashboard.presentation

import android.os.Bundle
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.unit.sp
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

    /** La pantalla de inicio se mantiene hasta leer las preferencias (sin destello negro). */
    @Volatile private var ready = false

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        splash.setKeepOnScreenCondition { !ready }
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            userPreferences.userGoals.first()
            ready = true
            // Avisa al sistema de que la app ya es usable (mejora las optimizaciones de arranque)
            window.decorView.post { reportFullyDrawn() }
        }
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
            val themeMode = goals?.themeMode ?: "OSCURO"
            val isLight = paletteFor(themeMode).isLight
            // Iconos de la barra de estado oscuros en modo claro y claros en modo oscuro
            LaunchedEffect(isLight) {
                val transparent = android.graphics.Color.TRANSPARENT
                val style = if (isLight) androidx.activity.SystemBarStyle.light(transparent, transparent)
                            else androidx.activity.SystemBarStyle.dark(transparent)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            LifeDashboardTheme(themeMode = themeMode) {
                CrashNoticeDialog()
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

    // Botón "+": registro universal
    val quickLog: com.lifedashboard.presentation.quicklog.QuickLogViewModel = androidx.hilt.navigation.compose.hiltViewModel()
    var showQuickLog by remember { mutableStateOf(false) }
    var weightDialogKg by remember { mutableStateOf<Float?>(null) }
    var showMoodDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    if (showQuickLog) {
        com.lifedashboard.presentation.quicklog.QuickLogSheet(
            onDismiss = { showQuickLog = false },
            onNutrition = { action ->
                showQuickLog = false
                com.lifedashboard.presentation.quicklog.QuickActionBus.request(action)
                navController.navigate(Screen.Nutrition.route) {
                    popUpTo(Screen.Dashboard.route) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            },
            onWeight = {
                showQuickLog = false
                scope.launch { weightDialogKg = quickLog.currentWeight() }
            },
            onMood = {
                showQuickLog = false
                showMoodDialog = true
            },
            onWater = { ml ->
                showQuickLog = false
                quickLog.addWater(ml)
                android.widget.Toast.makeText(context, "+$ml ml de agua", android.widget.Toast.LENGTH_SHORT).show()
            },
        )
    }
    if (showMoodDialog) {
        com.lifedashboard.presentation.common.components.MoodDialog(
            initialMood = null,
            initialEnergy = null,
            onSave = { m, e ->
                quickLog.saveMood(m, e)
                showMoodDialog = false
                android.widget.Toast.makeText(context, "Guardado", android.widget.Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showMoodDialog = false },
        )
    }
    weightDialogKg?.let { kg ->
        com.lifedashboard.presentation.quicklog.QuickWeightDialog(
            initialKg = kg,
            onSave = {
                quickLog.logWeight(it)
                weightDialogKg = null
                android.widget.Toast.makeText(context, "Peso guardado", android.widget.Toast.LENGTH_SHORT).show()
            },
            onDismiss = { weightDialogKg = null },
        )
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
                onAction       = { showQuickLog = true },
            )
        }
    ) { paddingValues ->
        Box(Modifier.padding(paddingValues)) {
            NavGraph(navController = navController)
        }
    }
}


/** Si la app se cerró por un error, lo muestra al volver a abrirla para poder copiarlo. */
@Composable
private fun CrashNoticeDialog() {
    val context = LocalContext.current
    var crash by remember { mutableStateOf(com.lifedashboard.core.crash.CrashReporter.lastCrash(context)) }
    val text = crash ?: return
    AlertDialog(
        onDismissRequest = {},
        containerColor = Panel,
        title = { Text("Halo se cerró la última vez", color = TextHigh) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Copia este texto y pásalo para poder arreglarlo.", color = TextMute, style = MaterialTheme.typography.bodySmall)
                Box(
                    Modifier.heightIn(max = 320.dp).fillMaxWidth().clip(RoundedCornerShape(8.dp))
                        .background(PanelHigh).verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(8.dp)
                ) {
                    Text(text, color = TextHigh, style = androidx.compose.ui.text.TextStyle(fontFamily = MonoFamily, fontSize = 10.sp))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                cm.setPrimaryClip(android.content.ClipData.newPlainText("Error de Halo", text))
                android.widget.Toast.makeText(context, "Copiado", android.widget.Toast.LENGTH_SHORT).show()
            }) { Text("Copiar", color = Ion) }
        },
        dismissButton = {
            TextButton(onClick = {
                com.lifedashboard.core.crash.CrashReporter.clear(context)
                crash = null
            }) { Text("Cerrar", color = TextMute) }
        },
    )
}
