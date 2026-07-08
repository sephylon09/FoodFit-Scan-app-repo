package com.sephylon.foodfitscan.domain.rules

import com.sephylon.foodfitscan.domain.model.AllergenOption
import com.sephylon.foodfitscan.domain.model.ProductDetails
import com.sephylon.foodfitscan.domain.model.SuitabilityLevel
import com.sephylon.foodfitscan.domain.model.SuitabilityResult
import com.sephylon.foodfitscan.domain.model.UserFoodPreferences

class AllergenSuitabilityRule {
    fun evaluate(product: ProductDetails, preferences: UserFoodPreferences): SuitabilityResult {
        val avoided = preferences.allergensToAvoid
        if (avoided.isEmpty()) return SuitabilityResult(SuitabilityLevel.GOOD_MATCH)

        val avoidReasons = mutableListOf<String>()
        val cautionReasons = mutableListOf<String>()
        val unknownReasons = mutableListOf<String>()

        val directMatches = avoided.filter { key ->
            product.allergensTags.any { it.equals(key, ignoreCase = true) }
        }
        directMatches.forEach { key ->
            avoidReasons.add("Contains ${displayName(key)}")
        }

        val traceMatches = avoided.filter { key ->
            key !in directMatches && product.tracesTags.any { it.equals(key, ignoreCase = true) }
        }
        traceMatches.forEach { key ->
            cautionReasons.add("May contain traces of ${displayName(key)}")
        }

        // No allergen or trace data at all — warn about incomplete data
        if (product.allergensTags.isEmpty() && product.tracesTags.isEmpty()) {
            unknownReasons.add("Allergen data is incomplete. Check the packaging.")
        }

        val allReasons = avoidReasons + cautionReasons + unknownReasons
        val level = when {
            avoidReasons.isNotEmpty() -> SuitabilityLevel.AVOID
            cautionReasons.isNotEmpty() -> SuitabilityLevel.CAUTION
            unknownReasons.isNotEmpty() -> SuitabilityLevel.UNKNOWN
            else -> SuitabilityLevel.GOOD_MATCH
        }
        return SuitabilityResult(level, allReasons)
    }

    private fun displayName(key: String): String =
        AllergenOption.ALL.find { it.key == key }?.displayName ?: key
}
