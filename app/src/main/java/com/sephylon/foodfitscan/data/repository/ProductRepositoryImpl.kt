package com.sephylon.foodfitscan.data.repository

import com.sephylon.foodfitscan.core.network.OpenFoodFactsClient
import com.sephylon.foodfitscan.data.local.CachedProductDao
import com.sephylon.foodfitscan.data.local.ScanHistoryDao
import com.sephylon.foodfitscan.data.mapper.ProductDetailsCacheMapper
import com.sephylon.foodfitscan.data.mapper.ScanHistoryMapper
import com.sephylon.foodfitscan.domain.model.AlternativesResult
import com.sephylon.foodfitscan.domain.model.ProductDetails
import com.sephylon.foodfitscan.domain.model.ProductLookupResult
import com.sephylon.foodfitscan.domain.model.ScanHistoryItem
import com.sephylon.foodfitscan.domain.model.UserFoodPreferences
import com.sephylon.foodfitscan.domain.repository.ProductRepository
import com.sephylon.foodfitscan.domain.rules.AlternativeCategorySelector
import com.sephylon.foodfitscan.domain.rules.AlternativeRanker
import com.sephylon.foodfitscan.domain.util.BarcodeValidator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.IOException

internal class ProductRepositoryImpl(
    private val client: OpenFoodFactsClient,
    private val cachedProductDao: CachedProductDao,
    private val scanHistoryDao: ScanHistoryDao,
    private val cacheMapper: ProductDetailsCacheMapper,
    private val historyMapper: ScanHistoryMapper,
) : ProductRepository {

    companion object {
        private const val CACHE_FRESH_MILLIS = 7L * 24 * 60 * 60 * 1000
    }

    override suspend fun getProduct(barcode: String): ProductLookupResult {
        if (!BarcodeValidator.isValidBarcode(barcode)) {
            return ProductLookupResult.NotFound(barcode)
        }
        val normalized = BarcodeValidator.normalizeBarcode(barcode)
        val result = resolveProduct(normalized)
        scanHistoryDao.insertScanHistory(historyMapper.toEntity(normalized, result))
        return result
    }

    private suspend fun resolveProduct(barcode: String): ProductLookupResult {
        val cached = cachedProductDao.getCachedProduct(barcode)
        if (cached != null) {
            val isFresh = System.currentTimeMillis() - cached.cachedAtMillis < CACHE_FRESH_MILLIS
            if (isFresh) {
                val product = cacheMapper.fromEntity(cached)
                if (product != null) return ProductLookupResult.Found(product)
            } else {
                val networkResult = fetchFromNetwork(barcode)
                if (networkResult is ProductLookupResult.Found) return networkResult
                val product = cacheMapper.fromEntity(cached)
                if (product != null) {
                    // TODO Phase 1D: surface stale indicator in UI
                    return ProductLookupResult.Found(product, isFromStaleCache = true)
                }
                return networkResult
            }
        }
        return fetchFromNetwork(barcode)
    }

    private suspend fun fetchFromNetwork(barcode: String): ProductLookupResult {
        val result = try {
            client.getProduct(barcode)
        } catch (e: IOException) {
            ProductLookupResult.NetworkError(e.message ?: "Network error")
        } catch (e: Exception) {
            ProductLookupResult.UnknownError(e.message ?: "Unknown error")
        }
        if (result is ProductLookupResult.Found) {
            cachedProductDao.upsertCachedProduct(cacheMapper.toEntity(result.product))
        }
        return result
    }

    override suspend fun findAlternativesFor(
        product: ProductDetails,
        preferences: UserFoodPreferences,
    ): AlternativesResult {
        val categoryTag = AlternativeCategorySelector.selectCategoryTag(product)
            ?: return AlternativesResult.NoCategory
        return try {
            val candidates = client.searchByCategory(categoryTag, pageSize = 20)
            val ranked = AlternativeRanker.rank(
                candidates = candidates,
                currentBarcode = product.barcode,
                preferences = preferences,
            )
            // TODO Phase 2C: consider caching alternatives per category tag
            if (ranked.isEmpty()) AlternativesResult.Empty
            else AlternativesResult.Success(ranked)
        } catch (e: IOException) {
            AlternativesResult.NetworkError(e.message ?: "Network error")
        } catch (e: Exception) {
            AlternativesResult.UnknownError(e.message ?: "Unknown error")
        }
    }

    override fun observeScanHistory(limit: Int): Flow<List<ScanHistoryItem>> =
        scanHistoryDao.observeRecentScanHistory(limit).map { entities ->
            entities.map { historyMapper.toHistoryItem(it) }
        }

    override suspend fun clearScanHistory() = scanHistoryDao.clearScanHistory()

    override suspend fun deleteScanHistoryItem(id: Long) = scanHistoryDao.deleteScanHistoryItem(id)
}
