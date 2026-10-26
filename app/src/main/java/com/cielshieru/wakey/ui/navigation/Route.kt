package com.cielshieru.wakey.ui.navigation

sealed class Route(val route: String) {
    object Home : Route("home")
    object Settings : Route("settings")
    object ConcessionPassCalculator : Route("concession_pass_calculator")
    object ConcessionPassBestStartDates : Route("concession_pass_best_start_dates")
    object RingtonePicker : Route("ringtone_picker")
}
