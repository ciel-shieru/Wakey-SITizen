package com.cielshieru.wakey.ui.navigation

sealed class Route(val route: String) {
    object Home : Route("home")
    object Settings : Route("settings")
}
