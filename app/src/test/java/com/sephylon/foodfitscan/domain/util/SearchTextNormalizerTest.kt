package com.sephylon.foodfitscan.domain.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SearchTextNormalizerTest {

    @Test
    fun `normalize lowercases and trims`() {
        assertEquals("nutella", SearchTextNormalizer.normalize("  NuTeLLa  "))
    }

    @Test
    fun `normalize removes punctuation`() {
        assertEquals("cadbury dairy milk", SearchTextNormalizer.normalize("Cadbury: Dairy-Milk!"))
    }

    @Test
    fun `normalize collapses repeated whitespace`() {
        assertEquals("dark chocolate bar", SearchTextNormalizer.normalize("dark   chocolate\tbar"))
    }

    @Test
    fun `normalize expands ampersand like the sync script`() {
        assertEquals("m and m", SearchTextNormalizer.normalize("M&M"))
    }

    @Test
    fun `queryPrefix returns first searchable word lowercased`() {
        assertEquals("nutella", SearchTextNormalizer.queryPrefix("Nutella Hazelnut"))
    }

    @Test
    fun `queryPrefix skips leading short words`() {
        // "3" is shorter than the minimum indexed word length, so the first real word wins.
        assertEquals("musketeers", SearchTextNormalizer.queryPrefix("3 Musketeers"))
    }

    @Test
    fun `queryPrefix is null for blank input`() {
        assertNull(SearchTextNormalizer.queryPrefix("   "))
    }

    @Test
    fun `queryPrefix is null when every word is too short`() {
        assertNull(SearchTextNormalizer.queryPrefix("a b"))
    }

    @Test
    fun `queryPrefix is null for a two character query`() {
        assertNull(SearchTextNormalizer.queryPrefix("ab"))
    }

    @Test
    fun `queryPrefix caps very long words at 24 characters`() {
        val longWord = "a".repeat(40)
        assertEquals("a".repeat(24), SearchTextNormalizer.queryPrefix(longWord))
    }
}
