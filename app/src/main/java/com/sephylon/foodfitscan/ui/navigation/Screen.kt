package com.sephylon.foodfitscan.ui.navigation

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Onboarding : Screen("onboarding")
    data object Scanner : Screen("scanner")
    data object History : Screen("history")
    data object Settings : Screen("settings")
    data object About : Screen("about")
    data object ProductDetail : Screen("product_detail/{barcode}") {
        fun createRoute(barcode: String) = "product_detail/$barcode"
    }
}
