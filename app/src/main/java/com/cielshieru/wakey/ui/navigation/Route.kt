package com.cielshieru.wakey.ui.navigation

sealed class Route(val route: String) {
    object Home : Route("home")
    object Settings : Route("settings")
    object ConcessionPassCalculator : Route("concession_pass_calculator")
    object RingtonePicker : Route("ringtone_picker")
}
