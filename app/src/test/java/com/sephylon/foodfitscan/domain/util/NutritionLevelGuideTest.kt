package com.sephylon.foodfitscan.domain.util

import com.sephylon.foodfitscan.domain.model.NutritionDisplayOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NutritionLevelGuideTest {

    @Test
    fun `guide limits match the documented defaults`() {
        assertEquals(400.0, NutritionLevelGuide.guideLimitFor(NutritionDisplayOption.ENERGY_KCAL)!!, 0.0)
        assertEquals(17.5, NutritionLevelGuide.guideLimitFor(NutritionDisplayOption.FAT)!!, 0.0)
        assertEquals(5.0, NutritionLevelGuide.guideLimitFor(NutritionDisplayOption.SATURATED_FAT)!!, 0.0)
        assertEquals(22.5, NutritionLevelGuide.guideLimitFor(NutritionDisplayOption.SUGARS)!!, 0.0)
        assertEquals(1.5, NutritionLevelGuide.guideLimitFor(NutritionDisplayOption.SALT)!!, 0.0)
        assertEquals(0.6, NutritionLevelGuide.guideLimitFor(NutritionDisplayOption.SODIUM)!!, 0.0)
    }

    @Test
    fun `fields without a sensible upper guide have no limit`() {
        assertNull(NutritionLevelGuide.guideLimitFor(NutritionDisplayOption.CARBOHYDRATES))
        assertNull(NutritionLevelGuide.guideLimitFor(NutritionDisplayOption.FIBER))
        assertNull(NutritionLevelGuide.guideLimitFor(NutritionDisplayOption.PROTEIN))
    }
}
