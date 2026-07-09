package com.sephylon.foodfitscan.domain.model

/**
 * Lightweight product-name search result shown on the Home screen. Tapping one opens
 * [ProductDetails] via [barcode]; the full Open Food Facts lookup then loads the details.
 */
data class ProductSearchItem(
    val barcode: String,
    val name: String,
    val brand: String? = null,
    val imageUrl: String? = null,
)
