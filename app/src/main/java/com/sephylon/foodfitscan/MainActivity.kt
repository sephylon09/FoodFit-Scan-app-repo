package com.sephylon.foodfitscan

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.sephylon.foodfitscan.ui.navigation.AppNavigation
import com.sephylon.foodfitscan.ui.theme.FoodFitScanTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FoodFitScanTheme {
                AppNavigation()
            }
        }
    }
}
