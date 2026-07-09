package com.sephylon.foodfitscan.domain.util

/**
 * Display-quality rules for product-name search results. The Firestore index contains many
 * near-empty or junk documents (test entries, barcode-as-name products, entries with no
 * image/brand/categories). These rules decide, app-side, which results are worth showing
 * and how to order them — nothing is deleted or written server-side.
 */
object SearchResultQuality {

    /** Results with a photo — always shown first. */
    const val TIER_IMAGE = 0

    /** No photo, but a brand gives the row enough identity. */
    const val TIER_BRAND = 1

    /** No photo or brand; kept only because other metadata (categories) made it displayable. */
    const val TIER_WEAK = 2

    /**
     * Normalized names that identify placeholder/junk documents rather than real products.
     * Compared against [SearchTextNormalizer.normalize] output, so entries here must be
     * lowercase, punctuation-free, single-spaced.
     */
    private val suppressedNames = setOf(
        "test", "tests", "testing", "test product",
        "product", "products", "produit", "producto", "new product",
        "unknown", "none", "null", "na", "n a",
        "sample", "example", "misc", "todo", "tbd", "xxx",
        "no name", "noname", "sans nom", "sin nombre",
        "food", "item", "article",
    )

    private val digitsOnly = Regex("[0-9 ]+")

    /**
     * Whether a search-index entry carries enough meaningful information to show to the
     * user. A product needs a usable name plus at least one supporting signal (image,
     * brand, or categories); entries failing this are hidden from search results.
     */
    fun isDisplayable(
        name: String?,
        brand: String?,
        imageUrl: String?,
        categoriesCount: Int,
    ): Boolean {
        val normalizedName = SearchTextNormalizer.normalize(name.orEmpty())
        if (normalizedName.length < SearchTextNormalizer.MIN_PREFIX_LENGTH) return false
        if (normalizedName in suppressedNames) return false
        // A purely numeric "name" is almost always the barcode pasted into the name field.
        if (digitsOnly.matches(normalizedName)) return false

        return hasImage(imageUrl) || hasBrand(brand) || categoriesCount > 0
    }

    /**
     * Ranking tier for a displayable result: [TIER_IMAGE] < [TIER_BRAND] < [TIER_WEAK].
     * Lower sorts first.
     */
    fun displayTier(brand: String?, imageUrl: String?): Int = when {
        hasImage(imageUrl) -> TIER_IMAGE
        hasBrand(brand) -> TIER_BRAND
        else -> TIER_WEAK
    }

    private fun hasImage(imageUrl: String?) = !imageUrl.isNullOrBlank()

    private fun hasBrand(brand: String?) = !brand.isNullOrBlank()
}
