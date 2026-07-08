package com.sephylon.foodfitscan.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface CachedProductDao {
    @Query("SELECT * FROM cached_products WHERE barcode = :barcode")
    suspend fun getCachedProduct(barcode: String): CachedProductEntity?

    @Upsert
    suspend fun upsertCachedProduct(entity: CachedProductEntity)

    @Query("DELETE FROM cached_products WHERE barcode = :barcode")
    suspend fun deleteCachedProduct(barcode: String)

    @Query("DELETE FROM cached_products")
    suspend fun clearCache()

    @Query("SELECT COUNT(*) FROM cached_products")
    suspend fun getCacheCount(): Int
}
