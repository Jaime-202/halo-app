package com.lifedashboard.presentation.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import com.lifedashboard.presentation.theme.HaloMotion
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.lifedashboard.presentation.screen.dashboard.DashboardScreen
import com.lifedashboard.presentation.screen.health.HealthScreen
import com.lifedashboard.presentation.screen.settings.SettingsScreen
import com.lifedashboard.presentation.screen.workout.WorkoutScreen

@Composable
fun NavGraph(navController: NavHostController) {
    NavHost(
        navController    = navController,
        startDestination = Screen.Dashboard.route,
        // "Fade-through": la pantalla anterior se desvanece y la nueva aparece con un ligero zoom
        enterTransition  = {
            fadeIn(tween(HaloMotion.MEDIUM, delayMillis = 60, easing = HaloMotion.EmphasizedDecelerate)) +
                scaleIn(tween(HaloMotion.MEDIUM, delayMillis = 60, easing = HaloMotion.EmphasizedDecelerate), initialScale = 0.96f)
        },
        exitTransition   = { fadeOut(tween(HaloMotion.FAST, easing = HaloMotion.EmphasizedAccelerate)) },
        popEnterTransition = {
            fadeIn(tween(HaloMotion.MEDIUM, delayMillis = 60, easing = HaloMotion.EmphasizedDecelerate)) +
                scaleIn(tween(HaloMotion.MEDIUM, delayMillis = 60), initialScale = 1.03f)
        },
        popExitTransition = {
            fadeOut(tween(HaloMotion.FAST)) + scaleOut(tween(HaloMotion.FAST), targetScale = 0.96f)
        }
    ) {
        composable(Screen.Dashboard.route) {
            DashboardScreen(
                onNavigateToHealth = { tab ->
                    navController.navigate(Screen.Health.createRoute(tab)) {
                        launchSingleTop = true
                    }
                },
                onNavigateToWorkout = {
                    navController.navigate(Screen.Workout.route) {
                        launchSingleTop = true
                    }
                },
                onNavigateToNutrition = {
                    navController.navigate(Screen.Nutrition.route) {
                        launchSingleTop = true
                    }
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route) {
                        launchSingleTop = true
                    }
                }
            )
        }
        composable(Screen.Workout.route) {
            WorkoutScreen(
                onBack = {
                    navController.popBackStack(Screen.Dashboard.route, inclusive = false)
                }
            )
        }
        composable(Screen.Nutrition.route) {
            com.lifedashboard.presentation.screen.nutrition.NutritionScreen(
                onBack = {
                    navController.popBackStack(Screen.Dashboard.route, inclusive = false)
                }
            )
        }
        composable(
            route = Screen.Health.route,
            arguments = listOf(
                navArgument("tab") {
                    type = NavType.StringType
                    defaultValue = "PASOS"
                }
            )
        ) { backStackEntry ->
            val tab = backStackEntry.arguments?.getString("tab") ?: "PASOS"
            HealthScreen(
                initialTabName = tab,
                onBack = {
                    navController.popBackStack(Screen.Dashboard.route, inclusive = false)
                }
            )
        }
        composable(Screen.Calendar.route) {
            com.lifedashboard.presentation.screen.calendar.CalendarScreen()
        }
        composable(Screen.Settings.route) {
            SettingsScreen()
        }
    }
}
