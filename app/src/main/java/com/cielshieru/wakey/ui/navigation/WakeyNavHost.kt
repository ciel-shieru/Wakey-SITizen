package com.cielshieru.wakey.ui.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.cielshieru.wakey.ui.home.HomeScreen
import com.cielshieru.wakey.ui.ringtone.RingtonePickerScreen
import com.cielshieru.wakey.ui.settings.SettingsScreen
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WakeyNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = androidx.navigation.compose.rememberNavController()
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    NavHost(
        navController = navController,
        startDestination = Route.Home.route,
        modifier = modifier
    ) {
        composable(Route.Home.route) {
            HomeScreen(onNavigateToSettings = { navController.navigate(Route.Settings.route) })
        }
        composable(Route.Settings.route) {
            Column(modifier = Modifier.fillMaxSize()) {
                SettingsScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onShowSnackbar = { message ->
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar(message)
                        }
                    },
                    onNavigateToRingtonePicker = { navController.navigate(Route.RingtonePicker.route) }
                )
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        composable(Route.RingtonePicker.route) {
            Column(modifier = Modifier.fillMaxSize()) {
                RingtonePickerScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onShowSnackbar = { message ->
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar(message)
                        }
                    }
                )
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
