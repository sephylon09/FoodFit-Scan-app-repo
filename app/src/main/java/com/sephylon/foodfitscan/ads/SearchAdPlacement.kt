package com.sephylon.foodfitscan.ads

/** One row of the search-results list: either a real product or a native ad slot. */
sealed interface SearchRow<out T> {
    data class Product<T>(val item: T) : SearchRow<T>
    data class Ad(val slot: Int) : SearchRow<Nothing>
}

/**
 * Pure placement rules for native ads inside search results:
 *
 * - first ad after the first [FIRST_AD_AFTER] products,
 * - then one after every [REPEAT_EVERY] further products,
 * - never as the last row (an ad must always be followed by at least one product),
 * - never on short lists (fewer than [FIRST_AD_AFTER] + 1 products -> no ads),
 * - at most [MAX_AD_SLOTS] ads per result list.
 *
 * Product order is always preserved exactly as given.
 */
object SearchAdPlacement {

    const val FIRST_AD_AFTER = 6
    const val REPEAT_EVERY = 11
    const val MAX_AD_SLOTS = 3

    fun <T> buildRows(items: List<T>): List<SearchRow<T>> {
        if (items.size <= FIRST_AD_AFTER) return items.map { SearchRow.Product(it) }

        val rows = ArrayList<SearchRow<T>>(items.size + MAX_AD_SLOTS)
        var slot = 0
        var productsSinceLastAd = 0
        var nextAdAfter = FIRST_AD_AFTER
        items.forEachIndexed { index, item ->
            rows += SearchRow.Product(item)
            productsSinceLastAd++
            val moreProductsFollow = index < items.lastIndex
            if (slot < MAX_AD_SLOTS && productsSinceLastAd == nextAdAfter && moreProductsFollow) {
                rows += SearchRow.Ad(slot)
                slot++
                productsSinceLastAd = 0
                nextAdAfter = REPEAT_EVERY
            }
        }
        return rows
    }
}
