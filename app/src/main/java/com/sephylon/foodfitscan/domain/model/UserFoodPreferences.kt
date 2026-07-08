package com.sephylon.foodfitscan.domain.model

data class UserFoodPreferences(
    val allergensToAvoid: Set<String> = emptySet(),
    val additivesToAvoid: Set<String> = emptySet(),
    val avoidUltraProcessed: Boolean = false,
    val maxSugarsPer100g: Double? = null,
    val maxSaltPer100g: Double? = null,
    val maxSaturatedFatPer100g: Double? = null,
)
