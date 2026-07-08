package com.sephylon.foodfitscan.domain.rules

import com.sephylon.foodfitscan.domain.model.AdditiveOption
import com.sephylon.foodfitscan.domain.model.ProductDetails
import com.sephylon.foodfitscan.domain.model.SuitabilityLevel
import com.sephylon.foodfitscan.domain.model.SuitabilityResult
import com.sephylon.foodfitscan.domain.model.UserFoodPreferences

class AdditiveSuitabilityRule {
    fun evaluate(product: ProductDetails, preferences: UserFoodPreferences): SuitabilityResult {
        val avoidedCategories = preferences.additivesToAvoid
        if (avoidedCategories.isEmpty()) return SuitabilityResult(SuitabilityLevel.GOOD_MATCH)

        if (product.additivesTags.isEmpty()) {
            return SuitabilityResult(
                level = SuitabilityLevel.UNKNOWN,
                reasons = listOf("Additive data is incomplete."),
            )
        }

        val matchedCategories = AdditiveCategoryMapper.matchingCategories(product.additivesTags, avoidedCategories)
        if (matchedCategories.isEmpty()) return SuitabilityResult(SuitabilityLevel.GOOD_MATCH)

        val reasons = matchedCategories.map { category ->
            val name = AdditiveOption.ALL.find { it.key == category }?.displayName ?: category
            "Contains additive from avoided group: ${name.lowercase()}"
        }
        return SuitabilityResult(level = SuitabilityLevel.CAUTION, reasons = reasons)
    }
}
