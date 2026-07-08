package com.sephylon.foodfitscan.data.mapper

import com.google.gson.Gson
import com.sephylon.foodfitscan.data.local.CachedProductEntity
import com.sephylon.foodfitscan.domain.model.NutritionFacts
import com.sephylon.foodfitscan.domain.model.ProductDetails
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ProductDetailsCacheMapperTest {

    private val mapper = ProductDetailsCacheMapper(Gson())

    @Test
    fun `toEntity maps all fields correctly`() {
        val product = fullProduct()

        val entity = mapper.toEntity(product)

        assertEquals(product.barcode, entity.barcode)
        assertEquals(product.name, entity.productName)
        assertEquals(product.brand, entity.brand)
        assertEquals(product.quantity, entity.quantity)
        assertEquals(product.imageFrontUrl, entity.imageFrontUrl)
        assertEquals(product.nutriScore, entity.nutriScore)
        assertEquals(product.novaGroup, entity.novaGroup)
        assertNotNull(entity.serializedProductJson)
    }

    @Test
    fun `fromEntity restores ProductDetails from JSON round-trip`() {
        val original = fullProduct()
        val entity = mapper.toEntity(original)

        val restored = mapper.fromEntity(entity)

        assertNotNull(restored)
        assertEquals(original.barcode, restored!!.barcode)
        assertEquals(original.name, restored.name)
        assertEquals(original.brand, restored.brand)
        assertEquals(original.quantity, restored.quantity)
        assertEquals(original.imageFrontUrl, restored.imageFrontUrl)
        assertEquals(original.nutriScore, restored.nutriScore)
        assertEquals(original.novaGroup, restored.novaGroup)
        assertEquals(original.allergensTags, restored.allergensTags)
        assertEquals(original.tracesTags, restored.tracesTags)
        assertEquals(original.additivesTags, restored.additivesTags)
    }

    @Test
    fun `fromEntity handles nullable fields in round-trip`() {
        val original = ProductDetails(
            barcode = "01234567890",
            name = "Minimal product",
            brand = null,
            quantity = null,
            imageFrontUrl = null,
            nutriScore = null,
            novaGroup = null,
            nutrition = null,
            ingredientsText = null,
            allergensTags = emptyList(),
            tracesTags = emptyList(),
            additivesTags = emptyList(),
        )
        val entity = mapper.toEntity(original)

        val restored = mapper.fromEntity(entity)

        assertNotNull(restored)
        assertEquals(original, restored)
    }

    @Test
    fun `fromEntity returns null for corrupt JSON`() {
        val badEntity = CachedProductEntity(
            barcode = "00000000",
            productName = null,
            brand = null,
            quantity = null,
            imageFrontUrl = null,
            nutriScore = null,
            novaGroup = null,
            cachedAtMillis = System.currentTimeMillis(),
            serializedProductJson = "NOT_VALID_JSON{{",
        )

        val result = mapper.fromEntity(badEntity)

        assertNull(result)
    }

    private fun fullProduct() = ProductDetails(
        barcode = "5449000000996",
        name = "Coca-Cola",
        brand = "Coca-Cola",
        quantity = "330ml",
        imageFrontUrl = "https://example.com/image.jpg",
        nutriScore = "e",
        novaGroup = 4,
        nutrition = NutritionFacts(
            energyKcalPer100g = 42.0,
            fatPer100g = 0.0,
            carbohydratesPer100g = 10.6,
            sugarsPer100g = 10.6,
        ),
        ingredientsText = "Carbonated water, sugar, colour (caramel E150d)",
        allergensTags = listOf("en:gluten"),
        tracesTags = emptyList(),
        additivesTags = listOf("en:e150d"),
    )
}
