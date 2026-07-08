package com.sephylon.foodfitscan.domain.model

data class ProductDetails(
    val barcode: String,
    val name: String,
    val brand: String?,
    val quantity: String?,
    val imageFrontUrl: String?,
    val nutriScore: String?,
    val novaGroup: Int?,
    val nutrition: NutritionFacts?,
    val ingredientsText: String?,
    val allergensTags: List<String>,
    val tracesTags: List<String>,
    val additivesTags: List<String>,
    // Null means the field was absent in JSON (e.g. old cached data). Use orEmpty() at call sites.
    val categoriesTags: List<String>? = null,
)
