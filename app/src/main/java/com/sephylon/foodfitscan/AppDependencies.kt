package com.sephylon.foodfitscan

import android.content.Context
import com.google.gson.Gson
import com.sephylon.foodfitscan.ads.AdConfig
import com.sephylon.foodfitscan.ads.InterstitialAdManager
import com.sephylon.foodfitscan.core.network.OpenFoodFactsClient
import com.sephylon.foodfitscan.data.device.DeviceRegionProvider
import com.sephylon.foodfitscan.data.device.LocaleDeviceRegionProvider
import com.sephylon.foodfitscan.data.local.CachedProductDao
import com.sephylon.foodfitscan.data.local.FoodFitDatabase
import com.sephylon.foodfitscan.data.local.ScanHistoryDao
import com.sephylon.foodfitscan.data.firebase.FirestoreProductSearchClient
import com.sephylon.foodfitscan.data.mapper.ProductDetailsCacheMapper
import com.sephylon.foodfitscan.data.mapper.ScanHistoryMapper
import com.sephylon.foodfitscan.data.preferences.foodPreferenceDataStore
import com.sephylon.foodfitscan.data.remote.OpenFoodFactsClientImpl
import com.sephylon.foodfitscan.data.repository.PreferenceRepositoryImpl
import com.sephylon.foodfitscan.data.repository.ProductRepositoryImpl
import com.sephylon.foodfitscan.data.repository.ProductSearchRepositoryImpl
import com.sephylon.foodfitscan.domain.repository.PreferenceRepository
import com.sephylon.foodfitscan.domain.repository.ProductRepository
import com.sephylon.foodfitscan.domain.repository.ProductSearchRepository

// Lightweight manual DI. Replace with Hilt/Dagger in a later phase.
internal object AppDependencies {
    private lateinit var appContext: Context
    private lateinit var database: FoodFitDatabase

    fun init(context: Context) {
        if (!::appContext.isInitialized) {
            appContext = context.applicationContext
        }
        if (!::database.isInitialized) {
            database = FoodFitDatabase.getInstance(context)
        }
    }

    val openFoodFactsClient: OpenFoodFactsClient by lazy {
        OpenFoodFactsClientImpl.create()
    }

    private val gson: Gson by lazy { Gson() }

    val cachedProductDao: CachedProductDao
        get() = database.cachedProductDao()

    val scanHistoryDao: ScanHistoryDao
        get() = database.scanHistoryDao()

    val productRepository: ProductRepository by lazy {
        ProductRepositoryImpl(
            client = openFoodFactsClient,
            cachedProductDao = cachedProductDao,
            scanHistoryDao = scanHistoryDao,
            cacheMapper = ProductDetailsCacheMapper(gson),
            historyMapper = ScanHistoryMapper(),
        )
    }

    val preferenceRepository: PreferenceRepository by lazy {
        PreferenceRepositoryImpl(appContext.foodPreferenceDataStore)
    }

    val productSearchRepository: ProductSearchRepository by lazy {
        ProductSearchRepositoryImpl(client = FirestoreProductSearchClient())
    }

    val deviceRegionProvider: DeviceRegionProvider by lazy {
        LocaleDeviceRegionProvider(appContext)
    }

    val interstitialAdManager: InterstitialAdManager by lazy {
        InterstitialAdManager(
            context = appContext,
            adUnitId = AdConfig.interstitialAdUnitId,
            dataStore = appContext.foodPreferenceDataStore,
        )
    }
}
