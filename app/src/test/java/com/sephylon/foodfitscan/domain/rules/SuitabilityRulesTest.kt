package com.sephylon.foodfitscan.domain.rules

import com.sephylon.foodfitscan.domain.model.NutritionFacts
import com.sephylon.foodfitscan.domain.model.ProductDetails
import com.sephylon.foodfitscan.domain.model.SuitabilityLevel
import com.sephylon.foodfitscan.domain.model.UserFoodPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SuitabilityRulesTest {

    private val scorer = SuitabilityScorer()
    private val allergenRule = AllergenSuitabilityRule()
    private val additiveRule = AdditiveSuitabilityRule()
    private val novaRule = NovaSuitabilityRule()
    private val nutritionRule = NutritionSuitabilityRule()

    // ── Scorer: top-level tests ──────────────────────────────────────────────

    @Test
    fun `no preferences configured returns UNKNOWN with noPreferencesConfigured flag`() {
        val result = scorer.score(makeProduct(), makePreferences())
        assertEquals(SuitabilityLevel.UNKNOWN, result.level)
        assertTrue(result.noPreferencesConfigured)
        assertTrue(result.reasons.any { "preferences" in it.lowercase() })
    }

    @Test
    fun `direct allergen match returns AVOID`() {
        val result = scorer.score(
            makeProduct(allergensTags = listOf("en:milk")),
            makePreferences(allergensToAvoid = setOf("en:milk")),
        )
        assertEquals(SuitabilityLevel.AVOID, result.level)
        assertTrue(result.reasons.any { "Contains Milk" in it })
    }

    @Test
    fun `trace allergen match returns CAUTION`() {
        val result = scorer.score(
            makeProduct(allergensTags = listOf("en:soy"), tracesTags = listOf("en:milk")),
            makePreferences(allergensToAvoid = setOf("en:milk")),
        )
        assertEquals(SuitabilityLevel.CAUTION, result.level)
        assertTrue(result.reasons.any { "traces of Milk" in it })
    }

    @Test
    fun `missing allergen data with allergen preference returns UNKNOWN`() {
        val result = scorer.score(
            makeProduct(allergensTags = emptyList(), tracesTags = emptyList()),
            makePreferences(allergensToAvoid = setOf("en:milk")),
        )
        assertEquals(SuitabilityLevel.UNKNOWN, result.level)
        assertTrue(result.reasons.any { "incomplete" in it.lowercase() })
    }

    @Test
    fun `avoid NOVA 4 enabled and product is NOVA 4 returns CAUTION`() {
        val result = scorer.score(
            makeProduct(novaGroup = 4, allergensTags = listOf("en:soy")),
            makePreferences(avoidUltraProcessed = true, allergensToAvoid = setOf("en:milk")),
        )
        assertEquals(SuitabilityLevel.CAUTION, result.level)
        assertTrue(result.reasons.any { "NOVA 4" in it })
    }

    @Test
    fun `NOVA missing with avoid NOVA 4 enabled returns at least UNKNOWN`() {
        val result = scorer.score(
            makeProduct(novaGroup = null, allergensTags = listOf("en:soy")),
            makePreferences(avoidUltraProcessed = true, allergensToAvoid = setOf("en:milk")),
        )
        assertTrue(result.level == SuitabilityLevel.UNKNOWN || result.level == SuitabilityLevel.CAUTION)
        assertTrue(result.reasons.any { "unavailable" in it.lowercase() })
    }

    @Test
    fun `sugar above user cap returns CAUTION`() {
        val result = scorer.score(
            makeProduct(nutrition = NutritionFacts(sugarsPer100g = 15.0)),
            makePreferences(maxSugarsPer100g = 10.0),
        )
        assertEquals(SuitabilityLevel.CAUTION, result.level)
        assertTrue(result.reasons.any { "Sugar" in it && "above your limit" in it })
    }

    @Test
    fun `salt above user cap returns CAUTION`() {
        val result = scorer.score(
            makeProduct(nutrition = NutritionFacts(saltPer100g = 2.5)),
            makePreferences(maxSaltPer100g = 1.5),
        )
        assertEquals(SuitabilityLevel.CAUTION, result.level)
        assertTrue(result.reasons.any { "Salt" in it && "above your limit" in it })
    }

    @Test
    fun `saturated fat above user cap returns CAUTION`() {
        val result = scorer.score(
            makeProduct(nutrition = NutritionFacts(saturatedFatPer100g = 8.0)),
            makePreferences(maxSaturatedFatPer100g = 5.0),
        )
        assertEquals(SuitabilityLevel.CAUTION, result.level)
        assertTrue(result.reasons.any { "Saturated fat" in it && "above your limit" in it })
    }

    @Test
    fun `missing sugar value with cap set returns at least UNKNOWN`() {
        val result = scorer.score(
            makeProduct(nutrition = NutritionFacts(sugarsPer100g = null)),
            makePreferences(maxSugarsPer100g = 10.0),
        )
        assertTrue(result.level == SuitabilityLevel.UNKNOWN || result.level == SuitabilityLevel.CAUTION)
        assertTrue(result.reasons.any { "Sugar value is unavailable" in it })
    }

    @Test
    fun `additive category match returns CAUTION`() {
        val result = scorer.score(
            makeProduct(additivesTags = listOf("en:e621")),
            makePreferences(additivesToAvoid = setOf("flavour-enhancers")),
        )
        assertEquals(SuitabilityLevel.CAUTION, result.level)
        assertTrue(result.reasons.any { "flavour enhancers" in it.lowercase() })
    }

    @Test
    fun `AVOID takes priority over CAUTION when both rules fire`() {
        val result = scorer.score(
            makeProduct(allergensTags = listOf("en:milk"), novaGroup = 4),
            makePreferences(allergensToAvoid = setOf("en:milk"), avoidUltraProcessed = true),
        )
        assertEquals(SuitabilityLevel.AVOID, result.level)
        assertTrue(result.reasons.any { "Contains Milk" in it })
        assertTrue(result.reasons.any { "NOVA 4" in it })
    }

    @Test
    fun `preferences configured and no issues returns GOOD_MATCH`() {
        val result = scorer.score(
            makeProduct(
                allergensTags = listOf("en:soy"),
                novaGroup = 1,
                nutrition = NutritionFacts(sugarsPer100g = 5.0, saltPer100g = 0.5, saturatedFatPer100g = 1.0),
                additivesTags = listOf("en:e330"),
            ),
            makePreferences(
                allergensToAvoid = setOf("en:milk"),
                avoidUltraProcessed = true,
                maxSugarsPer100g = 10.0,
                maxSaltPer100g = 2.0,
                maxSaturatedFatPer100g = 3.0,
                additivesToAvoid = setOf("flavour-enhancers"),
            ),
        )
        assertEquals(SuitabilityLevel.GOOD_MATCH, result.level)
        assertTrue(result.reasons.isEmpty())
    }

    // ── AllergenSuitabilityRule unit tests ────────────────────────────────────

    @Test
    fun `allergen rule returns GOOD_MATCH when no allergen prefs`() {
        val r = allergenRule.evaluate(
            makeProduct(allergensTags = listOf("en:milk")),
            makePreferences(),
        )
        assertEquals(SuitabilityLevel.GOOD_MATCH, r.level)
    }

    @Test
    fun `allergen rule identifies direct match in allergensTags`() {
        val r = allergenRule.evaluate(
            makeProduct(allergensTags = listOf("en:eggs", "en:milk")),
            makePreferences(allergensToAvoid = setOf("en:milk")),
        )
        assertEquals(SuitabilityLevel.AVOID, r.level)
        assertTrue(r.reasons.any { "Contains Milk" in it })
    }

    @Test
    fun `allergen rule identifies trace match only`() {
        val r = allergenRule.evaluate(
            makeProduct(allergensTags = listOf("en:soy"), tracesTags = listOf("en:peanuts")),
            makePreferences(allergensToAvoid = setOf("en:peanuts")),
        )
        assertEquals(SuitabilityLevel.CAUTION, r.level)
        assertTrue(r.reasons.any { "traces" in it.lowercase() && "Peanuts" in it })
    }

    @Test
    fun `allergen rule shows AVOID not CAUTION when allergen in both allergens and traces`() {
        val r = allergenRule.evaluate(
            makeProduct(allergensTags = listOf("en:milk"), tracesTags = listOf("en:milk")),
            makePreferences(allergensToAvoid = setOf("en:milk")),
        )
        assertEquals(SuitabilityLevel.AVOID, r.level)
    }

    // ── AdditiveSuitabilityRule unit tests ────────────────────────────────────

    @Test
    fun `additive rule returns GOOD_MATCH when no additive prefs`() {
        val r = additiveRule.evaluate(
            makeProduct(additivesTags = listOf("en:e621")),
            makePreferences(),
        )
        assertEquals(SuitabilityLevel.GOOD_MATCH, r.level)
    }

    @Test
    fun `additive rule returns UNKNOWN when additive data missing`() {
        val r = additiveRule.evaluate(
            makeProduct(additivesTags = emptyList()),
            makePreferences(additivesToAvoid = setOf("preservatives")),
        )
        assertEquals(SuitabilityLevel.UNKNOWN, r.level)
        assertTrue(r.reasons.any { "incomplete" in it.lowercase() })
    }

    @Test
    fun `additive rule matches preservative tag`() {
        val r = additiveRule.evaluate(
            makeProduct(additivesTags = listOf("en:e211")),
            makePreferences(additivesToAvoid = setOf("preservatives")),
        )
        assertEquals(SuitabilityLevel.CAUTION, r.level)
        assertTrue(r.reasons.any { "preservatives" in it.lowercase() })
    }

    @Test
    fun `additive rule matches artificial sweetener`() {
        val r = additiveRule.evaluate(
            makeProduct(additivesTags = listOf("en:e951")),
            makePreferences(additivesToAvoid = setOf("artificial-sweeteners")),
        )
        assertEquals(SuitabilityLevel.CAUTION, r.level)
        assertTrue(r.reasons.any { "artificial sweeteners" in it.lowercase() })
    }

    @Test
    fun `additive rule returns GOOD_MATCH when additives present but none avoided`() {
        val r = additiveRule.evaluate(
            makeProduct(additivesTags = listOf("en:e330")),
            makePreferences(additivesToAvoid = setOf("flavour-enhancers")),
        )
        assertEquals(SuitabilityLevel.GOOD_MATCH, r.level)
    }

    // ── NovaSuitabilityRule unit tests ────────────────────────────────────────

    @Test
    fun `nova rule returns GOOD_MATCH when avoid ultra processed is false`() {
        val r = novaRule.evaluate(makeProduct(novaGroup = 4), makePreferences(avoidUltraProcessed = false))
        assertEquals(SuitabilityLevel.GOOD_MATCH, r.level)
    }

    @Test
    fun `nova rule returns CAUTION for NOVA 4`() {
        val r = novaRule.evaluate(makeProduct(novaGroup = 4), makePreferences(avoidUltraProcessed = true))
        assertEquals(SuitabilityLevel.CAUTION, r.level)
        assertTrue(r.reasons.any { "NOVA 4" in it })
    }

    @Test
    fun `nova rule returns UNKNOWN when NOVA group missing`() {
        val r = novaRule.evaluate(makeProduct(novaGroup = null), makePreferences(avoidUltraProcessed = true))
        assertEquals(SuitabilityLevel.UNKNOWN, r.level)
        assertTrue(r.reasons.any { "unavailable" in it.lowercase() })
    }

    @Test
    fun `nova rule returns GOOD_MATCH for NOVA 1`() {
        val r = novaRule.evaluate(makeProduct(novaGroup = 1), makePreferences(avoidUltraProcessed = true))
        assertEquals(SuitabilityLevel.GOOD_MATCH, r.level)
    }

    // ── NutritionSuitabilityRule unit tests ───────────────────────────────────

    @Test
    fun `nutrition rule returns GOOD_MATCH when no caps set`() {
        val r = nutritionRule.evaluate(
            makeProduct(nutrition = NutritionFacts(sugarsPer100g = 50.0)),
            makePreferences(),
        )
        assertEquals(SuitabilityLevel.GOOD_MATCH, r.level)
    }

    @Test
    fun `nutrition rule returns CAUTION when value exceeds cap`() {
        val r = nutritionRule.evaluate(
            makeProduct(nutrition = NutritionFacts(sugarsPer100g = 20.0)),
            makePreferences(maxSugarsPer100g = 10.0),
        )
        assertEquals(SuitabilityLevel.CAUTION, r.level)
        assertTrue(r.reasons.any { "20.0g" in it })
    }

    @Test
    fun `nutrition rule returns UNKNOWN when value missing and cap set`() {
        val r = nutritionRule.evaluate(
            makeProduct(nutrition = null),
            makePreferences(maxSaltPer100g = 1.0),
        )
        assertEquals(SuitabilityLevel.UNKNOWN, r.level)
        assertTrue(r.reasons.any { "Salt value is unavailable" in it })
    }

    @Test
    fun `nutrition rule CAUTION beats UNKNOWN when both present`() {
        val r = nutritionRule.evaluate(
            makeProduct(nutrition = NutritionFacts(sugarsPer100g = 20.0, saltPer100g = null)),
            makePreferences(maxSugarsPer100g = 10.0, maxSaltPer100g = 1.0),
        )
        assertEquals(SuitabilityLevel.CAUTION, r.level)
        assertEquals(2, r.reasons.size)
    }

    // ── AdditiveCategoryMapper unit tests ─────────────────────────────────────

    @Test
    fun `AdditiveCategoryMapper returns empty set for unknown category`() {
        val tags = AdditiveCategoryMapper.tagsForCategory("unknown-category")
        assertTrue(tags.isEmpty())
    }

    @Test
    fun `AdditiveCategoryMapper matchingCategories finds correct categories`() {
        val matched = AdditiveCategoryMapper.matchingCategories(
            additiveTags = listOf("en:e211", "en:e621"),
            avoidedCategories = setOf("preservatives", "flavour-enhancers", "artificial-colours"),
        )
        assertTrue("preservatives" in matched)
        assertTrue("flavour-enhancers" in matched)
        assertTrue("artificial-colours" !in matched)
    }
}

