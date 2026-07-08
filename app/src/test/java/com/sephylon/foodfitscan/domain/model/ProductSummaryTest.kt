package com.sephylon.foodfitscan.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProductSummaryTest {

    @Test
    fun `ProductSummary can be created with barcode and name`() {
        val product = ProductSummary(
            barcode = "5449000000996",
            name = "Coca-Cola Original",
        )
        assertEquals("5449000000996", product.barcode)
        assertEquals("Coca-Cola Original", product.name)
        assertNull(product.brand)
        assertNull(product.nutriScore)
        assertNull(product.novaGroup)
        assertNull(product.imageUrl)
    }

    @Test
    fun `ProductSummary with null name is valid`() {
        val product = ProductSummary(barcode = "1234567890123", name = null)
        assertEquals("1234567890123", product.barcode)
        assertNull(product.name)
    }

    @Test
    fun `ProductSummary with all fields set stores correctly`() {
        val product = ProductSummary(
            barcode = "3017620422003",
            name = "Nutella",
            brand = "Ferrero",
            imageUrl = "https://example.com/image.jpg",
            nutriScore = "E",
            novaGroup = 4,
        )
        assertEquals("3017620422003", product.barcode)
        assertEquals("Nutella", product.name)
        assertEquals("Ferrero", product.brand)
        assertEquals("E", product.nutriScore)
        assertEquals(4, product.novaGroup)
    }
}
