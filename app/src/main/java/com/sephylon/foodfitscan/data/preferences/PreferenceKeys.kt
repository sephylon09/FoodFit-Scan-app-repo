package com.sephylon.foodfitscan.data.preferences

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey

internal object PreferenceKeys {
    val ALLERGENS_TO_AVOID = stringSetPreferencesKey("allergens_to_avoid")
    val ADDITIVES_TO_AVOID = stringSetPreferencesKey("additives_to_avoid")
    val AVOID_ULTRA_PROCESSED = booleanPreferencesKey("avoid_ultra_processed")
    val MAX_SUGARS_PER_100G = doublePreferencesKey("max_sugars_per_100g")
    val MAX_SALT_PER_100G = doublePreferencesKey("max_salt_per_100g")
    val MAX_SATURATED_FAT_PER_100G = doublePreferencesKey("max_saturated_fat_per_100g")
    val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
    val SELECTED_NUTRITION_FIELDS = stringSetPreferencesKey("selected_nutrition_fields")
    val SEARCH_COUNTRY = stringPreferencesKey("search_country")
}
