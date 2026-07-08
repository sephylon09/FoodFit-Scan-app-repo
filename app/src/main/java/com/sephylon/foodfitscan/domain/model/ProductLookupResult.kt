package com.sephylon.foodfitscan.domain.model

sealed class ProductLookupResult {
    data class Found(
        val product: ProductDetails,
        val isFromStaleCache: Boolean = false,
    ) : ProductLookupResult()
    data class NotFound(val barcode: String) : ProductLookupResult()
    data class NetworkError(val message: String) : ProductLookupResult()
    data class UnknownError(val message: String) : ProductLookupResult()
}
