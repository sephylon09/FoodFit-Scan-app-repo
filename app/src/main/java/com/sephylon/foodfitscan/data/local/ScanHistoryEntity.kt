package com.sephylon.foodfitscan.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scan_history")
data class ScanHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val barcode: String,
    val productName: String?,
    val brand: String?,
    val imageFrontUrl: String?,
    val scannedAtMillis: Long,
    val lookupStatus: String,
    val shortMessage: String?,
)
