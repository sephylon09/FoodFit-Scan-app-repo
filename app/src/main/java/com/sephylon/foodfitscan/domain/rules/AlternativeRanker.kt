package com.sephylon.foodfitscan.domain.rules

import com.sephylon.foodfitscan.domain.model.AlternativeProduct
import com.sephylon.foodfitscan.domain.model.ProductDetails
import com.sephylon.foodfitscan.domain.model.SuitabilityLevel
import com.sephylon.foodfitscan.domain.model.UserFoodPreferences

object AlternativeRanker {

    private const val MAX_ALTERNATIVES = 5

    fun rank(
        candidates: List<ProductDetails>,
        currentBarcode: String,
        preferences: UserFoodPreferences,
        scorer: SuitabilityScorer = SuitabilityScorer(),
    ): List<AlternativeProduct> {
        return candidates
            .filter { it.barcode != currentBarcode }
            .filter { it.barcode.isNotBlank() }
            .filter { it.name.isNotBlank() && it.name != "Unknown product" }
            .map { product ->
                val result = scorer.score(product, preferences)
                AlternativeProduct(
                    barcode = product.barcode,
                    productName = product.name,
                    brand = product.brand,
                    imageFrontUrl = product.imageFrontUrl,
                    nutriScore = product.nutriScore,
                    novaGroup = product.novaGroup,
                    suitabilityLevel = result.level,
                    shortReasons = result.reasons.take(2),
                    sugarsPer100g = product.nutrition?.sugarsPer100g,
                    saltPer100g = product.nutrition?.saltPer100g,
                    saturatedFatPer100g = product.nutrition?.saturatedFatPer100g,
                )
            }
            .filter { it.suitabilityLevel != SuitabilityLevel.AVOID }
            .sortedWith(alternativeComparator())
            .take(MAX_ALTERNATIVES)
    }

    private fun alternativeComparator(): Comparator<AlternativeProduct> =
        compareBy<AlternativeProduct> { it.suitabilityLevel.priority }
            .thenBy { nutriScorePriority(it.nutriScore) }
            .thenBy { it.novaGroup ?: 5 }
            .thenBy { it.sugarsPer100g ?: Double.MAX_VALUE }
            .thenBy { it.saltPer100g ?: Double.MAX_VALUE }
            .thenBy { it.saturatedFatPer100g ?: Double.MAX_VALUE }
            .thenBy { if (it.imageFrontUrl != null) 0 else 1 }

    private fun nutriScorePriority(grade: String?): Int = when (grade?.lowercase()) {
        "a" -> 0
        "b" -> 1
        "c" -> 2
        "d" -> 3
        "e" -> 4
        else -> 5
    }
}
