package com.sephylon.foodfitscan.domain.repository

import com.sephylon.foodfitscan.domain.model.AlternativesResult
import com.sephylon.foodfitscan.domain.model.ProductDetails
import com.sephylon.foodfitscan.domain.model.ProductLookupResult
import com.sephylon.foodfitscan.domain.model.ScanHistoryItem
import com.sephylon.foodfitscan.domain.model.UserFoodPreferences
import kotlinx.coroutines.flow.Flow

interface ProductRepository {
    suspend fun getProduct(barcode: String): ProductLookupResult
    suspend fun findAlternativesFor(product: ProductDetails, preferences: UserFoodPreferences): AlternativesResult
    fun observeScanHistory(limit: Int): Flow<List<ScanHistoryItem>>
    suspend fun clearScanHistory()
    suspend fun deleteScanHistoryItem(id: Long)
}
