package com.sephylon.foodfitscan.domain.rules

import com.sephylon.foodfitscan.domain.model.NutritionFacts
import com.sephylon.foodfitscan.domain.model.ProductDetails
import com.sephylon.foodfitscan.domain.model.SuitabilityLevel
import com.sephylon.foodfitscan.domain.model.UserFoodPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlternativeRankerTest {

    // ── AlternativeCategorySelector ───────────────────────────────────────────

    @Test
    fun `selectCategoryTag returns null for null categoriesTags`() {
        val product = makeProduct(categoriesTags = null)
        assertNull(AlternativeCategorySelector.selectCategoryTag(product))
    }

    @Test
    fun `selectCategoryTag returns null for empty categoriesTags`() {
        val product = makeProduct(categoriesTags = emptyList())
        assertNull(AlternativeCategorySelector.selectCategoryTag(product))
    }

    @Test
    fun `selectCategoryTag returns last specific English tag`() {
        val product = makeProduct(categoriesTags = listOf("en:foods-and-drinks", "en:snacks", "en:biscuits"))
        val tag = AlternativeCategorySelector.selectCategoryTag(product)
        assertEquals("en:biscuits", tag)
    }

    @Test
    fun `selectCategoryTag skips very generic tags`() {
        val product = makeProduct(categoriesTags = listOf("en:foods-and-drinks", "en:groceries"))
        // All are generic — falls back to last available
        val tag = AlternativeCategorySelector.selectCategoryTag(product)
        assertEquals("en:groceries", tag)
    }

    @Test
    fun `selectCategoryTag returns last non-generic tag`() {
        val product = makeProduct(categoriesTags = listOf("en:beverages", "en:sodas", "en:foods-and-drinks"))
        val tag = AlternativeCategorySelector.selectCategoryTag(product)
        assertEquals("en:sodas", tag)
    }

    @Test
    fun `selectCategoryTags returns most specific tags first capped at two`() {
        val product = makeProduct(
            categoriesTags = listOf("en:snacks", "en:sweet-snacks", "en:bars", "en:protein-bars"),
        )
        val tags = AlternativeCategorySelector.selectCategoryTags(product)
        assertEquals(listOf("en:protein-bars", "en:bars"), tags)
    }

    @Test
    fun `selectCategoryTags is empty without category data`() {
        assertTrue(AlternativeCategorySelector.selectCategoryTags(makeProduct(categoriesTags = null)).isEmpty())
        assertTrue(AlternativeCategorySelector.selectCategoryTags(makeProduct(categoriesTags = emptyList())).isEmpty())
    }

    @Test
    fun `specificCategoryTags drops generic and non-english tags`() {
        val tags = AlternativeCategorySelector.specificCategoryTags(
            listOf("en:foods-and-drinks", "fr:barres", "en:bars", "en:protein-bars"),
        )
        assertEquals(listOf("en:bars", "en:protein-bars"), tags)
    }

    // ── AlternativeRanker ─────────────────────────────────────────────────────

    @Test
    fun `rank excludes current product barcode`() {
        val current = makeProduct(barcode = "CURRENT")
        val other = makeProduct(barcode = "OTHER")
        val ranked = AlternativeRanker.rank(
            candidates = listOf(current, other),
            currentBarcode = "CURRENT",
            preferences = UserFoodPreferences(),
        )
        assertFalse(ranked.any { it.barcode == "CURRENT" })
        assertTrue(ranked.any { it.barcode == "OTHER" })
    }

    @Test
    fun `rank excludes products with blank barcode`() {
        val blank = makeProduct(barcode = "")
        val valid = makeProduct(barcode = "VALID")
        val ranked = AlternativeRanker.rank(
            candidates = listOf(blank, valid),
            currentBarcode = "X",
            preferences = UserFoodPreferences(),
        )
        assertFalse(ranked.any { it.barcode.isBlank() })
    }

    @Test
    fun `rank excludes products with no useful name`() {
        val unnamed = makeProduct(barcode = "A", name = "Unknown product")
        val named = makeProduct(barcode = "B", name = "Good Biscuit")
        val ranked = AlternativeRanker.rank(
            candidates = listOf(unnamed, named),
            currentBarcode = "X",
            preferences = UserFoodPreferences(),
        )
        assertFalse(ranked.any { it.productName == "Unknown product" })
    }

    @Test
    fun `rank excludes AVOID products when better options exist`() {
        val prefs = UserFoodPreferences(allergensToAvoid = setOf("en:milk"))
        val avoidProd = makeProduct(barcode = "A", name = "Bad", allergensTags = listOf("en:milk"))
        val goodProd = makeProduct(barcode = "B", name = "Good", allergensTags = emptyList())
        val ranked = AlternativeRanker.rank(
            candidates = listOf(avoidProd, goodProd),
            currentBarcode = "X",
            preferences = prefs,
        )
        assertFalse(ranked.any { it.suitabilityLevel == SuitabilityLevel.AVOID })
        assertTrue(ranked.any { it.barcode == "B" })
    }

    @Test
    fun `rank prefers GOOD_MATCH over CAUTION`() {
        val prefs = UserFoodPreferences(avoidUltraProcessed = true)
        val caution = makeProduct(barcode = "A", name = "Ultra", novaGroup = 4, allergensTags = emptyList())
        val good = makeProduct(barcode = "B", name = "Natural", novaGroup = 1, allergensTags = emptyList())
        val ranked = AlternativeRanker.rank(
            candidates = listOf(caution, good),
            currentBarcode = "X",
            preferences = prefs,
        )
        assertEquals("B", ranked.first().barcode)
        assertEquals(SuitabilityLevel.GOOD_MATCH, ranked.first().suitabilityLevel)
    }

    @Test
    fun `rank prefers better Nutri-Score when suitability is equal`() {
        val prefs = UserFoodPreferences()
        val gradeC = makeProduct(barcode = "A", name = "Grade C product", nutriScore = "c")
        val gradeA = makeProduct(barcode = "B", name = "Grade A product", nutriScore = "a")
        val ranked = AlternativeRanker.rank(
            candidates = listOf(gradeC, gradeA),
            currentBarcode = "X",
            preferences = prefs,
        )
        assertEquals("B", ranked.first().barcode)
    }

    @Test
    fun `rank prefers lower NOVA group when suitability and nutriScore are equal`() {
        val prefs = UserFoodPreferences()
        val nova3 = makeProduct(barcode = "A", name = "Nova 3 product", nutriScore = null, novaGroup = 3)
        val nova1 = makeProduct(barcode = "B", name = "Nova 1 product", nutriScore = null, novaGroup = 1)
        val ranked = AlternativeRanker.rank(
            candidates = listOf(nova3, nova1),
            currentBarcode = "X",
            preferences = prefs,
        )
        assertEquals("B", ranked.first().barcode)
    }

    @Test
    fun `rank returns at most 4 alternatives`() {
        val candidates = (1..10).map { i ->
            makeProduct(barcode = "B$i", name = "Product $i")
        }
        val ranked = AlternativeRanker.rank(
            candidates = candidates,
            currentBarcode = "X",
            preferences = UserFoodPreferences(),
        )
        assertTrue(ranked.size <= 4)
    }

    @Test
    fun `rank requires two shared categories when the base product has several specific tags`() {
        val base = listOf("en:snacks", "en:sweet-snacks", "en:bars", "en:protein-bars")
        val sameType = makeProduct(
            barcode = "SAME",
            name = "Other Protein Bar",
            categoriesTags = listOf("en:snacks", "en:bars", "en:protein-bars"),
        )
        val looselyRelated = makeProduct(
            barcode = "LOOSE",
            name = "Chocolate",
            categoriesTags = listOf("en:snacks"),
        )
        val unrelated = makeProduct(
            barcode = "WATER",
            name = "Spring Water",
            categoriesTags = listOf("en:beverages", "en:waters"),
        )
        val ranked = AlternativeRanker.rank(
            candidates = listOf(sameType, looselyRelated, unrelated),
            currentBarcode = "X",
            preferences = UserFoodPreferences(),
            baseCategoryTags = base,
        )
        assertEquals(listOf("SAME"), ranked.map { it.barcode })
    }

    @Test
    fun `rank accepts single shared category when the base product has only one specific tag`() {
        val base = listOf("en:foods-and-drinks", "en:biscuits")
        val match = makeProduct(
            barcode = "A",
            name = "Plain Biscuit",
            categoriesTags = listOf("en:biscuits"),
        )
        val nonMatch = makeProduct(
            barcode = "B",
            name = "Juice",
            categoriesTags = listOf("en:juices"),
        )
        val ranked = AlternativeRanker.rank(
            candidates = listOf(match, nonMatch),
            currentBarcode = "X",
            preferences = UserFoodPreferences(),
            baseCategoryTags = base,
        )
        assertEquals(listOf("A"), ranked.map { it.barcode })
    }

    @Test
    fun `rank orders stronger category overlap before better nutriscore`() {
        val base = listOf("en:snacks", "en:bars", "en:protein-bars")
        val closeMatchWorseScore = makeProduct(
            barcode = "CLOSE",
            name = "Protein Bar B",
            nutriScore = "c",
            categoriesTags = listOf("en:snacks", "en:bars", "en:protein-bars"),
        )
        val looserMatchBetterScore = makeProduct(
            barcode = "LOOSER",
            name = "Snack Bar A",
            nutriScore = "a",
            categoriesTags = listOf("en:snacks", "en:bars"),
        )
        val ranked = AlternativeRanker.rank(
            candidates = listOf(looserMatchBetterScore, closeMatchWorseScore),
            currentBarcode = "X",
            preferences = UserFoodPreferences(),
            baseCategoryTags = base,
        )
        assertEquals(listOf("CLOSE", "LOOSER"), ranked.map { it.barcode })
    }

    @Test
    fun `rank without base categories applies no overlap filter`() {
        val candidate = makeProduct(barcode = "A", name = "Anything", categoriesTags = null)
        val ranked = AlternativeRanker.rank(
            candidates = listOf(candidate),
            currentBarcode = "X",
            preferences = UserFoodPreferences(),
        )
        assertEquals(listOf("A"), ranked.map { it.barcode })
    }

    @Test
    fun `rank returns empty list when all candidates are excluded`() {
        val prefs = UserFoodPreferences(allergensToAvoid = setOf("en:milk"))
        val allAvoid = (1..3).map { i ->
            makeProduct(barcode = "B$i", name = "Product $i", allergensTags = listOf("en:milk"))
        }
        val ranked = AlternativeRanker.rank(
            candidates = allAvoid,
            currentBarcode = "X",
            preferences = prefs,
        )
        assertTrue(ranked.isEmpty())
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun assertNull(value: String?) {
    assertTrue("Expected null but was: $value", value == null)
}

private fun makeProduct(
    barcode: String = "0000000000000",
    name: String = "Test Product",
    novaGroup: Int? = null,
    nutriScore: String? = null,
    allergensTags: List<String> = emptyList(),
    categoriesTags: List<String>? = null,
    nutrition: NutritionFacts? = null,
) = ProductDetails(
    barcode = barcode,
    name = name,
    brand = null,
    quantity = null,
    imageFrontUrl = null,
    nutriScore = nutriScore,
    novaGroup = novaGroup,
    nutrition = nutrition,
    ingredientsText = null,
    allergensTags = allergensTags,
    tracesTags = emptyList(),
    additivesTags = emptyList(),
    categoriesTags = categoriesTags,
)
