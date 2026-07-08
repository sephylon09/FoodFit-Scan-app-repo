package com.sephylon.foodfitscan.domain.model

data class AlternativeProduct(
    val barcode: String,
    val productName: String,
    val brand: String?,
    val imageFrontUrl: String?,
    val nutriScore: String?,
    val novaGroup: Int?,
    val suitabilityLevel: SuitabilityLevel,
    val shortReasons: List<String>,
    val sugarsPer100g: Double?,
    val saltPer100g: Double?,
    val saturatedFatPer100g: Double?,
)
