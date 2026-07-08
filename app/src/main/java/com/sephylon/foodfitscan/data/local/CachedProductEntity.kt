package com.sephylon.foodfitscan.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cached_products")
data class CachedProductEntity(
    @PrimaryKey val barcode: String,
    val productName: String?,
    val brand: String?,
    val quantity: String?,
    val imageFrontUrl: String?,
    val nutriScore: String?,
    val novaGroup: Int?,
    val cachedAtMillis: Long,
    val serializedProductJson: String,
)
