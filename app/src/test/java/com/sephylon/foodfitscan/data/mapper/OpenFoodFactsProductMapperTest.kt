package com.sephylon.foodfitscan.data.mapper

import com.sephylon.foodfitscan.data.remote.NutrimentsDto
import com.sephylon.foodfitscan.data.remote.ProductDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenFoodFactsProductMapperTest {

    private val mapper = OpenFoodFactsProductMapper()

    @Test
    fun `maps complete product data correctly`() {
        val dto = fullProductDto()
        val result = mapper.map("5449000000996", dto)

        assertEquals("5449000000996", result.barcode)
        assertEquals("Coca-Cola", result.name)
        assertEquals("Coca-Cola", result.brand)
        assertEquals("500ml", result.quantity)
        assertEquals("https://example.com/image.jpg", result.imageFrontUrl)
        assertEquals("e", result.nutriScore)
        assertEquals(4, result.novaGroup)
        assertNotNull(result.nutrition)
        assertEquals(42.0, result.nutrition!!.energyKcalPer100g)
        assertEquals(10.6, result.nutrition!!.carbohydratesPer100g)
        assertEquals("Water, sugar, caramel color", result.ingredientsText)
        assertEquals(listOf("en:milk"), result.allergensTags)
        assertEquals(listOf("en:eggs"), result.tracesTags)
        assertEquals(listOf("en:e150d"), result.additivesTags)
    }

    @Test
    fun `missing product name defaults to Unknown product`() {
        val dto = emptyProductDto(productName = null)
        val result = mapper.map("1234567890123", dto)
        assertEquals("Unknown product", result.name)
    }

    @Test
    fun `blank product name defaults to Unknown product`() {
        val dto = emptyProductDto(productName = "   ")
        val result = mapper.map("1234567890123", dto)
        assertEquals("Unknown product", result.name)
    }

    @Test
    fun `missing nutriments returns null nutrition`() {
        val dto = emptyProductDto(nutriments = null)
        val result = mapper.map("1234567890123", dto)
        assertNull(result.nutrition)
    }

    @Test
    fun `all-null nutriments values returns null nutrition`() {
        val dto = emptyProductDto(
            nutriments = NutrimentsDto(
                energyKcal100g = null,
                fat100g = null,
                saturatedFat100g = null,
                carbohydrates100g = null,
                sugars100g = null,
                fiber100g = null,
                proteins100g = null,
                salt100g = null,
                sodium100g = null,
            ),
        )
        val result = mapper.map("1234567890123", dto)
        assertNull(result.nutrition)
    }

    @Test
    fun `missing allergens tags returns empty list`() {
        val dto = emptyProductDto(allergensTags = null)
        val result = mapper.map("1234567890123", dto)
        assertTrue(result.allergensTags.isEmpty())
    }

    @Test
    fun `missing traces tags returns empty list`() {
        val dto = emptyProductDto(tracesTags = null)
        val result = mapper.map("1234567890123", dto)
        assertTrue(result.tracesTags.isEmpty())
    }

    @Test
    fun `missing additives tags returns empty list`() {
        val dto = emptyProductDto(additivesTags = null)
        val result = mapper.map("1234567890123", dto)
        assertTrue(result.additivesTags.isEmpty())
    }

    @Test
    fun `nutriscore_grade takes priority over nutrition_grades`() {
        val dto = emptyProductDto().copy(
            nutriscoreGrade = "a",
            nutritionGrades = "b",
        )
        val result = mapper.map("1234567890123", dto)
        assertEquals("a", result.nutriScore)
    }

    @Test
    fun `nova group is converted from Double to Int`() {
        val dto = emptyProductDto().copy(novaGroup = 3.0)
        val result = mapper.map("1234567890123", dto)
        assertEquals(3, result.novaGroup)
    }

    @Test
    fun `missing nova group returns null`() {
        val dto = emptyProductDto(novaGroup = null)
        val result = mapper.map("1234567890123", dto)
        assertNull(result.novaGroup)
    }

    @Test
    fun `dto code is used as barcode if present`() {
        val dto = emptyProductDto().copy(code = "0000000000001")
        val result = mapper.map("1234567890123", dto)
        assertEquals("0000000000001", result.barcode)
    }

    @Test
    fun `fallback barcode is used when dto code is null`() {
        val dto = emptyProductDto().copy(code = null)
        val result = mapper.map("1234567890123", dto)
        assertEquals("1234567890123", result.barcode)
    }

    @Test
    fun `English ingredients text takes priority over generic ingredients text`() {
        val dto = emptyProductDto().copy(
            ingredientsTextEn = "Water, sugar",
            ingredientsText = "Eau, sucre",
        )
        val result = mapper.map("1234567890123", dto)
        assertEquals("Water, sugar", result.ingredientsText)
    }

    // --- helpers ---

    private fun fullProductDto() = ProductDto(
        code = "5449000000996",
        productName = "Coca-Cola",
        genericName = "Sparkling beverage",
        brands = "Coca-Cola",
        quantity = "500ml",
        categories = null,
        categoriesTags = listOf("en:beverages"),
        imageFrontUrl = "https://example.com/image.jpg",
        imageIngredientsUrl = null,
        imageNutritionUrl = null,
        nutriments = NutrimentsDto(
            energyKcal100g = 42.0,
            fat100g = 0.0,
            saturatedFat100g = 0.0,
            carbohydrates100g = 10.6,
            sugars100g = 10.6,
            fiber100g = 0.0,
            proteins100g = 0.0,
            salt100g = 0.01,
            sodium100g = 0.004,
        ),
        ingredientsText = "Water, sugar, caramel color",
        ingredientsTextEn = null,
        allergens = null,
        allergensTags = listOf("en:milk"),
        traces = null,
        tracesTags = listOf("en:eggs"),
        additivesTags = listOf("en:e150d"),
        nutritionGrades = "e",
        nutriscoreGrade = "e",
        novaGroup = 4.0,
        miscTags = null,
        statesTags = null,
        lastModifiedT = null,
    )

    private fun emptyProductDto(
        productName: String? = "Test Product",
        nutriments: NutrimentsDto? = null,
        allergensTags: List<String>? = null,
        tracesTags: List<String>? = null,
        additivesTags: List<String>? = null,
        novaGroup: Double? = null,
    ) = ProductDto(
        code = "1234567890123",
        productName = productName,
        genericName = null,
        brands = null,
        quantity = null,
        categories = null,
        categoriesTags = null,
        imageFrontUrl = null,
        imageIngredientsUrl = null,
        imageNutritionUrl = null,
        nutriments = nutriments,
        ingredientsText = null,
        ingredientsTextEn = null,
        allergens = null,
        allergensTags = allergensTags,
        traces = null,
        tracesTags = tracesTags,
        additivesTags = additivesTags,
        nutritionGrades = null,
        nutriscoreGrade = null,
        novaGroup = novaGroup,
        miscTags = null,
        statesTags = null,
        lastModifiedT = null,
    )
}
