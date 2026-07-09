package com.sephylon.foodfitscan.domain.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchResultQualityTest {

    // ── isDisplayable: supporting-signal rule ───────────────────────────────────

    @Test
    fun `product with image is displayable`() {
        assertTrue(
            SearchResultQuality.isDisplayable(
                name = "KitKat",
                brand = null,
                imageUrl = "https://img/kitkat.jpg",
                categoriesCount = 0,
            ),
        )
    }

    @Test
    fun `product with brand but no image is displayable`() {
        assertTrue(
            SearchResultQuality.isDisplayable(
                name = "KitKat",
                brand = "Nestle",
                imageUrl = null,
                categoriesCount = 0,
            ),
        )
    }

    @Test
    fun `product with categories but no image or brand is displayable`() {
        assertTrue(
            SearchResultQuality.isDisplayable(
                name = "Plain Rice",
                brand = null,
                imageUrl = null,
                categoriesCount = 3,
            ),
        )
    }

    @Test
    fun `product with only a name is hidden`() {
        assertFalse(
            SearchResultQuality.isDisplayable(
                name = "Some Snack",
                brand = null,
                imageUrl = null,
                categoriesCount = 0,
            ),
        )
    }

    @Test
    fun `blank brand and image do not count as signals`() {
        assertFalse(
            SearchResultQuality.isDisplayable(
                name = "Some Snack",
                brand = "   ",
                imageUrl = "",
                categoriesCount = 0,
            ),
        )
    }

    // ── isDisplayable: name quality rules ───────────────────────────────────────

    @Test
    fun `null or blank name is hidden`() {
        assertFalse(SearchResultQuality.isDisplayable(null, "Brand", "https://img/x.jpg", 5))
        assertFalse(SearchResultQuality.isDisplayable("   ", "Brand", "https://img/x.jpg", 5))
    }

    @Test
    fun `name shorter than three normalized characters is hidden`() {
        assertFalse(SearchResultQuality.isDisplayable("ab", "Brand", "https://img/x.jpg", 5))
    }

    @Test
    fun `placeholder names are hidden regardless of other data`() {
        listOf("test", "TEST", "Test Product", "Unknown", "N/A", "sans nom", "Producto").forEach {
            assertFalse(
                "expected \"$it\" to be suppressed",
                SearchResultQuality.isDisplayable(it, "Brand", "https://img/x.jpg", 5),
            )
        }
    }

    @Test
    fun `numeric-only name is hidden`() {
        assertFalse(SearchResultQuality.isDisplayable("5000159407236", "Brand", "https://img/x.jpg", 5))
        assertFalse(SearchResultQuality.isDisplayable("12 345", "Brand", "https://img/x.jpg", 5))
    }

    @Test
    fun `real names containing digits are kept`() {
        assertTrue(SearchResultQuality.isDisplayable("7Up Lemon", "PepsiCo", null, 0))
    }

    @Test
    fun `real product names are not suppressed`() {
        listOf("Nutella", "Greek Yogurt", "Tomato & Basil Soup").forEach {
            assertTrue(
                "expected \"$it\" to be displayable",
                SearchResultQuality.isDisplayable(it, "Brand", null, 0),
            )
        }
    }

    // ── displayTier ─────────────────────────────────────────────────────────────

    @Test
    fun `image beats brand for tier`() {
        assertEquals(
            SearchResultQuality.TIER_IMAGE,
            SearchResultQuality.displayTier(brand = "Nestle", imageUrl = "https://img/x.jpg"),
        )
        assertEquals(
            SearchResultQuality.TIER_IMAGE,
            SearchResultQuality.displayTier(brand = null, imageUrl = "https://img/x.jpg"),
        )
    }

    @Test
    fun `brand without image is second tier`() {
        assertEquals(
            SearchResultQuality.TIER_BRAND,
            SearchResultQuality.displayTier(brand = "Nestle", imageUrl = null),
        )
    }

    @Test
    fun `no image and no brand is weakest tier`() {
        assertEquals(
            SearchResultQuality.TIER_WEAK,
            SearchResultQuality.displayTier(brand = null, imageUrl = ""),
        )
    }

    @Test
    fun `tiers order image before brand before weak`() {
        assertTrue(SearchResultQuality.TIER_IMAGE < SearchResultQuality.TIER_BRAND)
        assertTrue(SearchResultQuality.TIER_BRAND < SearchResultQuality.TIER_WEAK)
    }
}
