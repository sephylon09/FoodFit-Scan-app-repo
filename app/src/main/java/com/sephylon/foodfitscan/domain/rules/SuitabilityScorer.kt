package com.sephylon.foodfitscan.domain.rules

import com.sephylon.foodfitscan.domain.model.ProductDetails
import com.sephylon.foodfitscan.domain.model.SuitabilityLevel
import com.sephylon.foodfitscan.domain.model.SuitabilityResult
import com.sephylon.foodfitscan.domain.model.UserFoodPreferences

class SuitabilityScorer(
    private val allergenRule: AllergenSuitabilityRule = AllergenSuitabilityRule(),
    private val additiveRule: AdditiveSuitabilityRule = AdditiveSuitabilityRule(),
    private val novaRule: NovaSuitabilityRule = NovaSuitabilityRule(),
    private val nutritionRule: NutritionSuitabilityRule = NutritionSuitabilityRule(),
) {
    fun score(product: ProductDetails, preferences: UserFoodPreferences): SuitabilityResult {
        if (!preferences.hasAnyPreference()) {
            return SuitabilityResult(
                level = SuitabilityLevel.UNKNOWN,
                reasons = listOf("Set your food preferences to get personalised guidance."),
                noPreferencesConfigured = true,
            )
        }

        val results = listOf(
            allergenRule.evaluate(product, preferences),
            additiveRule.evaluate(product, preferences),
            novaRule.evaluate(product, preferences),
            nutritionRule.evaluate(product, preferences),
        )

        val allReasons = results.flatMap { it.reasons }
        val level = results
            .map { it.level }
            .maxByOrNull { it.priority }
            ?: SuitabilityLevel.GOOD_MATCH

        return SuitabilityResult(level = level, reasons = allReasons)
    }
}

private fun UserFoodPreferences.hasAnyPreference(): Boolean =
    allergensToAvoid.isNotEmpty() ||
        additivesToAvoid.isNotEmpty() ||
        avoidUltraProcessed ||
        maxSugarsPer100g != null ||
        maxSaltPer100g != null ||
        maxSaturatedFatPer100g != null
