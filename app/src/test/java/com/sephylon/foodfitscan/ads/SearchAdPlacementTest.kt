package com.sephylon.foodfitscan.ads

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchAdPlacementTest {

    private fun products(count: Int): List<String> = (1..count).map { "product$it" }

    private fun List<SearchRow<String>>.productsOnly(): List<String> =
        filterIsInstance<SearchRow.Product<String>>().map { it.item }

    private fun List<SearchRow<String>>.adPositions(): List<Int> =
        mapIndexedNotNull { index, row -> if (row is SearchRow.Ad) index else null }

    @Test
    fun `empty list produces no rows and no ads`() {
        val rows = SearchAdPlacement.buildRows(emptyList<String>())
        assertTrue(rows.isEmpty())
    }

    @Test
    fun `short list of 5 products gets no ads`() {
        val rows = SearchAdPlacement.buildRows(products(5))
        assertEquals(5, rows.size)
        assertTrue(rows.none { it is SearchRow.Ad })
    }

    @Test
    fun `exactly 6 products gets no trailing ad`() {
        val rows = SearchAdPlacement.buildRows(products(6))
        assertEquals(6, rows.size)
        assertTrue(rows.none { it is SearchRow.Ad })
    }

    @Test
    fun `7 products get one ad after the 6th product`() {
        val rows = SearchAdPlacement.buildRows(products(7))

        // 7 products + 1 ad; the ad sits at row index 6 (right after the 6th product).
        assertEquals(8, rows.size)
        assertEquals(listOf(6), rows.adPositions())
        assertTrue(rows.last() is SearchRow.Product<*>)
    }

    @Test
    fun `ad insertion preserves exact product order`() {
        val items = products(20)
        val rows = SearchAdPlacement.buildRows(items)
        assertEquals(items, rows.productsOnly())
    }

    @Test
    fun `long list repeats ads every 11 products after the first slot`() {
        val rows = SearchAdPlacement.buildRows(products(20))

        // Ad 0 after 6 products (row 6), ad 1 after 11 more products (row 6+1+11 = 18).
        assertEquals(listOf(6, 18), rows.adPositions())
        assertEquals(listOf(0, 1), rows.filterIsInstance<SearchRow.Ad>().map { it.slot })
    }

    @Test
    fun `ads are capped at MAX_AD_SLOTS even for very long lists`() {
        val rows = SearchAdPlacement.buildRows(products(100))
        assertEquals(SearchAdPlacement.MAX_AD_SLOTS, rows.count { it is SearchRow.Ad })
    }

    @Test
    fun `an ad is never the last row`() {
        for (count in 0..40) {
            val rows = SearchAdPlacement.buildRows(products(count))
            if (rows.isNotEmpty()) {
                assertTrue(
                    "list of $count products must not end with an ad",
                    rows.last() is SearchRow.Product<*>,
                )
            }
        }
    }

    @Test
    fun `ad slots use distinct ids for stable lazy list keys`() {
        val slots = SearchAdPlacement.buildRows(products(100))
            .filterIsInstance<SearchRow.Ad>()
            .map { it.slot }
        assertEquals(slots.distinct(), slots)
    }
}
