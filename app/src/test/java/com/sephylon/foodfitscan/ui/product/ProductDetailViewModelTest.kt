package com.sephylon.foodfitscan.ui.product

import com.sephylon.foodfitscan.domain.model.AlternativesResult
import com.sephylon.foodfitscan.domain.model.NutritionDisplayOption
import com.sephylon.foodfitscan.domain.model.NutritionFacts
import com.sephylon.foodfitscan.domain.model.ProductDetails
import com.sephylon.foodfitscan.domain.model.ProductLookupResult
import com.sephylon.foodfitscan.domain.model.ScanHistoryItem
import com.sephylon.foodfitscan.domain.model.SearchCountry
import com.sephylon.foodfitscan.domain.model.UserFoodPreferences
import com.sephylon.foodfitscan.domain.repository.PreferenceRepository
import com.sephylon.foodfitscan.domain.repository.ProductRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProductDetailViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `alternativesState starts as Idle`() = runTest(testDispatcher) {
        val vm = ProductDetailViewModel(
            barcode = "5449000000996",
            productRepository = FakeProductRepository(),
            preferenceRepository = FakePreferenceRepo(),
        )
        assertEquals(AlternativesResult.Idle, vm.alternativesState.value)
    }

    @Test
    fun `alternativesState stays Idle without calling loadAlternatives after product loads`() =
        runTest(testDispatcher) {
            val product = sampleProduct()
            val vm = ProductDetailViewModel(
                barcode = product.barcode,
                productRepository = FakeProductRepository(lookupResult = ProductLookupResult.Found(product)),
                preferenceRepository = FakePreferenceRepo(),
            )
            advanceUntilIdle()

            assertTrue(vm.uiState.value is ProductDetailUiState.ProductFound)
            assertEquals(AlternativesResult.Idle, vm.alternativesState.value)
        }

    @Test
    fun `loadAlternatives transitions to result after product is loaded`() =
        runTest(testDispatcher) {
            val product = sampleProduct()
            val vm = ProductDetailViewModel(
                barcode = product.barcode,
                productRepository = FakeProductRepository(
                    lookupResult = ProductLookupResult.Found(product),
                    alternativesResult = AlternativesResult.Empty,
                ),
                preferenceRepository = FakePreferenceRepo(),
            )
            advanceUntilIdle()

            vm.loadAlternatives()
            advanceUntilIdle()

            assertEquals(AlternativesResult.Empty, vm.alternativesState.value)
        }

    @Test
    fun `loadAlternatives does nothing when product is not found`() = runTest(testDispatcher) {
        val vm = ProductDetailViewModel(
            barcode = "invalid",
            productRepository = FakeProductRepository(lookupResult = ProductLookupResult.NotFound("invalid")),
            preferenceRepository = FakePreferenceRepo(),
        )
        advanceUntilIdle()

        vm.loadAlternatives()
        advanceUntilIdle()

        assertEquals(AlternativesResult.Idle, vm.alternativesState.value)
    }

    @Test
    fun `onProductViewed fires exactly once when a product loads successfully`() =
        runTest(testDispatcher) {
            val product = sampleProduct()
            var viewedCount = 0
            val vm = ProductDetailViewModel(
                barcode = product.barcode,
                productRepository = FakeProductRepository(lookupResult = ProductLookupResult.Found(product)),
                preferenceRepository = FakePreferenceRepo(),
                onProductViewed = { viewedCount++ },
            )
            advanceUntilIdle()
            assertEquals(1, viewedCount)

            // A retry on the same screen must not count as a second product view.
            vm.retry()
            advanceUntilIdle()
            assertEquals(1, viewedCount)
        }

    @Test
    fun `onProductViewed does not fire when the lookup fails`() = runTest(testDispatcher) {
        var viewedCount = 0
        ProductDetailViewModel(
            barcode = "invalid",
            productRepository = FakeProductRepository(lookupResult = ProductLookupResult.NotFound("invalid")),
            preferenceRepository = FakePreferenceRepo(),
            onProductViewed = { viewedCount++ },
        )
        advanceUntilIdle()
        assertEquals(0, viewedCount)
    }

    @Test
    fun `loadAlternatives returns Success with alternatives`() = runTest(testDispatcher) {
        val product = sampleProduct()
        val successResult = AlternativesResult.Success(emptyList())
        val vm = ProductDetailViewModel(
            barcode = product.barcode,
            productRepository = FakeProductRepository(
                lookupResult = ProductLookupResult.Found(product),
                alternativesResult = successResult,
            ),
            preferenceRepository = FakePreferenceRepo(),
        )
        advanceUntilIdle()

        vm.loadAlternatives()
        advanceUntilIdle()

        assertTrue(vm.alternativesState.value is AlternativesResult.Success)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun sampleProduct() = ProductDetails(
        barcode = "5449000000996",
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
        categoriesTags = listOf("en:biscuits"),
    )
}

// ── Fakes ─────────────────────────────────────────────────────────────────────

private class FakeProductRepository(
    private val lookupResult: ProductLookupResult = ProductLookupResult.NotFound("test"),
    private val alternativesResult: AlternativesResult = AlternativesResult.Idle,
) : ProductRepository {
    override suspend fun getProduct(barcode: String): ProductLookupResult = lookupResult
    override suspend fun findAlternativesFor(
        product: ProductDetails,
        preferences: UserFoodPreferences,
    ): AlternativesResult = alternativesResult
    override fun observeScanHistory(limit: Int): Flow<List<ScanHistoryItem>> = MutableStateFlow(emptyList())
    override suspend fun clearScanHistory() {}
    override suspend fun deleteScanHistoryItem(id: Long) {}
}

private class FakePreferenceRepo(
    initial: UserFoodPreferences = UserFoodPreferences(),
) : PreferenceRepository {
    private val _flow = MutableStateFlow(initial)
    private val _nutritionFields = MutableStateFlow(NutritionDisplayOption.DEFAULT_KEYS)
    private val _searchCountry = MutableStateFlow<SearchCountry?>(null)
    override fun getUserPreferences(): Flow<UserFoodPreferences> = _flow
    override suspend fun saveUserPreferences(preferences: UserFoodPreferences) {
        _flow.value = preferences
    }
    override fun observeOnboardingCompleted(): Flow<Boolean> = MutableStateFlow(false)
    override suspend fun setOnboardingCompleted(completed: Boolean) {}
    override fun observeSelectedNutritionFields(): Flow<Set<String>> = _nutritionFields
    override suspend fun saveSelectedNutritionFields(fields: Set<String>) {
        _nutritionFields.value = fields.ifEmpty { NutritionDisplayOption.DEFAULT_KEYS }
    }
    override fun observeSearchCountry(): Flow<SearchCountry?> = _searchCountry
    override suspend fun saveSearchCountry(country: SearchCountry) {
        _searchCountry.value = country
    }
}
