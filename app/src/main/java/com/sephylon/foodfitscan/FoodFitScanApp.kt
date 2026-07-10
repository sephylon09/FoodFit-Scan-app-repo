package com.sephylon.foodfitscan

import android.app.Application
import com.sephylon.foodfitscan.ads.AdsInitializer

class FoodFitScanApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppDependencies.init(this)
        AdsInitializer.initialize(this)
    }
}
