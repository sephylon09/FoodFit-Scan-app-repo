package com.sephylon.foodfitscan.domain.rules

import com.sephylon.foodfitscan.domain.model.ProductDetails

object AlternativeCategorySelector {

    private val veryGenericTags = setOf(
        "en:foods-and-drinks",
        "en:plant-based-foods-and-beverages",
        "en:plant-based-foods",
        "en:groceries",
        "en:food",
        "en:non-food-products",
    )

    /**
     * Category tags to search for alternatives, most specific first, at most [max].
     * OFF `categories_tags` are ordered general → specific, so the list is read from the
     * end. Prefers specific English tags; falls back to the last non-generic tag, then
     * the last tag of any kind, so a product with any category data returns at least one.
     */
    fun selectCategoryTags(product: ProductDetails, max: Int = 2): List<String> {
        val tags = product.categoriesTags.orEmpty()
        if (tags.isEmpty()) return emptyList()

        val specific = specificCategoryTags(tags)
        if (specific.isNotEmpty()) return specific.takeLast(max).reversed()

        val nonGeneric = tags.lastOrNull { it !in veryGenericTags }
        return listOfNotNull(nonGeneric ?: tags.lastOrNull())
    }

    /** The single best (most specific) category tag, or null without category data. */
    fun selectCategoryTag(product: ProductDetails): String? =
        selectCategoryTags(product).firstOrNull()

    /**
     * The meaningful (English, non-generic) category tags of a tag list — the basis for
     * category-overlap similarity between a product and an alternative candidate.
     */
    fun specificCategoryTags(tags: List<String>?): List<String> =
        tags.orEmpty().filter { it.startsWith("en:") && it !in veryGenericTags }
}
