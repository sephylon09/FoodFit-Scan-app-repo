package com.sephylon.foodfitscan.ui.scanner

import androidx.lifecycle.ViewModel

class ScannerViewModel : ViewModel() {

    private var lastScannedBarcode: String? = null
    private var lastScanTimeMs: Long = 0L
    private val cooldownMs = 2_000L

    fun shouldNavigate(barcode: String): Boolean {
        val now = System.currentTimeMillis()
        if (barcode == lastScannedBarcode && (now - lastScanTimeMs) < cooldownMs) {
            return false
        }
        lastScannedBarcode = barcode
        lastScanTimeMs = now
        return true
    }
}
