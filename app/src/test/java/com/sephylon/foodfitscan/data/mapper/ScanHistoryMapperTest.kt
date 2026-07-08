package com.sephylon.foodfitscan.data.mapper

import com.sephylon.foodfitscan.domain.model.LookupStatus
import com.sephylon.foodfitscan.domain.model.NutritionFacts
import com.sephylon.foodfitscan.domain.model.ProductDetails
import com.sephylon.foodfitscan.domain.model.ProductLookupResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScanHistoryMapperTest {

    private val mapper = ScanHistoryMapper()

    @Test
    fun `toEntity maps Found result correctly`() {
        val product = sampleProduct()

        val entity = mapper.toEntity(product.barcode, ProductLookupResult.Found(product))

        assertEquals(product.barcode, entity.barcode)
        assertEquals(product.name, entity.productName)
        assertEquals(product.brand, entity.brand)
        assertEquals(LookupStatus.FOUND.name, entity.lookupStatus)
        assertNull(entity.shortMessage)
    }

    @Test
    fun `toEntity maps NotFound result correctly`() {
        val barcode = "5449000000996"

        val entity = mapper.toEntity(barcode, ProductLookupResult.NotFound(barcode))

        assertEquals(barcode, entity.barcode)
        assertNull(entity.productName)
        assertNull(entity.brand)
        assertEquals(LookupStatus.NOT_FOUND.name, entity.lookupStatus)
    }

    @Test
    fun `toEntity maps NetworkError result correctly`() {
        val barcode = "5449000000996"
        val message = "Connection refused"

        val entity = mapper.toEntity(barcode, ProductLookupResult.NetworkError(message))

        assertEquals(barcode, entity.barcode)
        assertEquals(LookupStatus.NETWORK_ERROR.name, entity.lookupStatus)
        assertEquals(message, entity.shortMessage)
    }

    @Test
    fun `toEntity maps UnknownError result correctly`() {
        val barcode = "5449000000996"

        val entity = mapper.toEntity(barcode, ProductLookupResult.UnknownError("Oops"))

        assertEquals(LookupStatus.UNKNOWN_ERROR.name, entity.lookupStatus)
    }

    @Test
    fun `toHistoryItem maps entity to ScanHistoryItem`() {
        val product = sampleProduct()
        val entity = mapper.toEntity(product.barcode, ProductLookupResult.Found(product))
        val entityWithId = entity.copy(id = 42L)

        val item = mapper.toHistoryItem(entityWithId)

        assertEquals(42L, item.id)
        assertEquals(product.barcode, item.barcode)
        assertEquals(product.name, item.productName)
        assertEquals(LookupStatus.FOUND, item.lookupStatus)
    }

    @Test
    fun `toHistoryItem handles unknown status gracefully`() {
        val product = sampleProduct()
        val entity = mapper.toEntity(product.barcode, ProductLookupResult.Found(product))
            .copy(lookupStatus = "GARBAGE_VALUE")

        val item = mapper.toHistoryItem(entity)

        assertEquals(LookupStatus.UNKNOWN_ERROR, item.lookupStatus)
    }

    private fun sampleProduct() = ProductDetails(
        barcode = "5449000000996",
        name = "Test Product",
        brand = "Test Brand",
        quantity = "100g",
        imageFrontUrl = "https://example.com/img.jpg",
        nutriScore = "b",
        novaGroup = 2,
        nutrition = NutritionFacts(energyKcalPer100g = 100.0),
        ingredientsText = "Water",
        allergensTags = emptyList(),
        tracesTags = emptyList(),
        additivesTags = emptyList(),
    )
}
