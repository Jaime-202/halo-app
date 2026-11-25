package com.lifedashboard.presentation.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String) {
    data object Dashboard  : Screen("dashboard")
    data object Workout    : Screen("workout")
    data object Nutrition  : Screen("nutrition")
    data object Health     : Screen("health?tab={tab}") {
        const val ROUTE_BASE = "health"
        fun createRoute(tab: String = "PASOS") = "health?tab=$tab"
    }
    data object Settings   : Screen("settings")
    data object Calendar   : Screen("calendar")
}

data class BottomNavItem(
    val screen: Screen,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector = icon
)

/** Pestañas de la barra Órbita. Ajustes va aparte, en el botón separado. */
val bottomNavItems = listOf(
    BottomNavItem(
        screen       = Screen.Dashboard,
        label        = "Hoy",
        icon         = Icons.Outlined.TrackChanges,
        selectedIcon = Icons.Filled.TrackChanges,
    ),
    BottomNavItem(
        screen       = Screen.Nutrition,
        label        = "Comida",
        icon         = Icons.Outlined.Restaurant,
        selectedIcon = Icons.Filled.Restaurant,
    ),
    BottomNavItem(
        screen       = Screen.Calendar,
        label        = "Calendario",
        icon         = Icons.Outlined.CalendarMonth,
        selectedIcon = Icons.Filled.CalendarMonth,
    ),
    BottomNavItem(
        screen       = Screen.Workout,
        label        = "Actividad",
        icon         = Icons.Outlined.FitnessCenter,
        selectedIcon = Icons.Filled.FitnessCenter,
    ),
)
