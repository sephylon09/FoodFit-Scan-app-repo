package com.sephylon.foodfitscan.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NutritionDisplayOptionTest {

    private val fullNutrition = NutritionFacts(
        energyKcalPer100g = 250.0,
        fatPer100g = 10.0,
        saturatedFatPer100g = 4.0,
        carbohydratesPer100g = 30.0,
        sugarsPer100g = 20.0,
        fiberPer100g = 2.5,
        proteinPer100g = 8.0,
        saltPer100g = 1.2,
        sodiumPer100g = 0.48,
    )

    // ── Value mapping ───────────────────────────────────────────────────────────

    @Test
    fun `each option maps to the correct NutritionFacts value`() {
        assertEquals(250.0, NutritionDisplayOption.ENERGY_KCAL.valueFrom(fullNutrition))
        assertEquals(10.0, NutritionDisplayOption.FAT.valueFrom(fullNutrition))
        assertEquals(4.0, NutritionDisplayOption.SATURATED_FAT.valueFrom(fullNutrition))
        assertEquals(30.0, NutritionDisplayOption.CARBOHYDRATES.valueFrom(fullNutrition))
        assertEquals(20.0, NutritionDisplayOption.SUGARS.valueFrom(fullNutrition))
        assertEquals(2.5, NutritionDisplayOption.FIBER.valueFrom(fullNutrition))
        assertEquals(8.0, NutritionDisplayOption.PROTEIN.valueFrom(fullNutrition))
        assertEquals(1.2, NutritionDisplayOption.SALT.valueFrom(fullNutrition))
        assertEquals(0.48, NutritionDisplayOption.SODIUM.valueFrom(fullNutrition))
    }

    @Test
    fun `valueFrom returns null for missing values`() {
        val empty = NutritionFacts()
        NutritionDisplayOption.ALL.forEach { option ->
            assertNull("Expected null for ${option.key}", option.valueFrom(empty))
        }
    }

    @Test
    fun `energy option uses kcal unit and others use grams`() {
        assertEquals("kcal", NutritionDisplayOption.ENERGY_KCAL.unit)
        assertEquals("g", NutritionDisplayOption.PROTEIN.unit)
        assertEquals("g", NutritionDisplayOption.SALT.unit)
    }

    // ── Keys ───────────────────────────────────────────────────────────────────

    @Test
    fun `all nine options are present with stable keys`() {
        val keys = NutritionDisplayOption.ALL.map { it.key }
        assertEquals(9, keys.size)
        assertEquals(
            listOf(
                "energy_kcal", "fat", "saturated_fat", "carbohydrates",
                "sugars", "fiber", "protein", "salt", "sodium",
            ),
            keys,
        )
    }

    @Test
    fun `default keys are energy sugars salt saturated fat and protein`() {
        assertEquals(
            setOf("energy_kcal", "sugars", "salt", "saturated_fat", "protein"),
            NutritionDisplayOption.DEFAULT_KEYS,
        )
    }

    @Test
    fun `fromKey resolves a known key and returns null for unknown`() {
        assertEquals(NutritionDisplayOption.PROTEIN, NutritionDisplayOption.fromKey("protein"))
        assertNull(NutritionDisplayOption.fromKey("not_a_real_key"))
    }

    // ── resolveSelected ─────────────────────────────────────────────────────────

    @Test
    fun `resolveSelected falls back to defaults for null`() {
        val resolved = NutritionDisplayOption.resolveSelected(null).map { it.key }.toSet()
        assertEquals(NutritionDisplayOption.DEFAULT_KEYS, resolved)
    }

    @Test
    fun `resolveSelected falls back to defaults for empty set`() {
        val resolved = NutritionDisplayOption.resolveSelected(emptySet()).map { it.key }.toSet()
        assertEquals(NutritionDisplayOption.DEFAULT_KEYS, resolved)
    }

    @Test
    fun `resolveSelected falls back to defaults when all keys are unknown`() {
        val resolved = NutritionDisplayOption.resolveSelected(setOf("bogus", "nope")).map { it.key }.toSet()
        assertEquals(NutritionDisplayOption.DEFAULT_KEYS, resolved)
    }

    @Test
    fun `resolveSelected returns only the selected options`() {
        val resolved = NutritionDisplayOption.resolveSelected(setOf("protein", "carbohydrates"))
        assertEquals(listOf(NutritionDisplayOption.CARBOHYDRATES, NutritionDisplayOption.PROTEIN), resolved)
    }

    @Test
    fun `resolveSelected always returns canonical order regardless of input order`() {
        val resolved = NutritionDisplayOption.resolveSelected(setOf("protein", "fat", "energy_kcal"))
        assertEquals(
            listOf(
                NutritionDisplayOption.ENERGY_KCAL,
                NutritionDisplayOption.FAT,
                NutritionDisplayOption.PROTEIN,
            ),
            resolved,
        )
    }

    @Test
    fun `resolveSelected ignores unknown keys mixed with valid ones`() {
        val resolved = NutritionDisplayOption.resolveSelected(setOf("protein", "bogus")).map { it.key }
        assertTrue("protein" in resolved)
        assertFalse("bogus" in resolved)
        assertEquals(1, resolved.size)
    }
}
