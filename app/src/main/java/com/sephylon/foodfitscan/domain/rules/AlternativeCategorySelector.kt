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

    // OFF categories_tags are ordered general → specific; pick the last useful English tag.
    fun selectCategoryTag(product: ProductDetails): String? {
        val tags = product.categoriesTags ?: return null
        if (tags.isEmpty()) return null
        return tags.lastOrNull { it.startsWith("en:") && it !in veryGenericTags }
            ?: tags.lastOrNull { it !in veryGenericTags }
            ?: tags.lastOrNull()
    }
}
