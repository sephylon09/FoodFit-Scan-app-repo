package com.sephylon.foodfitscan.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SuitabilityLevelTest {

    @Test
    fun `SuitabilityLevel contains all required values`() {
        val entries = SuitabilityLevel.entries
        assertTrue(entries.contains(SuitabilityLevel.GOOD_MATCH))
        assertTrue(entries.contains(SuitabilityLevel.CAUTION))
        assertTrue(entries.contains(SuitabilityLevel.AVOID))
        assertTrue(entries.contains(SuitabilityLevel.UNKNOWN))
        assertEquals(4, entries.size)
    }

    @Test
    fun `SuitabilityResult unknown factory creates UNKNOWN result with empty reasons`() {
        val result = SuitabilityResult.unknown()
        assertEquals(SuitabilityLevel.UNKNOWN, result.level)
        assertTrue(result.reasons.isEmpty())
    }

    @Test
    fun `SuitabilityResult can be created with reasons`() {
        val result = SuitabilityResult(
            level = SuitabilityLevel.AVOID,
            reasons = listOf("Contains gluten", "High sugar"),
        )
        assertEquals(SuitabilityLevel.AVOID, result.level)
        assertEquals(2, result.reasons.size)
        assertTrue(result.reasons.contains("Contains gluten"))
    }
}
