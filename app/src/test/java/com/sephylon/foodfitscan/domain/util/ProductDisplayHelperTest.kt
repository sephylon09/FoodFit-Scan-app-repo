package com.sephylon.foodfitscan.domain.util

import com.sephylon.foodfitscan.domain.model.NutritionDisplayOption
import com.sephylon.foodfitscan.domain.model.NutritionFacts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductDisplayHelperTest {

    // formatNutrient

    @Test
    fun `formatNutrient returns value with default grams unit`() {
        assertEquals("1.5 g", ProductDisplayHelper.formatNutrient(1.5))
    }

    @Test
    fun `formatNutrient returns value with custom unit`() {
        assertEquals("100.0 kcal", ProductDisplayHelper.formatNutrient(100.0, "kcal"))
    }

    @Test
    fun `formatNutrient returns Not available for null`() {
        assertEquals("Not available", ProductDisplayHelper.formatNutrient(null))
    }

    @Test
    fun `formatNutrient formats zero correctly`() {
        assertEquals("0.0 g", ProductDisplayHelper.formatNutrient(0.0))
    }

    @Test
    fun `formatNutrient rounds to one decimal place`() {
        assertEquals("3.1 g", ProductDisplayHelper.formatNutrient(3.14159))
    }

    // orNotAvailable

    @Test
    fun `orNotAvailable returns value when non-blank`() {
        assertEquals("hello", ProductDisplayHelper.orNotAvailable("hello"))
    }

    @Test
    fun `orNotAvailable returns Not available for null`() {
        assertEquals("Not available", ProductDisplayHelper.orNotAvailable(null))
    }

    @Test
    fun `orNotAvailable returns Not available for blank string`() {
        assertEquals("Not available", ProductDisplayHelper.orNotAvailable("   "))
    }

    @Test
    fun `orNotAvailable returns Not available for empty string`() {
        assertEquals("Not available", ProductDisplayHelper.orNotAvailable(""))
    }

    // orUnknown

    @Test
    fun `orUnknown returns value when non-blank`() {
        assertEquals("Brand X", ProductDisplayHelper.orUnknown("Brand X"))
    }

    @Test
    fun `orUnknown returns Unknown for null`() {
        assertEquals("Unknown", ProductDisplayHelper.orUnknown(null))
    }

    @Test
    fun `orUnknown returns Unknown for blank string`() {
        assertEquals("Unknown", ProductDisplayHelper.orUnknown("  "))
    }

    @Test
    fun `orUnknown returns Unknown for empty string`() {
        assertEquals("Unknown", ProductDisplayHelper.orUnknown(""))
    }

    // formatTagList

    @Test
    fun `formatTagList returns None listed for empty list`() {
        assertEquals("None listed", ProductDisplayHelper.formatTagList(emptyList()))
    }

    @Test
    fun `formatTagList strips language prefix`() {
        assertEquals("milk", ProductDisplayHelper.formatTagList(listOf("en:milk")))
    }

    @Test
    fun `formatTagList replaces hyphens with spaces`() {
        assertEquals("soy beans", ProductDisplayHelper.formatTagList(listOf("en:soy-beans")))
    }

    @Test
    fun `formatTagList joins multiple tags with comma space`() {
        assertEquals("milk, eggs", ProductDisplayHelper.formatTagList(listOf("en:milk", "en:eggs")))
    }

    @Test
    fun `formatTagList handles tags without prefix`() {
        assertEquals("soy beans", ProductDisplayHelper.formatTagList(listOf("soy-beans")))
    }

    @Test
    fun `formatTagList handles single unmodified tag`() {
        assertEquals("gluten", ProductDisplayHelper.formatTagList(listOf("gluten")))
    }

    // selectedNutritionRows

    @Test
    fun `selectedNutritionRows returns only the selected fields in canonical order`() {
        val nutrition = NutritionFacts(proteinPer100g = 8.0, carbohydratesPer100g = 30.0, sugarsPer100g = 20.0)

        val rows = ProductDisplayHelper.selectedNutritionRows(nutrition, setOf("protein", "carbohydrates"))

        assertEquals(listOf("carbohydrates", "protein"), rows.map { it.key })
        assertEquals("Carbohydrates", rows[0].label)
        assertEquals("30.0 g", rows[0].value)
        assertEquals("8.0 g", rows[1].value)
    }

    @Test
    fun `selectedNutritionRows shows Not available for a missing selected value`() {
        val nutrition = NutritionFacts(proteinPer100g = 8.0) // carbohydrates missing

        val rows = ProductDisplayHelper.selectedNutritionRows(nutrition, setOf("protein", "carbohydrates"))

        val carbRow = rows.first { it.key == "carbohydrates" }
        assertEquals("Not available", carbRow.value)
    }

    @Test
    fun `selectedNutritionRows uses kcal unit for energy`() {
        val nutrition = NutritionFacts(energyKcalPer100g = 250.0)

        val rows = ProductDisplayHelper.selectedNutritionRows(nutrition, setOf("energy_kcal"))

        assertEquals("250.0 kcal", rows.single().value)
    }

    @Test
    fun `selectedNutritionRows returns all Not available when nutrition is null`() {
        val rows = ProductDisplayHelper.selectedNutritionRows(null, setOf("protein", "salt"))

        assertEquals(2, rows.size)
        assertTrue(rows.all { it.value == "Not available" })
    }

    @Test
    fun `selectedNutritionRows falls back to default fields when selection is empty`() {
        val rows = ProductDisplayHelper.selectedNutritionRows(NutritionFacts(), emptySet())

        assertEquals(NutritionDisplayOption.DEFAULT_KEYS, rows.map { it.key }.toSet())
    }

    @Test
    fun `selectedNutritionRows falls back to default fields when selection is null`() {
        val rows = ProductDisplayHelper.selectedNutritionRows(NutritionFacts(), null)

        assertEquals(NutritionDisplayOption.DEFAULT_KEYS, rows.map { it.key }.toSet())
    }

    // availableNutritionRows

    @Test
    fun `availableNutritionRows omits selected fields with no data`() {
        val nutrition = NutritionFacts(proteinPer100g = 8.0) // carbohydrates missing

        val rows = ProductDisplayHelper.availableNutritionRows(nutrition, setOf("protein", "carbohydrates"))

        assertEquals(listOf("protein"), rows.map { it.key })
        assertEquals("8.0 g", rows.single().value)
    }

    @Test
    fun `availableNutritionRows keeps canonical order for present fields`() {
        val nutrition = NutritionFacts(proteinPer100g = 8.0, carbohydratesPer100g = 30.0)

        val rows = ProductDisplayHelper.availableNutritionRows(nutrition, setOf("protein", "carbohydrates"))

        assertEquals(listOf("carbohydrates", "protein"), rows.map { it.key })
    }

    @Test
    fun `availableNutritionRows is empty when nutrition is null`() {
        val rows = ProductDisplayHelper.availableNutritionRows(null, setOf("protein", "salt"))

        assertTrue(rows.isEmpty())
    }

    @Test
    fun `availableNutritionRows is empty when no selected field has data`() {
        val rows = ProductDisplayHelper.availableNutritionRows(NutritionFacts(), setOf("protein", "salt"))

        assertTrue(rows.isEmpty())
    }

    @Test
    fun `availableNutritionRows uses kcal unit for energy`() {
        val nutrition = NutritionFacts(energyKcalPer100g = 250.0)

        val rows = ProductDisplayHelper.availableNutritionRows(nutrition, setOf("energy_kcal"))

        assertEquals("250.0 kcal", rows.single().value)
    }

    @Test
    fun `availableNutritionRows carries raw value unit and guide limit for the level bar`() {
        val nutrition = NutritionFacts(energyKcalPer100g = 250.0, sugarsPer100g = 30.0)

        val rows = ProductDisplayHelper.availableNutritionRows(nutrition, setOf("energy_kcal", "sugars"))

        val energy = rows.first { it.key == "energy_kcal" }
        assertEquals(250.0, energy.rawValue!!, 0.0)
        assertEquals("kcal", energy.unit)
        assertEquals(400.0, energy.guideLimit!!, 0.0)

        val sugars = rows.first { it.key == "sugars" }
        assertEquals(30.0, sugars.rawValue!!, 0.0)
        assertEquals(22.5, sugars.guideLimit!!, 0.0)
    }

    @Test
    fun `availableNutritionRows has no guide limit for fields without one`() {
        val nutrition = NutritionFacts(proteinPer100g = 8.0)

        val row = ProductDisplayHelper.availableNutritionRows(nutrition, setOf("protein")).single()

        assertEquals(null, row.guideLimit)
        assertEquals(8.0, row.rawValue!!, 0.0)
    }
}
