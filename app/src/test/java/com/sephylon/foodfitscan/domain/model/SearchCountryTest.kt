package com.sephylon.foodfitscan.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchCountryTest {

    // ── Device region -> default country ────────────────────────────────────

    @Test
    fun `SG region defaults to Singapore`() {
        assertEquals(SearchCountry.SINGAPORE, SearchCountry.fromRegionCode("SG"))
    }

    @Test
    fun `supported regions map to their country`() {
        assertEquals(SearchCountry.MALAYSIA, SearchCountry.fromRegionCode("MY"))
        assertEquals(SearchCountry.JAPAN, SearchCountry.fromRegionCode("JP"))
        assertEquals(SearchCountry.AUSTRALIA, SearchCountry.fromRegionCode("AU"))
        assertEquals(SearchCountry.UNITED_STATES, SearchCountry.fromRegionCode("US"))
        assertEquals(SearchCountry.SOUTH_KOREA, SearchCountry.fromRegionCode("KR"))
        assertEquals(SearchCountry.HONG_KONG, SearchCountry.fromRegionCode("HK"))
    }

    @Test
    fun `region code is matched case-insensitively and trimmed`() {
        assertEquals(SearchCountry.SINGAPORE, SearchCountry.fromRegionCode("sg"))
        assertEquals(SearchCountry.JAPAN, SearchCountry.fromRegionCode(" jp "))
    }

    @Test
    fun `united kingdom accepts both GB and the legacy UK code`() {
        assertEquals(SearchCountry.UNITED_KINGDOM, SearchCountry.fromRegionCode("GB"))
        assertEquals(SearchCountry.UNITED_KINGDOM, SearchCountry.fromRegionCode("UK"))
    }

    @Test
    fun `unsupported region defaults to All`() {
        assertEquals(SearchCountry.ALL, SearchCountry.fromRegionCode("BR"))
        assertEquals(SearchCountry.ALL, SearchCountry.fromRegionCode("ZA"))
    }

    @Test
    fun `missing or malformed region defaults to All`() {
        assertEquals(SearchCountry.ALL, SearchCountry.fromRegionCode(null))
        assertEquals(SearchCountry.ALL, SearchCountry.fromRegionCode(""))
        assertEquals(SearchCountry.ALL, SearchCountry.fromRegionCode("   "))
        assertEquals(SearchCountry.ALL, SearchCountry.fromRegionCode("SGP"))
    }

    // ── Display name -> Firestore countryTags value ─────────────────────────

    @Test
    fun `display names map to the expected open food facts country tags`() {
        val expected = mapOf(
            "Singapore" to "en:singapore",
            "Malaysia" to "en:malaysia",
            "Indonesia" to "en:indonesia",
            "Thailand" to "en:thailand",
            "Japan" to "en:japan",
            "South Korea" to "en:south-korea",
            "China" to "en:china",
            "Taiwan" to "en:taiwan",
            "Hong Kong" to "en:hong-kong",
            "Australia" to "en:australia",
            "New Zealand" to "en:new-zealand",
            "United States" to "en:united-states",
            "United Kingdom" to "en:united-kingdom",
            "India" to "en:india",
            "France" to "en:france",
            "Germany" to "en:germany",
            "Italy" to "en:italy",
        )

        val actual = SearchCountry.OPTIONS
            .filter { it != SearchCountry.ALL }
            .associate { it.displayName to it.countryTag }

        assertEquals(expected, actual)
    }

    @Test
    fun `All has no country tag and is the first dropdown option`() {
        assertNull(SearchCountry.ALL.countryTag)
        assertEquals(SearchCountry.ALL, SearchCountry.OPTIONS.first())
        assertEquals(18, SearchCountry.OPTIONS.size)
    }

    // ── Tag matching ────────────────────────────────────────────────────────

    @Test
    fun `All matches every document including ones with no country tags`() {
        assertTrue(SearchCountry.ALL.matches(listOf("en:france")))
        assertTrue(SearchCountry.ALL.matches(emptyList()))
    }

    @Test
    fun `a country matches only documents carrying its tag`() {
        val tags = listOf("en:singapore", "en:malaysia")

        assertTrue(SearchCountry.SINGAPORE.matches(tags))
        assertTrue(SearchCountry.MALAYSIA.matches(tags))
        assertFalse(SearchCountry.JAPAN.matches(tags))
        assertFalse(SearchCountry.SINGAPORE.matches(emptyList()))
    }

    @Test
    fun `tag matching tolerates whitespace and casing from the index`() {
        assertTrue(SearchCountry.JAPAN.matches(listOf(" EN:JAPAN ")))
    }

    // ── Persistence keys ────────────────────────────────────────────────────

    @Test
    fun `keys round-trip through fromKey`() {
        SearchCountry.OPTIONS.forEach { country ->
            assertEquals(country, SearchCountry.fromKey(country.key))
        }
    }

    @Test
    fun `unknown or absent key resolves to null`() {
        assertNull(SearchCountry.fromKey("ATLANTIS"))
        assertNull(SearchCountry.fromKey(null))
    }
}
