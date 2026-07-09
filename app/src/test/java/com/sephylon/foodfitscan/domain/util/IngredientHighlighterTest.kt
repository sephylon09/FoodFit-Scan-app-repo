package com.sephylon.foodfitscan.domain.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IngredientHighlighterTest {

    private fun highlighted(text: String, keys: Set<String>): List<String> =
        IngredientHighlighter.findMatchSpans(text, keys)
            .map { text.substring(it.start, it.endExclusive) }

    @Test
    fun `highlights milk inside milk powder for avoided milk`() {
        val text = "Sugar, milk powder, cocoa butter"
        val matches = highlighted(text, setOf("en:milk"))
        assertTrue("milk" in matches)
    }

    @Test
    fun `highlights soy at word start of soya lecithin`() {
        val text = "Cocoa mass, soya lecithin, vanilla"
        val matches = highlighted(text, setOf("en:soybeans"))
        assertEquals(listOf("soya"), matches)
    }

    @Test
    fun `highlights derived words like eggs and buttermilk`() {
        val text = "Eggs, buttermilk, flour"
        val matches = highlighted(text, setOf("en:eggs", "en:milk"))
        assertEquals(listOf("Eggs", "buttermilk"), matches)
    }

    @Test
    fun `matching is case insensitive`() {
        val matches = highlighted("WHEAT FLOUR, salt", setOf("en:gluten"))
        assertEquals(listOf("WHEAT"), matches)
    }

    @Test
    fun `does not match in the middle of words`() {
        // "coconut" must not be flagged for tree nuts; "nutmeg" has no term either.
        val matches = highlighted("Coconut oil, nutmeg", setOf("en:nuts"))
        assertTrue(matches.isEmpty())
    }

    @Test
    fun `no avoided allergens yields no spans`() {
        assertTrue(IngredientHighlighter.findMatchSpans("Milk, eggs", emptySet()).isEmpty())
    }

    @Test
    fun `null or blank text yields no spans`() {
        assertTrue(IngredientHighlighter.findMatchSpans(null, setOf("en:milk")).isEmpty())
        assertTrue(IngredientHighlighter.findMatchSpans("   ", setOf("en:milk")).isEmpty())
    }

    @Test
    fun `unknown preference keys are ignored`() {
        assertTrue(IngredientHighlighter.findMatchSpans("Milk", setOf("en:unheard-of")).isEmpty())
    }

    @Test
    fun `multiple occurrences are all matched in order`() {
        val text = "Milk, sugar, milk protein"
        val spans = IngredientHighlighter.findMatchSpans(text, setOf("en:milk"))
        assertEquals(2, spans.size)
        assertTrue(spans[0].start < spans[1].start)
    }

    @Test
    fun `spans do not overlap after merging`() {
        val text = "Milk, whey, buttermilk"
        val spans = IngredientHighlighter.findMatchSpans(text, setOf("en:milk"))
        spans.zipWithNext().forEach { (a, b) ->
            assertTrue(a.endExclusive <= b.start)
        }
    }

    @Test
    fun `hasMatches reflects presence of avoided terms`() {
        assertTrue(IngredientHighlighter.hasMatches("wheat flour", setOf("en:gluten")))
        assertFalse(IngredientHighlighter.hasMatches("rice flour", setOf("en:gluten")))
    }
}
