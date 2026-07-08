package com.sephylon.foodfitscan.data.preferences

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore

val Context.foodPreferenceDataStore by preferencesDataStore(name = "food_preferences")
