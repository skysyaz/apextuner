package com.apextuner.app.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.apextuner.app.ui.cpu.CpuScreen
import com.apextuner.app.ui.dashboard.DashboardScreen
import com.apextuner.app.ui.display.DisplayScreen
import com.apextuner.app.ui.games.GameLibraryScreen
import com.apextuner.app.ui.gpu.GpuScreen
import com.apextuner.app.ui.network.VpnDnsScreen
import com.apextuner.app.ui.onboarding.OnboardingScreen
import com.apextuner.app.ui.profiles.ProfilesScreen
import com.apextuner.app.ui.settings.SettingsScreen

object Routes {
    const val ONBOARDING = "onboarding"
    const val DASHBOARD = "dashboard"
    const val CPU = "cpu"
    const val GPU = "gpu"
    const val DISPLAY = "display"
    const val NETWORK = "network"
    const val GAMES = "games"
    const val PROFILES = "profiles"
    const val SETTINGS = "settings"
}

@Composable
fun ApexNavHost(
    navController: NavHostController = rememberNavController(),
    onboardingComplete: Boolean
) {
    val start = if (onboardingComplete) Routes.DASHBOARD else Routes.ONBOARDING

    NavHost(
        navController = navController,
        startDestination = start,
        enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(220)) + fadeIn(tween(220)) },
        exitTransition = { fadeOut(tween(180)) },
        popEnterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(220)) + fadeIn(tween(220)) },
        popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(220)) + fadeOut(tween(180)) }
    ) {
        composable(Routes.ONBOARDING) {
            OnboardingScreen(onFinished = {
                navController.navigate(Routes.DASHBOARD) {
                    popUpTo(Routes.ONBOARDING) { inclusive = true }
                }
            })
        }
        composable(Routes.DASHBOARD) {
            DashboardScreen(
                onNavigate = { route -> navController.navigate(route) }
            )
        }
        composable(Routes.CPU) { CpuScreen() }
        composable(Routes.GPU) { GpuScreen() }
        composable(Routes.DISPLAY) { DisplayScreen() }
        composable(Routes.NETWORK) { VpnDnsScreen() }
        composable(Routes.GAMES) { GameLibraryScreen() }
        composable(Routes.PROFILES) { ProfilesScreen() }
        composable(Routes.SETTINGS) { SettingsScreen() }
    }
}
