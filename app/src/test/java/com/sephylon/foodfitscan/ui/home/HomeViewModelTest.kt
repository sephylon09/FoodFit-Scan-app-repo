package com.sephylon.foodfitscan.ui.home

import com.sephylon.foodfitscan.domain.model.NutritionDisplayOption
import com.sephylon.foodfitscan.domain.model.ProductSearchItem
import com.sephylon.foodfitscan.domain.model.ProductSearchResult
import com.sephylon.foodfitscan.domain.model.UserFoodPreferences
import com.sephylon.foodfitscan.domain.repository.PreferenceRepository
import com.sephylon.foodfitscan.domain.repository.ProductSearchRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(
        preferenceRepository: PreferenceRepository = FakePreferenceRepository(),
        searchRepository: ProductSearchRepository = FakeProductSearchRepository(),
    ) = HomeViewModel(preferenceRepository, searchRepository)

    // ── Preferences helper card ─────────────────────────────────────────────────

    @Test
    fun `showPreferencesCard is true when all preferences are default`() = runTest(testDispatcher) {
        val vm = viewModel(FakePreferenceRepository(UserFoodPreferences()))

        assertTrue(vm.showPreferencesCard.first { true })
    }

    @Test
    fun `showPreferencesCard is false when allergens are set`() = runTest(testDispatcher) {
        val vm = viewModel(
            FakePreferenceRepository(UserFoodPreferences(allergensToAvoid = setOf("en:milk"))),
        )

        assertFalse(vm.showPreferencesCard.first { true })
    }

    @Test
    fun `showPreferencesCard updates when preferences change`() = runTest(testDispatcher) {
        val prefsFlow = MutableStateFlow(UserFoodPreferences())
        val vm = viewModel(FakePreferenceRepository(prefs = prefsFlow))

        assertTrue(vm.showPreferencesCard.first { true })

        prefsFlow.value = UserFoodPreferences(allergensToAvoid = setOf("en:milk"))

        assertFalse(vm.showPreferencesCard.first { true })
    }

    // ── Search submit logic ─────────────────────────────────────────────────────

    @Test
    fun `blank search returns Handled and shows blank validation message`() = runTest(testDispatcher) {
        val search = FakeProductSearchRepository()
        val vm = viewModel(searchRepository = search)

        vm.onSearchQueryChange("   ")
        val action = vm.onSearchSubmit()

        assertEquals(HomeSearchAction.Handled, action)
        assertEquals(
            ProductSearchResult.ValidationError(HomeViewModel.BLANK_MESSAGE),
            vm.searchState.value,
        )
        assertEquals(0, search.callCount)
    }

    @Test
    fun `empty search shows blank validation message`() = runTest(testDispatcher) {
        val vm = viewModel()

        val action = vm.onSearchSubmit()

        assertEquals(HomeSearchAction.Handled, action)
        assertEquals(
            ProductSearchResult.ValidationError(HomeViewModel.BLANK_MESSAGE),
            vm.searchState.value,
        )
    }

    @Test
    fun `two character query shows minimum length message and skips firebase`() =
        runTest(testDispatcher) {
            val search = FakeProductSearchRepository()
            val vm = viewModel(searchRepository = search)

            vm.onSearchQueryChange("ab")
            val action = vm.onSearchSubmit()

            assertEquals(HomeSearchAction.Handled, action)
            assertEquals(
                ProductSearchResult.ValidationError(HomeViewModel.MIN_LENGTH_MESSAGE),
                vm.searchState.value,
            )
            assertEquals(0, search.callCount)
        }

    @Test
    fun `valid barcode navigates to product detail without querying firebase`() =
        runTest(testDispatcher) {
            val search = FakeProductSearchRepository()
            val vm = viewModel(searchRepository = search)

            vm.onSearchQueryChange("5449000000996")
            val action = vm.onSearchSubmit()

            assertEquals(HomeSearchAction.NavigateToProduct("5449000000996"), action)
            assertEquals(ProductSearchResult.Idle, vm.searchState.value)
            assertEquals(0, search.callCount)
        }

    @Test
    fun `valid barcode with surrounding spaces is normalized before navigation`() =
        runTest(testDispatcher) {
            val vm = viewModel()

            vm.onSearchQueryChange("  5449000000996  ")
            val action = vm.onSearchSubmit()

            assertEquals(HomeSearchAction.NavigateToProduct("5449000000996"), action)
        }

    @Test
    fun `product name search calls repository and publishes results`() = runTest(testDispatcher) {
        val items = listOf(ProductSearchItem(barcode = "1", name = "Nutella"))
        val search = FakeProductSearchRepository(result = ProductSearchResult.Success(items))
        val vm = viewModel(searchRepository = search)

        vm.onSearchQueryChange("nutella")
        val action = vm.onSearchSubmit()

        assertEquals(HomeSearchAction.Handled, action)
        assertEquals(1, search.callCount)
        assertEquals("nutella", search.queries.last())
        assertEquals(ProductSearchResult.Success(items), vm.searchState.value)
    }

    @Test
    fun `product name search does not run while typing`() = runTest(testDispatcher) {
        val search = FakeProductSearchRepository()
        val vm = viewModel(searchRepository = search)

        vm.onSearchQueryChange("n")
        vm.onSearchQueryChange("nu")
        vm.onSearchQueryChange("nut")
        vm.onSearchQueryChange("nutella")

        assertEquals(0, search.callCount)
        assertEquals(ProductSearchResult.Idle, vm.searchState.value)
    }

    @Test
    fun `editing the query clears a previous message`() = runTest(testDispatcher) {
        val vm = viewModel()

        vm.onSearchQueryChange("ab")
        vm.onSearchSubmit()
        assertTrue(vm.searchState.value is ProductSearchResult.ValidationError)

        vm.onSearchQueryChange("abc")

        assertEquals(ProductSearchResult.Idle, vm.searchState.value)
    }

    @Test
    fun `retry re-runs the last product name query`() = runTest(testDispatcher) {
        val search = FakeProductSearchRepository(result = ProductSearchResult.NetworkError("offline"))
        val vm = viewModel(searchRepository = search)

        vm.onSearchQueryChange("nutella")
        vm.onSearchSubmit()
        assertEquals(1, search.callCount)

        vm.retryLastSearch()

        assertEquals(2, search.callCount)
        assertEquals(listOf("nutella", "nutella"), search.queries)
    }

    @Test
    fun `retry does nothing when no search has run`() = runTest(testDispatcher) {
        val search = FakeProductSearchRepository()
        val vm = viewModel(searchRepository = search)

        vm.retryLastSearch()

        assertEquals(0, search.callCount)
    }

    @Test
    fun `query state reflects typed text`() = runTest(testDispatcher) {
        val vm = viewModel()

        vm.onSearchQueryChange("nutella")

        assertEquals("nutella", vm.searchQuery.value)
    }
}

