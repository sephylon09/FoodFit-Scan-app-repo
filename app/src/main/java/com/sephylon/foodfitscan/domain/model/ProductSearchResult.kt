package com.sephylon.foodfitscan.domain.model

/**
 * State of a product-name search. Searches run only after an explicit submit
 * (no search-as-you-type). [ValidationError] is produced locally for blank/short
 * queries; [Empty]/[Success]/[NetworkError]/[UnknownError] come back from the
 * search repository.
 */
sealed interface ProductSearchResult {
    data object Idle : ProductSearchResult
    data object Loading : ProductSearchResult
    data class Success(val items: List<ProductSearchItem>) : ProductSearchResult
    data object Empty : ProductSearchResult
    data class ValidationError(val message: String) : ProductSearchResult
    data class NetworkError(val message: String) : ProductSearchResult
    data class UnknownError(val message: String) : ProductSearchResult
}
