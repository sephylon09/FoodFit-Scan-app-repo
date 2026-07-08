package com.sephylon.foodfitscan.core.network

import com.sephylon.foodfitscan.domain.model.ProductDetails
import com.sephylon.foodfitscan.domain.model.ProductLookupResult

interface OpenFoodFactsClient {
    suspend fun getProduct(barcode: String): ProductLookupResult
    // Throws IOException / Exception on network failure; caller is responsible for catching.
    suspend fun searchByCategory(categoryTag: String, pageSize: Int): List<ProductDetails>
}