private class FakeProductSearchRepository(
    private val result: ProductSearchResult = ProductSearchResult.Empty,
) : ProductSearchRepository {
    var callCount = 0
    val queries = mutableListOf<String>()

    override suspend fun searchByName(rawQuery: String): ProductSearchResult {
        callCount++
        queries.add(rawQuery)
        return result
    }
}

private class FakePreferenceRepository(
    initial: UserFoodPreferences = UserFoodPreferences(),
    private val prefs: MutableStateFlow<UserFoodPreferences> = MutableStateFlow(initial),
) : PreferenceRepository {
    private val _nutritionFields = MutableStateFlow(NutritionDisplayOption.DEFAULT_KEYS)
    override fun getUserPreferences(): Flow<UserFoodPreferences> = prefs
    override suspend fun saveUserPreferences(preferences: UserFoodPreferences) {
        prefs.value = preferences
    }
    override fun observeOnboardingCompleted(): Flow<Boolean> = MutableStateFlow(false)
    override suspend fun setOnboardingCompleted(completed: Boolean) {}
    override fun observeSelectedNutritionFields(): Flow<Set<String>> = _nutritionFields
    override suspend fun saveSelectedNutritionFields(fields: Set<String>) {
        _nutritionFields.value = fields.ifEmpty { NutritionDisplayOption.DEFAULT_KEYS }
    }
}
