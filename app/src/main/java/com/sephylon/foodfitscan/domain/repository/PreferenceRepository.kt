package com.sephylon.foodfitscan.domain.repository

import com.sephylon.foodfitscan.domain.model.UserFoodPreferences
import kotlinx.coroutines.flow.Flow

interface PreferenceRepository {
    fun getUserPreferences(): Flow<UserFoodPreferences>
    suspend fun saveUserPreferences(preferences: UserFoodPreferences)
    fun observeOnboardingCompleted(): Flow<Boolean>
    suspend fun setOnboardingCompleted(completed: Boolean)

    /**
     * Display-only preference: which nutrition fields the user wants shown prominently.
     * Emits the default selection when nothing is stored or the stored set is empty.
     * This does NOT affect suitability scoring.
     */
    fun observeSelectedNutritionFields(): Flow<Set<String>>
    suspend fun saveSelectedNutritionFields(fields: Set<String>)
}
