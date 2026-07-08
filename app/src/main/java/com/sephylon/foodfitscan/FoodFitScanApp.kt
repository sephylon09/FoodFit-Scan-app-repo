package com.sephylon.foodfitscan

import android.app.Application

class FoodFitScanApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppDependencies.init(this)
    }
}
