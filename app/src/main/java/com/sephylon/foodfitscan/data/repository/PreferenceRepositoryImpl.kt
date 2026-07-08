package com.sephylon.foodfitscan.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import com.sephylon.foodfitscan.data.preferences.PreferenceKeys
import com.sephylon.foodfitscan.domain.model.NutritionDisplayOption
import com.sephylon.foodfitscan.domain.model.UserFoodPreferences
import com.sephylon.foodfitscan.domain.repository.PreferenceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

class PreferenceRepositoryImpl(
    private val dataStore: DataStore<Preferences>,
) : PreferenceRepository {

    override fun getUserPreferences(): Flow<UserFoodPreferences> =
        dataStore.data
            .catch { emit(emptyPreferences()) }
            .map { it.toUserFoodPreferences() }

    override suspend fun saveUserPreferences(preferences: UserFoodPreferences) {
        dataStore.edit { prefs ->
            prefs[PreferenceKeys.ALLERGENS_TO_AVOID] = preferences.allergensToAvoid
            prefs[PreferenceKeys.ADDITIVES_TO_AVOID] = preferences.additivesToAvoid
            prefs[PreferenceKeys.AVOID_ULTRA_PROCESSED] = preferences.avoidUltraProcessed
            if (preferences.maxSugarsPer100g != null) {
                prefs[PreferenceKeys.MAX_SUGARS_PER_100G] = preferences.maxSugarsPer100g
            } else {
                prefs.remove(PreferenceKeys.MAX_SUGARS_PER_100G)
            }
            if (preferences.maxSaltPer100g != null) {
                prefs[PreferenceKeys.MAX_SALT_PER_100G] = preferences.maxSaltPer100g
            } else {
                prefs.remove(PreferenceKeys.MAX_SALT_PER_100G)
            }
            if (preferences.maxSaturatedFatPer100g != null) {
                prefs[PreferenceKeys.MAX_SATURATED_FAT_PER_100G] = preferences.maxSaturatedFatPer100g
            } else {
                prefs.remove(PreferenceKeys.MAX_SATURATED_FAT_PER_100G)
            }
        }
    }

    override fun observeOnboardingCompleted(): Flow<Boolean> =
        dataStore.data
            .catch { emit(emptyPreferences()) }
            .map { it[PreferenceKeys.ONBOARDING_COMPLETED] ?: false }

    override suspend fun setOnboardingCompleted(completed: Boolean) {
        dataStore.edit { prefs ->
            prefs[PreferenceKeys.ONBOARDING_COMPLETED] = completed
        }
    }

    override fun observeSelectedNutritionFields(): Flow<Set<String>> =
        dataStore.data
            .catch { emit(emptyPreferences()) }
            .map { prefs ->
                val stored = prefs[PreferenceKeys.SELECTED_NUTRITION_FIELDS]
                // Fall back to the practical default when nothing is stored or the set is empty.
                if (stored.isNullOrEmpty()) NutritionDisplayOption.DEFAULT_KEYS else stored
            }

    override suspend fun saveSelectedNutritionFields(fields: Set<String>) {
        dataStore.edit { prefs ->
            if (fields.isEmpty()) {
                prefs.remove(PreferenceKeys.SELECTED_NUTRITION_FIELDS)
            } else {
                prefs[PreferenceKeys.SELECTED_NUTRITION_FIELDS] = fields
            }
        }
    }

    private fun Preferences.toUserFoodPreferences() = UserFoodPreferences(
        allergensToAvoid = this[PreferenceKeys.ALLERGENS_TO_AVOID] ?: emptySet(),
        additivesToAvoid = this[PreferenceKeys.ADDITIVES_TO_AVOID] ?: emptySet(),
        avoidUltraProcessed = this[PreferenceKeys.AVOID_ULTRA_PROCESSED] ?: false,
        maxSugarsPer100g = this[PreferenceKeys.MAX_SUGARS_PER_100G],
        maxSaltPer100g = this[PreferenceKeys.MAX_SALT_PER_100G],
        maxSaturatedFatPer100g = this[PreferenceKeys.MAX_SATURATED_FAT_PER_100G],
    )
}
