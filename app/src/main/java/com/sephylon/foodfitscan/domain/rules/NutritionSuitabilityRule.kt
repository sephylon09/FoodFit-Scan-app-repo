package com.sephylon.foodfitscan.domain.rules

import com.sephylon.foodfitscan.domain.model.ProductDetails
import com.sephylon.foodfitscan.domain.model.SuitabilityLevel
import com.sephylon.foodfitscan.domain.model.SuitabilityResult
import com.sephylon.foodfitscan.domain.model.UserFoodPreferences

class NutritionSuitabilityRule {
    fun evaluate(product: ProductDetails, preferences: UserFoodPreferences): SuitabilityResult {
        val nutrition = product.nutrition
        val cautionReasons = mutableListOf<String>()
        val unknownReasons = mutableListOf<String>()

        preferences.maxSugarsPer100g?.let { cap ->
            when (val v = nutrition?.sugarsPer100g) {
                null -> unknownReasons.add("Sugar value is unavailable.")
                else -> if (v > cap) cautionReasons.add("Sugar is above your limit: ${"%.1f".format(v)}g per 100g")
            }
        }

        preferences.maxSaltPer100g?.let { cap ->
            when (val v = nutrition?.saltPer100g) {
                null -> unknownReasons.add("Salt value is unavailable.")
                else -> if (v > cap) cautionReasons.add("Salt is above your limit: ${"%.1f".format(v)}g per 100g")
            }
        }

        preferences.maxSaturatedFatPer100g?.let { cap ->
            when (val v = nutrition?.saturatedFatPer100g) {
                null -> unknownReasons.add("Saturated fat value is unavailable.")
                else -> if (v > cap) cautionReasons.add("Saturated fat is above your limit: ${"%.1f".format(v)}g per 100g")
            }
        }

        val allReasons = cautionReasons + unknownReasons
        val level = when {
            cautionReasons.isNotEmpty() -> SuitabilityLevel.CAUTION
            unknownReasons.isNotEmpty() -> SuitabilityLevel.UNKNOWN
            else -> SuitabilityLevel.GOOD_MATCH
        }
        return SuitabilityResult(level, allReasons)
    }
}
