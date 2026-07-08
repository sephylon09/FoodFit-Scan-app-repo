package com.sephylon.foodfitscan.domain.util

object BarcodeValidator {

    private val validLengths = setOf(8, 12, 13)

    fun isValidBarcode(value: String): Boolean {
        val normalized = normalizeBarcode(value)
        return normalized.isNotEmpty() &&
            normalized.all { it.isDigit() } &&
            normalized.length in validLengths
    }

    fun normalizeBarcode(value: String): String = value.trim().replace(" ", "")
}
