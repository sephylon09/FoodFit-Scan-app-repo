package com.sephylon.foodfitscan.domain.model

data class NutritionFacts(
    val energyKcalPer100g: Double? = null,
    val fatPer100g: Double? = null,
    val saturatedFatPer100g: Double? = null,
    val carbohydratesPer100g: Double? = null,
    val sugarsPer100g: Double? = null,
    val fiberPer100g: Double? = null,
    val proteinPer100g: Double? = null,
    val saltPer100g: Double? = null,
    val sodiumPer100g: Double? = null,
)
