package com.sephylon.foodfitscan.data.remote

import com.google.gson.annotations.SerializedName

internal data class ProductResponseDto(
    @SerializedName("status") val status: Int?,
    @SerializedName("status_verbose") val statusVerbose: String?,
    @SerializedName("code") val code: String?,
    @SerializedName("product") val product: ProductDto?,
)

internal data class ProductDto(
    @SerializedName("code") val code: String?,
    @SerializedName("product_name") val productName: String?,
    @SerializedName("generic_name") val genericName: String?,
    @SerializedName("brands") val brands: String?,
    @SerializedName("quantity") val quantity: String?,
    @SerializedName("categories") val categories: String?,
    @SerializedName("categories_tags") val categoriesTags: List<String>?,
    @SerializedName("image_front_url") val imageFrontUrl: String?,
    @SerializedName("image_ingredients_url") val imageIngredientsUrl: String?,
    @SerializedName("image_nutrition_url") val imageNutritionUrl: String?,
    @SerializedName("nutriments") val nutriments: NutrimentsDto?,
    @SerializedName("ingredients_text") val ingredientsText: String?,
    @SerializedName("ingredients_text_en") val ingredientsTextEn: String?,
    @SerializedName("allergens") val allergens: String?,
    @SerializedName("allergens_tags") val allergensTags: List<String>?,
    @SerializedName("traces") val traces: String?,
    @SerializedName("traces_tags") val tracesTags: List<String>?,
    @SerializedName("additives_tags") val additivesTags: List<String>?,
    @SerializedName("nutrition_grades") val nutritionGrades: String?,
    @SerializedName("nutriscore_grade") val nutriscoreGrade: String?,
    // Returned as a number 1-4; Double? handles both integer and float JSON forms
    @SerializedName("nova_group") val novaGroup: Double?,
    @SerializedName("misc_tags") val miscTags: List<String>?,
    @SerializedName("states_tags") val statesTags: List<String>?,
    @SerializedName("last_modified_t") val lastModifiedT: Long?,
)

internal data class SearchResponseDto(
    @SerializedName("count") val count: Int?,
    @SerializedName("page") val page: Int?,
    @SerializedName("page_size") val pageSize: Int?,
    @SerializedName("products") val products: List<ProductDto>?,
)

internal data class NutrimentsDto(
    @SerializedName("energy-kcal_100g") val energyKcal100g: Double?,
    @SerializedName("fat_100g") val fat100g: Double?,
    @SerializedName("saturated-fat_100g") val saturatedFat100g: Double?,
    @SerializedName("carbohydrates_100g") val carbohydrates100g: Double?,
    @SerializedName("sugars_100g") val sugars100g: Double?,
    @SerializedName("fiber_100g") val fiber100g: Double?,
    @SerializedName("proteins_100g") val proteins100g: Double?,
    @SerializedName("salt_100g") val salt100g: Double?,
    @SerializedName("sodium_100g") val sodium100g: Double?,
)
