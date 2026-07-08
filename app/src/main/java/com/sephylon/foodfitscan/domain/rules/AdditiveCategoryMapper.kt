package com.sephylon.foodfitscan.domain.rules

internal object AdditiveCategoryMapper {
    private val FLAVOUR_ENHANCERS = setOf(
        "en:e620", "en:e621", "en:e622", "en:e623", "en:e624", "en:e625",
    )
    private val ARTIFICIAL_SWEETENERS = setOf(
        "en:e950", "en:e951", "en:e952", "en:e954", "en:e955", "en:e960", "en:e961", "en:e962",
    )
    private val ARTIFICIAL_COLOURS = setOf(
        "en:e102", "en:e104", "en:e110", "en:e122", "en:e124", "en:e129", "en:e133",
    )
    private val PRESERVATIVES = setOf(
        "en:e200", "en:e202", "en:e210", "en:e211", "en:e220", "en:e221", "en:e250", "en:e251", "en:e252",
    )

    private val categoryMap: Map<String, Set<String>> = mapOf(
        "flavour-enhancers" to FLAVOUR_ENHANCERS,
        "artificial-sweeteners" to ARTIFICIAL_SWEETENERS,
        "artificial-colours" to ARTIFICIAL_COLOURS,
        "preservatives" to PRESERVATIVES,
    )

    fun tagsForCategory(categoryKey: String): Set<String> = categoryMap[categoryKey] ?: emptySet()

    fun matchingCategories(additiveTags: List<String>, avoidedCategories: Set<String>): List<String> =
        avoidedCategories.filter { category ->
            val tags = tagsForCategory(category)
            additiveTags.any { it in tags }
        }
}