// ── Test helpers ─────────────────────────────────────────────────────────────

private fun makeProduct(
    barcode: String = "0000000000000",
    name: String = "Test Product",
    novaGroup: Int? = null,
    allergensTags: List<String> = emptyList(),
    tracesTags: List<String> = emptyList(),
    additivesTags: List<String> = emptyList(),
    nutrition: NutritionFacts? = null,
) = ProductDetails(
    barcode = barcode,
    name = name,
    brand = null,
    quantity = null,
    imageFrontUrl = null,
    nutriScore = null,
    novaGroup = novaGroup,
    nutrition = nutrition,
    ingredientsText = null,
    allergensTags = allergensTags,
    tracesTags = tracesTags,
    additivesTags = additivesTags,
)

private fun makePreferences(
    allergensToAvoid: Set<String> = emptySet(),
    additivesToAvoid: Set<String> = emptySet(),
    avoidUltraProcessed: Boolean = false,
    maxSugarsPer100g: Double? = null,
    maxSaltPer100g: Double? = null,
    maxSaturatedFatPer100g: Double? = null,
) = UserFoodPreferences(
    allergensToAvoid = allergensToAvoid,
    additivesToAvoid = additivesToAvoid,
    avoidUltraProcessed = avoidUltraProcessed,
    maxSugarsPer100g = maxSugarsPer100g,
    maxSaltPer100g = maxSaltPer100g,
    maxSaturatedFatPer100g = maxSaturatedFatPer100g,
)
