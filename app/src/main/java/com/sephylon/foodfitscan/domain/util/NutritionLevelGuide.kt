package com.sephylon.foodfitscan.domain.util

import com.sephylon.foodfitscan.domain.model.NutritionDisplayOption

/**
 * Default per-100g "acceptable" guide limits used by the animated nutrition level bars.
 * Values for fat, saturated fat, sugars, salt, and sodium follow the UK FSA front-of-pack
 * "high" traffic-light thresholds; energy uses a practical 400 kcal guide. Fields with no
 * sensible upper guide (carbohydrates, fiber, protein) return null and render without a bar.
 *
 * Display-only — never used by the suitability scoring engine, which reads the user's own
 * nutrition caps.
 */
object NutritionLevelGuide {

    fun guideLimitFor(option: NutritionDisplayOption): Double? = when (option) {
        NutritionDisplayOption.ENERGY_KCAL -> 400.0
        NutritionDisplayOption.FAT -> 17.5
        NutritionDisplayOption.SATURATED_FAT -> 5.0
        NutritionDisplayOption.SUGARS -> 22.5
        NutritionDisplayOption.SALT -> 1.5
        NutritionDisplayOption.SODIUM -> 0.6
        NutritionDisplayOption.CARBOHYDRATES,
        NutritionDisplayOption.FIBER,
        NutritionDisplayOption.PROTEIN,
        -> null
    }
}
