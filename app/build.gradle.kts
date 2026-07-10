plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.google.services)
}

// ── AdMob configuration ──────────────────────────────────────────────────────
// Google sample IDs: return test ads for every request, never earn revenue, and can
// never generate invalid traffic. Safe for development and closed testing.
val testAdmobAppId = "ca-app-pub-3940256099942544~3347511713"
val testBannerAdUnitId = "ca-app-pub-3940256099942544/6300978111"
val testInterstitialAdUnitId = "ca-app-pub-3940256099942544/1033173712"
val testNativeAdUnitId = "ca-app-pub-3940256099942544/2247696110"

// Real FoodFit Scan AdMob IDs — wired into the `release` build type only.
val prodAdmobAppId = "ca-app-pub-6675028272966387~5874646346"
val prodBannerAdUnitId = "ca-app-pub-6675028272966387/5612169301"
val prodInterstitialAdUnitId = "ca-app-pub-6675028272966387/8889069336"
val prodNativeAdUnitId = "ca-app-pub-6675028272966387/4561564676"

// Flip closedTesting to real ad units with: ./gradlew bundleClosedTesting -PclosedTestingRealAds=true
val closedTestingRealAds =
    providers.gradleProperty("closedTestingRealAds").map { it.toBoolean() }.getOrElse(false)

/** Injects the AdMob app ID (manifest placeholder) and ad unit IDs (BuildConfig) per build type. */
fun com.android.build.api.dsl.ApplicationBuildType.adMobIds(
    appId: String,
    bannerId: String,
    interstitialId: String,
    nativeId: String,
    testAds: Boolean,
) {
    manifestPlaceholders["admobAppId"] = appId
    buildConfigField("boolean", "ADS_USE_TEST_UNITS", "$testAds")
    buildConfigField("String", "ADMOB_BANNER_AD_UNIT_ID", "\"$bannerId\"")
    buildConfigField("String", "ADMOB_INTERSTITIAL_AD_UNIT_ID", "\"$interstitialId\"")
    buildConfigField("String", "ADMOB_NATIVE_AD_UNIT_ID", "\"$nativeId\"")
}

android {
    namespace = "com.sephylon.foodfitscan"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.sephylon.foodfitscan"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            // Fully-sample AdMob config (sample app ID + sample ad units): zero risk while developing.
            adMobIds(
                appId = testAdmobAppId,
                bannerId = testBannerAdUnitId,
                interstitialId = testInterstitialAdUnitId,
                nativeId = testNativeAdUnitId,
                testAds = true,
            )
        }
        release {
            optimization {
                enable = false
            }
            // Real ads. Only ship this variant to the production track.
            adMobIds(
                appId = prodAdmobAppId,
                bannerId = prodBannerAdUnitId,
                interstitialId = prodInterstitialAdUnitId,
                nativeId = prodNativeAdUnitId,
                testAds = false,
            )
        }
        // Upload THIS variant to the Play Console closed-testing track: release-identical build
        // (real AdMob app ID, not debuggable, Play-signable) but Google sample ad units, so
        // testers can never generate invalid traffic against the real ad units.
        create("closedTesting") {
            initWith(getByName("release"))
            matchingFallbacks += "release"
            adMobIds(
                appId = prodAdmobAppId,
                bannerId = if (closedTestingRealAds) prodBannerAdUnitId else testBannerAdUnitId,
                interstitialId = if (closedTestingRealAds) prodInterstitialAdUnitId else testInterstitialAdUnitId,
                nativeId = if (closedTestingRealAds) prodNativeAdUnitId else testNativeAdUnitId,
                testAds = !closedTestingRealAds,
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

// AGP 9 creates host (unit) tests only for the debug build type by default. Enable them for
// every variant so AdConfigTest also verifies the release/closedTesting ad-unit wiring.
androidComponents {
    beforeVariants { variantBuilder ->
        variantBuilder.hostTests[com.android.build.api.variant.HostTestBuilder.UNIT_TEST_TYPE]
            ?.enable = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.datastore.preferences)
    implementation(libs.coil.compose)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)
    implementation(libs.mlkit.barcode.scanning)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.firestore)
    implementation(libs.play.services.ads)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
