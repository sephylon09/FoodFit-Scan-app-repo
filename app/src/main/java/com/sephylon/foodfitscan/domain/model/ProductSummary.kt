package com.sephylon.foodfitscan.domain.model

data class ProductSummary(
    val barcode: String,
    val name: String?,
    val brand: String? = null,
    val imageUrl: String? = null,
    val nutriScore: String? = null,
    val novaGroup: Int? = null,
)
