package com.sephylon.foodfitscan.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.sephylon.foodfitscan.AppViewModel
import com.sephylon.foodfitscan.ui.about.AboutScreen
import com.sephylon.foodfitscan.ui.history.HistoryScreen
import com.sephylon.foodfitscan.ui.home.HomeScreen
import com.sephylon.foodfitscan.ui.onboarding.OnboardingScreen
import com.sephylon.foodfitscan.ui.product.ProductDetailScreen
import com.sephylon.foodfitscan.ui.scanner.ScannerScreen
import com.sephylon.foodfitscan.ui.settings.SettingsScreen

@Composable
fun AppNavigation(appViewModel: AppViewModel = viewModel(factory = AppViewModel.Factory)) {
    val resolvedStartDest by appViewModel.startDestination.collectAsStateWithLifecycle()

    if (resolvedStartDest == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    // Lock the start destination so NavHost is not recreated if the DataStore later emits again.
    val startDest = remember { resolvedStartDest!! }
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = startDest,
    ) {
        composable(Screen.Onboarding.route) {
            OnboardingScreen(
                onComplete = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(startDest) { inclusive = true }
                    }
                },
            )
        }
        composable(Screen.Home.route) {
            HomeScreen(
                onScanClick = { navController.navigate(Screen.Scanner.route) },
                onOpenProduct = { barcode ->
                    navController.navigate(Screen.ProductDetail.createRoute(barcode))
                },
                onSettingsClick = { navController.navigate(Screen.Settings.route) },
                onAboutClick = { navController.navigate(Screen.About.route) },
            )
        }
        composable(Screen.Scanner.route) {
            ScannerScreen(
                onBack = { navController.popBackStack() },
                onBarcodeScanned = { barcode ->
                    navController.navigate(Screen.ProductDetail.createRoute(barcode))
                },
            )
        }
        composable(
            route = Screen.ProductDetail.route,
            arguments = listOf(navArgument("barcode") { type = NavType.StringType }),
        ) { backStackEntry ->
            val barcode = backStackEntry.arguments?.getString("barcode") ?: ""
            ProductDetailScreen(
                barcode = barcode,
                onBack = { navController.popBackStack() },
                onScanAnother = {
                    navController.navigate(Screen.Scanner.route) {
                        popUpTo(Screen.Home.route)
                    }
                },
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                onNavigateToProduct = { altBarcode ->
                    navController.navigate(Screen.ProductDetail.createRoute(altBarcode))
                },
            )
        }
        composable(Screen.History.route) {
            HistoryScreen(
                onBack = { navController.popBackStack() },
                onNavigateToProduct = { barcode ->
                    navController.navigate(Screen.ProductDetail.createRoute(barcode))
                },
            )
        }
        composable(Screen.Settings.route) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onReviewOnboarding = {
                    navController.navigate(Screen.Onboarding.route)
                },
            )
        }
        composable(Screen.About.route) {
            AboutScreen(onBack = { navController.popBackStack() })
        }
    }
}
