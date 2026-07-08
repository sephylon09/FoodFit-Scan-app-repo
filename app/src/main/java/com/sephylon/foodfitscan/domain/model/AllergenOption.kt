package com.sephylon.foodfitscan.domain.model

data class AllergenOption(
    val key: String,
    val displayName: String,
) {
    companion object {
        val ALL = listOf(
            AllergenOption("en:milk", "Milk"),
            AllergenOption("en:eggs", "Eggs"),
            AllergenOption("en:peanuts", "Peanuts"),
            AllergenOption("en:nuts", "Tree nuts"),
            AllergenOption("en:soybeans", "Soy"),
            AllergenOption("en:gluten", "Wheat / gluten"),
            AllergenOption("en:fish", "Fish"),
            AllergenOption("en:crustaceans", "Shellfish"),
            AllergenOption("en:sesame-seeds", "Sesame"),
        )
    }
}
