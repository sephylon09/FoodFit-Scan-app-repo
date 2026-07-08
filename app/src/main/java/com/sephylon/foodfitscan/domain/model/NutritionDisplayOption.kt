package com.sephylon.foodfitscan.domain.model

/**
 * A stable, user-selectable nutrition field that can be shown prominently on the
 * product result screen. This model controls **display only** — it must never be
 * used by the suitability scoring engine, which always reads the underlying
 * [NutritionFacts] values it needs based on the user's nutrition caps.
 */
enum class NutritionDisplayOption(
    val key: String,
    val displayName: String,
    val unit: String,
    val shortDescription: String,
    private val valueSelector: (NutritionFacts) -> Double?,
) {
    ENERGY_KCAL(
        key = "energy_kcal",
        displayName = "Energy / calories",
        unit = "kcal",
        shortDescription = "Energy per 100 g",
        valueSelector = { it.energyKcalPer100g },
    ),
    FAT(
        key = "fat",
        displayName = "Fat",
        unit = "g",
        shortDescription = "Total fat per 100 g",
        valueSelector = { it.fatPer100g },
    ),
    SATURATED_FAT(
        key = "saturated_fat",
        displayName = "Saturated fat",
        unit = "g",
        shortDescription = "Saturated fat per 100 g",
        valueSelector = { it.saturatedFatPer100g },
    ),
    CARBOHYDRATES(
        key = "carbohydrates",
        displayName = "Carbohydrates",
        unit = "g",
        shortDescription = "Carbohydrates per 100 g",
        valueSelector = { it.carbohydratesPer100g },
    ),
    SUGARS(
        key = "sugars",
        displayName = "Sugars",
        unit = "g",
        shortDescription = "Sugars per 100 g",
        valueSelector = { it.sugarsPer100g },
    ),
    FIBER(
        key = "fiber",
        displayName = "Fiber",
        unit = "g",
        shortDescription = "Fiber per 100 g",
        valueSelector = { it.fiberPer100g },
    ),
    PROTEIN(
        key = "protein",
        displayName = "Protein",
        unit = "g",
        shortDescription = "Protein per 100 g",
        valueSelector = { it.proteinPer100g },
    ),
    SALT(
        key = "salt",
        displayName = "Salt",
        unit = "g",
        shortDescription = "Salt per 100 g",
        valueSelector = { it.saltPer100g },
    ),
    SODIUM(
        key = "sodium",
        displayName = "Sodium",
        unit = "g",
        shortDescription = "Sodium per 100 g",
        valueSelector = { it.sodiumPer100g },
    );

    /** Returns the matching per-100g value from [nutrition], or null when unavailable. */
    fun valueFrom(nutrition: NutritionFacts): Double? = valueSelector(nutrition)

    companion object {
        /** All options, in canonical display order. */
        val ALL: List<NutritionDisplayOption> = entries.toList()

        /**
         * Practical default selection for general users. Applied whenever the stored
         * selection is missing or empty.
         */
        val DEFAULT_KEYS: Set<String> = setOf(
            ENERGY_KCAL.key,
            SUGARS.key,
            SALT.key,
            SATURATED_FAT.key,
            PROTEIN.key,
        )

        private val byKey: Map<String, NutritionDisplayOption> = entries.associateBy { it.key }

        /** Looks up an option by its stable key, or null if the key is unknown. */
        fun fromKey(key: String): NutritionDisplayOption? = byKey[key]

        /**
         * Resolves a stored set of keys into an ordered list of options in canonical
         * [ALL] order. Falls back to [DEFAULT_KEYS] when [keys] is null, empty, or
         * contains no recognised keys, so the result is never empty.
         */
        fun resolveSelected(keys: Set<String>?): List<NutritionDisplayOption> {
            val effective = if (keys.isNullOrEmpty()) DEFAULT_KEYS else keys
            val resolved = ALL.filter { it.key in effective }
            return resolved.ifEmpty { ALL.filter { it.key in DEFAULT_KEYS } }
        }
    }
}
