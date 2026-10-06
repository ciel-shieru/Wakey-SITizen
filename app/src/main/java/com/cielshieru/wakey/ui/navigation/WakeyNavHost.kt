package com.cielshieru.wakey.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.cielshieru.wakey.ui.home.HomeScreen
import com.cielshieru.wakey.ui.settings.SettingsScreen

@Composable
fun WakeyNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = androidx.navigation.compose.rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = Route.Home.route,
        modifier = modifier
    ) {
        composable(Route.Home.route) {
            HomeScreen(onNavigateToSettings = { navController.navigate(Route.Settings.route) })
        }
        composable(Route.Settings.route) {
            SettingsScreen(onNavigateBack = { navController.popBackStack() })
        }
    }
}
