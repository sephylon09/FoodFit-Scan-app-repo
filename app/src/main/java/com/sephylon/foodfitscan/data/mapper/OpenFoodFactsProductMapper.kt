package com.sephylon.foodfitscan.data.mapper

import com.sephylon.foodfitscan.data.remote.NutrimentsDto
import com.sephylon.foodfitscan.data.remote.ProductDto
import com.sephylon.foodfitscan.domain.model.NutritionFacts
import com.sephylon.foodfitscan.domain.model.ProductDetails

internal class OpenFoodFactsProductMapper {

    fun map(barcode: String, dto: ProductDto): ProductDetails = ProductDetails(
        barcode = dto.code ?: barcode,
        name = dto.productName?.takeIf { it.isNotBlank() } ?: "Unknown product",
        brand = dto.brands?.takeIf { it.isNotBlank() },
        quantity = dto.quantity?.takeIf { it.isNotBlank() },
        imageFrontUrl = dto.imageFrontUrl?.takeIf { it.isNotBlank() },
        nutriScore = resolveNutriScore(dto),
        novaGroup = dto.novaGroup?.toInt(),
        nutrition = dto.nutriments?.let { mapNutrition(it) },
        ingredientsText = (dto.ingredientsTextEn ?: dto.ingredientsText)?.takeIf { it.isNotBlank() },
        allergensTags = dto.allergensTags.orEmpty(),
        tracesTags = dto.tracesTags.orEmpty(),
        additivesTags = dto.additivesTags.orEmpty(),
        categoriesTags = dto.categoriesTags?.takeIf { it.isNotEmpty() },
    )

    private fun resolveNutriScore(dto: ProductDto): String? {
        val grade = (dto.nutriscoreGrade ?: dto.nutritionGrades)?.lowercase()?.trim() ?: return null
        return grade.takeIf { it.isNotBlank() && it != "not-applicable" && it != "unknown" }
    }

    private fun mapNutrition(dto: NutrimentsDto): NutritionFacts? {
        if (dto.energyKcal100g == null && dto.fat100g == null && dto.proteins100g == null) return null
        return NutritionFacts(
            energyKcalPer100g = dto.energyKcal100g,
            fatPer100g = dto.fat100g,
            saturatedFatPer100g = dto.saturatedFat100g,
            carbohydratesPer100g = dto.carbohydrates100g,
            sugarsPer100g = dto.sugars100g,
            fiberPer100g = dto.fiber100g,
            proteinPer100g = dto.proteins100g,
            saltPer100g = dto.salt100g,
            sodiumPer100g = dto.sodium100g,
        )
    }
}
