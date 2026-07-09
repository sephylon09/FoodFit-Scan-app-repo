package com.sephylon.foodfitscan.data.repository

import com.google.gson.Gson
import com.sephylon.foodfitscan.core.network.OpenFoodFactsClient
import com.sephylon.foodfitscan.data.local.CachedProductEntity
import com.sephylon.foodfitscan.data.local.CachedProductDao
import com.sephylon.foodfitscan.data.local.ScanHistoryDao
import com.sephylon.foodfitscan.data.local.ScanHistoryEntity
import com.sephylon.foodfitscan.data.mapper.ProductDetailsCacheMapper
import com.sephylon.foodfitscan.data.mapper.ScanHistoryMapper
import com.sephylon.foodfitscan.domain.model.AlternativesResult
import com.sephylon.foodfitscan.domain.model.LookupStatus
import com.sephylon.foodfitscan.domain.model.NutritionFacts
import com.sephylon.foodfitscan.domain.model.ProductDetails
import com.sephylon.foodfitscan.domain.model.ProductLookupResult
import com.sephylon.foodfitscan.domain.model.UserFoodPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class ProductRepositoryImplTest {

    // --- basic result passthrough ---

    @Test
    fun `getProduct returns Found when client returns Found`() = runTest {
        val product = sampleProduct("5449000000996")
        val (repo, _, historyDao) = buildRepo(FakeClient(ProductLookupResult.Found(product)))

        val result = repo.getProduct("5449000000996")

        assertTrue(result is ProductLookupResult.Found)
        assertEquals(product, (result as ProductLookupResult.Found).product)
        assertEquals(LookupStatus.FOUND.name, historyDao.history.first().lookupStatus)
    }

    @Test
    fun `getProduct returns NotFound when client returns NotFound`() = runTest {
        val (repo, _, historyDao) = buildRepo(FakeClient(ProductLookupResult.NotFound("5449000000996")))

        val result = repo.getProduct("5449000000996")

        assertTrue(result is ProductLookupResult.NotFound)
        assertEquals(LookupStatus.NOT_FOUND.name, historyDao.history.first().lookupStatus)
    }

    @Test
    fun `getProduct returns NotFound for invalid barcode and does not call client`() = runTest {
        val client = FakeClient(ProductLookupResult.Found(sampleProduct("99999")))
        val (repo, _, historyDao) = buildRepo(client)

        val result = repo.getProduct("NOT_A_BARCODE")

        assertTrue(result is ProductLookupResult.NotFound)
        assertFalse(client.wasCalled)
        assertTrue(historyDao.history.isEmpty())
    }

    @Test
    fun `getProduct returns NetworkError when client returns NetworkError`() = runTest {
        val (repo, _, historyDao) = buildRepo(FakeClient(ProductLookupResult.NetworkError("Timeout")))

        val result = repo.getProduct("5449000000996")

        assertTrue(result is ProductLookupResult.NetworkError)
        assertEquals(LookupStatus.NETWORK_ERROR.name, historyDao.history.first().lookupStatus)
    }

    @Test
    fun `getProduct returns NetworkError when client throws IOException`() = runTest {
        val (repo, _, historyDao) = buildRepo(ThrowingClient(IOException("Connection refused")))

        val result = repo.getProduct("5449000000996")

        assertTrue(result is ProductLookupResult.NetworkError)
        assertEquals(LookupStatus.NETWORK_ERROR.name, historyDao.history.first().lookupStatus)
    }

    @Test
    fun `getProduct returns UnknownError when client throws unexpected exception`() = runTest {
        val (repo, _, historyDao) = buildRepo(ThrowingClient(RuntimeException("Unexpected")))

        val result = repo.getProduct("5449000000996")

        assertTrue(result is ProductLookupResult.UnknownError)
        assertEquals(LookupStatus.UNKNOWN_ERROR.name, historyDao.history.first().lookupStatus)
    }

    @Test
    fun `getProduct normalizes barcode before passing to client`() = runTest {
        val product = sampleProduct("5449000000996")
        val client = FakeClient(ProductLookupResult.Found(product))
        val (repo) = buildRepo(client)

        repo.getProduct(" 5449000000996 ")

        assertEquals("5449000000996", client.lastBarcode)
    }

    // --- cache behaviour ---

    @Test
    fun `getProduct returns cached product without calling API when cache is fresh`() = runTest {
        val product = sampleProduct("5449000000996")
        val client = FakeClient(ProductLookupResult.NotFound("5449000000996"))
        val (repo, cacheDao) = buildRepo(client)
        val gson = Gson()
        cacheDao.cache["5449000000996"] = CachedProductEntity(
            barcode = "5449000000996",
            productName = product.name,
            brand = product.brand,
            quantity = product.quantity,
            imageFrontUrl = product.imageFrontUrl,
            nutriScore = product.nutriScore,
            novaGroup = product.novaGroup,
            cachedAtMillis = System.currentTimeMillis(),
            serializedProductJson = gson.toJson(product),
        )

        val result = repo.getProduct("5449000000996")

        assertTrue(result is ProductLookupResult.Found)
        assertFalse("Should not call API on fresh cache", client.wasCalled)
    }

    @Test
    fun `getProduct saves found product to cache`() = runTest {
        val product = sampleProduct("5449000000996")
        val (repo, cacheDao) = buildRepo(FakeClient(ProductLookupResult.Found(product)))

        repo.getProduct("5449000000996")

        assertTrue(cacheDao.cache.containsKey("5449000000996"))
    }

    @Test
    fun `getProduct fetches from network when cache is missing`() = runTest {
        val product = sampleProduct("5449000000996")
        val client = FakeClient(ProductLookupResult.Found(product))
        val (repo) = buildRepo(client)

        repo.getProduct("5449000000996")

        assertTrue(client.wasCalled)
    }

    @Test
    fun `getProduct inserts FOUND history entry`() = runTest {
        val product = sampleProduct("5449000000996")
        val (repo, _, historyDao) = buildRepo(FakeClient(ProductLookupResult.Found(product)))

        repo.getProduct("5449000000996")

        assertEquals(1, historyDao.history.size)
        assertEquals(LookupStatus.FOUND.name, historyDao.history[0].lookupStatus)
    }

    @Test
    fun `getProduct inserts NOT_FOUND history entry`() = runTest {
        val (repo, _, historyDao) = buildRepo(FakeClient(ProductLookupResult.NotFound("5449000000996")))

        repo.getProduct("5449000000996")

        assertEquals(1, historyDao.history.size)
        assertEquals(LookupStatus.NOT_FOUND.name, historyDao.history[0].lookupStatus)
    }

    // --- findAlternativesFor ---

    @Test
    fun `findAlternativesFor returns NoCategory when product has no category tags`() = runTest {
        val product = sampleProduct("5449000000996") // categoriesTags = null
        val (repo) = buildRepo(FakeClient(ProductLookupResult.NotFound("x")))

        val result = repo.findAlternativesFor(product, UserFoodPreferences())

        assertTrue(result is AlternativesResult.NoCategory)
    }

    @Test
    fun `findAlternativesFor returns NetworkError when client throws IOException`() = runTest {
        val product = sampleProduct("5449000000996").copy(
            categoriesTags = listOf("en:biscuits"),
        )
        val client = FakeClient(
            ProductLookupResult.NotFound("x"),
            searchException = IOException("No network"),
        )
        val (repo) = buildRepo(client)

        val result = repo.findAlternativesFor(product, UserFoodPreferences())

        assertTrue(result is AlternativesResult.NetworkError)
    }

    @Test
    fun `findAlternativesFor returns Empty when search returns no candidates`() = runTest {
        val product = sampleProduct("5449000000996").copy(
            categoriesTags = listOf("en:biscuits"),
        )
        val client = FakeClient(ProductLookupResult.NotFound("x"), searchProducts = emptyList())
        val (repo) = buildRepo(client)

        val result = repo.findAlternativesFor(product, UserFoodPreferences())

        assertTrue(result is AlternativesResult.Empty)
    }

    @Test
    fun `findAlternativesFor returns similar-category candidates ranked healthier first`() = runTest {
        val product = sampleProduct("CURRENT").copy(
            categoriesTags = listOf("en:snacks", "en:bars", "en:protein-bars"),
        )
        val sameTypeGradeC = sampleProduct("ALT_C").copy(
            name = "Protein Bar C",
            nutriScore = "c",
            categoriesTags = listOf("en:snacks", "en:bars", "en:protein-bars"),
        )
        val sameTypeGradeA = sampleProduct("ALT_A").copy(
            name = "Protein Bar A",
            nutriScore = "a",
            categoriesTags = listOf("en:snacks", "en:bars", "en:protein-bars"),
        )
        val unrelatedWater = sampleProduct("WATER").copy(
            name = "Spring Water",
            nutriScore = "a",
            categoriesTags = listOf("en:beverages", "en:waters"),
        )
        val client = FakeClient(
            ProductLookupResult.NotFound("x"),
            searchProducts = listOf(sameTypeGradeC, unrelatedWater, sameTypeGradeA),
        )
        val (repo) = buildRepo(client)

        val result = repo.findAlternativesFor(product, UserFoodPreferences())

        assertTrue(result is AlternativesResult.Success)
        val barcodes = (result as AlternativesResult.Success).alternatives.map { it.barcode }
        assertEquals(listOf("ALT_A", "ALT_C"), barcodes)
        // Enough similar candidates from the most specific tag -> no fallback query.
        assertEquals(listOf("en:protein-bars"), client.searchedTags)
    }

    @Test
    fun `findAlternativesFor queries broader tag when specific tag yields too few`() = runTest {
        val product = sampleProduct("CURRENT").copy(
            categoriesTags = listOf("en:snacks", "en:bars", "en:protein-bars"),
        )
        // Candidates only loosely related: excluded by the overlap rule, forcing the fallback.
        val loose = sampleProduct("LOOSE").copy(
            name = "Generic Snack",
            categoriesTags = listOf("en:snacks"),
        )
        val client = FakeClient(
            ProductLookupResult.NotFound("x"),
            searchProducts = listOf(loose),
        )
        val (repo) = buildRepo(client)

        val result = repo.findAlternativesFor(product, UserFoodPreferences())

        assertTrue(result is AlternativesResult.Empty)
        assertEquals(listOf("en:protein-bars", "en:bars"), client.searchedTags)
    }

    @Test
    fun `getProduct falls back to stale cache when network fails`() = runTest {
        val product = sampleProduct("5449000000996")
        val client = FakeClient(ProductLookupResult.NetworkError("offline"))
        val (repo, cacheDao) = buildRepo(client)
        val gson = Gson()
        cacheDao.cache["5449000000996"] = CachedProductEntity(
            barcode = "5449000000996",
            productName = product.name,
            brand = product.brand,
            quantity = product.quantity,
            imageFrontUrl = product.imageFrontUrl,
            nutriScore = product.nutriScore,
            novaGroup = product.novaGroup,
            cachedAtMillis = System.currentTimeMillis() - (8L * 24 * 60 * 60 * 1000), // 8 days old
            serializedProductJson = gson.toJson(product),
        )

        val result = repo.getProduct("5449000000996")

        assertTrue(result is ProductLookupResult.Found)
        assertTrue((result as ProductLookupResult.Found).isFromStaleCache)
    }

    // --- helpers ---

    private data class RepoDeps(
        val repo: ProductRepositoryImpl,
        val cacheDao: FakeCachedProductDao,
        val historyDao: FakeScanHistoryDao,
    )

    private fun buildRepo(client: OpenFoodFactsClient): RepoDeps {
        val cacheDao = FakeCachedProductDao()
        val historyDao = FakeScanHistoryDao()
        val gson = Gson()
        val repo = ProductRepositoryImpl(
            client = client,
            cachedProductDao = cacheDao,
            scanHistoryDao = historyDao,
            cacheMapper = ProductDetailsCacheMapper(gson),
            historyMapper = ScanHistoryMapper(),
        )
        return RepoDeps(repo, cacheDao, historyDao)
    }

    private fun sampleProduct(barcode: String) = ProductDetails(
        barcode = barcode,
        name = "Test Product",
        brand = "Test Brand",
        quantity = "100g",
        imageFrontUrl = null,
        nutriScore = "b",
        novaGroup = 2,
        nutrition = NutritionFacts(energyKcalPer100g = 100.0),
        ingredientsText = "Water",
        allergensTags = emptyList(),
        tracesTags = emptyList(),
        additivesTags = emptyList(),
    )
}

