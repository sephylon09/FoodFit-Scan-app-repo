package com.sephylon.foodfitscan.domain.rules

import com.sephylon.foodfitscan.domain.model.AlternativeProduct
import com.sephylon.foodfitscan.domain.model.ProductDetails
import com.sephylon.foodfitscan.domain.model.SuitabilityLevel
import com.sephylon.foodfitscan.domain.model.SuitabilityResult
import com.sephylon.foodfitscan.domain.model.UserFoodPreferences

/**
 * Turns raw category-search candidates into a short, curated list of alternatives.
 *
 * Category similarity comes first: candidates must share enough specific category tags
 * with the scanned product to plausibly be the same *kind* of food (a protein bar should
 * suggest other bars, not water or plain chocolate). Only then are candidates ordered by
 * how healthy/suitable they are. When too few candidates are similar enough, the result
 * is deliberately short — a few good suggestions beat a page of unrelated ones.
 */
object AlternativeRanker {

    private const val MAX_ALTERNATIVES = 4

    fun rank(
        candidates: List<ProductDetails>,
        currentBarcode: String,
        preferences: UserFoodPreferences,
        baseCategoryTags: List<String>? = null,
        scorer: SuitabilityScorer = SuitabilityScorer(),
    ): List<AlternativeProduct> {
        val baseSpecific = AlternativeCategorySelector.specificCategoryTags(baseCategoryTags).toSet()
        // With 2+ specific tags on the scanned product, require candidates to share at
        // least 2 (the searched tag plus one more related tag). With only one, that one
        // is all we can require. Without category data there is nothing to compare.
        val requiredOverlap = baseSpecific.size.coerceAtMost(2)

        return candidates
            .asSequence()
            .filter { it.barcode != currentBarcode }
            .filter { it.barcode.isNotBlank() }
            .filter { it.name.isNotBlank() && it.name != "Unknown product" }
            .map { product -> RankedCandidate(product, categoryOverlap(product, baseSpecific), scorer.score(product, preferences)) }
            .filter { it.overlap >= requiredOverlap }
            .filter { it.suitability.level != SuitabilityLevel.AVOID }
            .sortedWith(candidateComparator())
            .take(MAX_ALTERNATIVES)
            .map { it.toAlternativeProduct() }
            .toList()
    }

    private data class RankedCandidate(
        val product: ProductDetails,
        val overlap: Int,
        val suitability: SuitabilityResult,
    ) {
        fun toAlternativeProduct() = AlternativeProduct(
            barcode = product.barcode,
            productName = product.name,
            brand = product.brand,
            imageFrontUrl = product.imageFrontUrl,
            nutriScore = product.nutriScore,
            novaGroup = product.novaGroup,
            suitabilityLevel = suitability.level,
            shortReasons = suitability.reasons.take(2),
            sugarsPer100g = product.nutrition?.sugarsPer100g,
            saltPer100g = product.nutrition?.saltPer100g,
            saturatedFatPer100g = product.nutrition?.saturatedFatPer100g,
        )
    }

    private fun categoryOverlap(product: ProductDetails, baseSpecific: Set<String>): Int {
        if (baseSpecific.isEmpty()) return 0
        return AlternativeCategorySelector.specificCategoryTags(product.categoriesTags)
            .count { it in baseSpecific }
    }

    // Same product type first (more shared categories), then suitability, then the
    // established healthier-first ordering.
    private fun candidateComparator(): Comparator<RankedCandidate> =
        compareByDescending<RankedCandidate> { it.overlap }
            .thenBy { it.suitability.level.priority }
            .thenBy { nutriScorePriority(it.product.nutriScore) }
            .thenBy { it.product.novaGroup ?: 5 }
            .thenBy { it.product.nutrition?.sugarsPer100g ?: Double.MAX_VALUE }
            .thenBy { it.product.nutrition?.saltPer100g ?: Double.MAX_VALUE }
            .thenBy { it.product.nutrition?.saturatedFatPer100g ?: Double.MAX_VALUE }
            .thenBy { if (it.product.imageFrontUrl != null) 0 else 1 }

    private fun nutriScorePriority(grade: String?): Int = when (grade?.lowercase()) {
        "a" -> 0
        "b" -> 1
        "c" -> 2
        "d" -> 3
        "e" -> 4
        else -> 5
    }
}
