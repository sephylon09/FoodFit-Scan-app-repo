package com.sephylon.foodfitscan.domain.model

data class ScanHistoryItem(
    val id: Long,
    val barcode: String,
    val productName: String?,
    val brand: String?,
    val imageFrontUrl: String?,
    val scannedAtMillis: Long,
    val lookupStatus: LookupStatus,
    val shortMessage: String?,
)