// --- Fake implementations ---

private class FakeClient(
    private val result: ProductLookupResult,
    private val searchProducts: List<ProductDetails> = emptyList(),
    private val searchException: Exception? = null,
) : OpenFoodFactsClient {
    var wasCalled = false
    var lastBarcode: String? = null
    val searchedTags = mutableListOf<String>()

    override suspend fun getProduct(barcode: String): ProductLookupResult {
        wasCalled = true
        lastBarcode = barcode
        return result
    }

    override suspend fun searchByCategory(categoryTag: String, pageSize: Int): List<ProductDetails> {
        searchedTags.add(categoryTag)
        if (searchException != null) throw searchException
        return searchProducts
    }
}

private class ThrowingClient(private val exception: Exception) : OpenFoodFactsClient {
    override suspend fun getProduct(barcode: String): ProductLookupResult = throw exception
    override suspend fun searchByCategory(categoryTag: String, pageSize: Int): List<ProductDetails> = throw exception
}

class FakeCachedProductDao : CachedProductDao {
    val cache = mutableMapOf<String, CachedProductEntity>()

    override suspend fun getCachedProduct(barcode: String): CachedProductEntity? = cache[barcode]
    override suspend fun upsertCachedProduct(entity: CachedProductEntity) { cache[entity.barcode] = entity }
    override suspend fun deleteCachedProduct(barcode: String) { cache.remove(barcode) }
    override suspend fun clearCache() { cache.clear() }
    override suspend fun getCacheCount(): Int = cache.size
}

class FakeScanHistoryDao : ScanHistoryDao {
    val history = mutableListOf<ScanHistoryEntity>()
    private val _flow = MutableStateFlow<List<ScanHistoryEntity>>(emptyList())

    override suspend fun insertScanHistory(entity: ScanHistoryEntity) {
        history.add(entity)
        _flow.value = history.toList()
    }

    override suspend fun getRecentScanHistory(limit: Int): List<ScanHistoryEntity> =
        history.sortedByDescending { it.scannedAtMillis }.take(limit)

    override fun observeRecentScanHistory(limit: Int): Flow<List<ScanHistoryEntity>> =
        _flow.asStateFlow()

    override suspend fun clearScanHistory() {
        history.clear()
        _flow.value = emptyList()
    }

    override suspend fun deleteScanHistoryItem(id: Long) {
        history.removeAll { it.id == id }
        _flow.value = history.toList()
    }
}
