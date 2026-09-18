package com.auracommunityact.missiongtamobile.ui.screens

sealed class Screen(val route: String) {
    object Startup : Screen("startup")
    object Home : Screen("home")
    object Game : Screen("game")
    object Diagnostics : Screen("diagnostics")
    object Settings : Screen("settings")
}
