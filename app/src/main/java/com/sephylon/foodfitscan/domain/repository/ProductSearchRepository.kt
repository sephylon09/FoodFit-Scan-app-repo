package com.sephylon.foodfitscan.domain.repository

import com.sephylon.foodfitscan.domain.model.ProductSearchResult

/**
 * Product-name search against the lightweight `product_search_index`. Callers must gate
 * this behind an explicit user submit — there is no search-as-you-type.
 */
interface ProductSearchRepository {
    /**
     * Runs a product-name search for [rawQuery]. Returns [ProductSearchResult.Success] with
     * ranked items, [ProductSearchResult.Empty] when nothing matches (or the query has no
     * searchable term), or a [ProductSearchResult.NetworkError] / [ProductSearchResult.UnknownError].
     */
    suspend fun searchByName(rawQuery: String): ProductSearchResult
}
