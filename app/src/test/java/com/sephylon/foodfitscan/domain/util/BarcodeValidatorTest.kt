package com.sephylon.foodfitscan.domain.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BarcodeValidatorTest {

    @Test
    fun `accepts valid 8-digit barcode`() {
        assertTrue(BarcodeValidator.isValidBarcode("01234565"))
    }

    @Test
    fun `accepts valid 12-digit barcode`() {
        assertTrue(BarcodeValidator.isValidBarcode("012345678905"))
    }

    @Test
    fun `accepts valid 13-digit barcode`() {
        assertTrue(BarcodeValidator.isValidBarcode("5449000000996"))
    }

    @Test
    fun `trims leading and trailing spaces`() {
        assertTrue(BarcodeValidator.isValidBarcode("  5449000000996  "))
    }

    @Test
    fun `rejects value containing letters`() {
        assertFalse(BarcodeValidator.isValidBarcode("544900000099A"))
    }

    @Test
    fun `rejects blank value`() {
        assertFalse(BarcodeValidator.isValidBarcode(""))
        assertFalse(BarcodeValidator.isValidBarcode("   "))
    }

    @Test
    fun `rejects unsupported length`() {
        assertFalse(BarcodeValidator.isValidBarcode("123"))
        assertFalse(BarcodeValidator.isValidBarcode("1234567890"))
        assertFalse(BarcodeValidator.isValidBarcode("12345678901234"))
    }

    @Test
    fun `normalizes barcode with internal spaces`() {
        val result = BarcodeValidator.normalizeBarcode("5449 0000 00996")
        assertEquals("5449000000996", result)
    }

    @Test
    fun `normalized spaced barcode passes validation`() {
        assertTrue(BarcodeValidator.isValidBarcode("5449 0000 00996"))
    }
}
