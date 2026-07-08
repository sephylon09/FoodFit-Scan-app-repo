package com.sephylon.foodfitscan.domain.rules

import com.sephylon.foodfitscan.domain.model.ProductDetails
import com.sephylon.foodfitscan.domain.model.SuitabilityLevel
import com.sephylon.foodfitscan.domain.model.SuitabilityResult
import com.sephylon.foodfitscan.domain.model.UserFoodPreferences

class NovaSuitabilityRule {
    fun evaluate(product: ProductDetails, preferences: UserFoodPreferences): SuitabilityResult {
        if (!preferences.avoidUltraProcessed) return SuitabilityResult(SuitabilityLevel.GOOD_MATCH)

        return when (product.novaGroup) {
            4 -> SuitabilityResult(
                level = SuitabilityLevel.CAUTION,
                reasons = listOf("NOVA 4: ultra-processed food"),
            )
            null -> SuitabilityResult(
                level = SuitabilityLevel.UNKNOWN,
                reasons = listOf("NOVA processing level is unavailable."),
            )
            else -> SuitabilityResult(SuitabilityLevel.GOOD_MATCH)
        }
    }
}
