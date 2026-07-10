package com.sephylon.foodfitscan.ui.home

import com.sephylon.foodfitscan.domain.model.NutritionDisplayOption
import com.sephylon.foodfitscan.domain.model.ProductSearchItem
import com.sephylon.foodfitscan.domain.model.ProductSearchResult
import com.sephylon.foodfitscan.domain.model.SearchCountry
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
        deviceRegionCode: String? = null,
    ) = HomeViewModel(preferenceRepository, searchRepository, deviceRegionCode)

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
    fun `product name search passes the selected country to the repository`() =
        runTest(testDispatcher) {
            val search = FakeProductSearchRepository()
            val vm = viewModel(searchRepository = search, deviceRegionCode = "MY")

            vm.onSearchQueryChange("nutella")
            vm.onSearchSubmit()

            assertEquals(SearchCountry.MALAYSIA, search.countries.last())
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

    @Test
    fun `editing the query stops a later retry from re-running the stale search`() =
        runTest(testDispatcher) {
            val search = FakeProductSearchRepository(result = ProductSearchResult.NetworkError("offline"))
            val vm = viewModel(searchRepository = search)

            vm.onSearchQueryChange("nutella")
            vm.onSearchSubmit()
            vm.onSearchQueryChange("kitkat")
            vm.retryLastSearch()

            assertEquals(1, search.callCount)
        }

    // ── Country filter ──────────────────────────────────────────────────────────

    @Test
    fun `device region SG defaults the filter to Singapore`() = runTest(testDispatcher) {
        val vm = viewModel(deviceRegionCode = "SG")

        assertEquals(SearchCountry.SINGAPORE, vm.selectedCountry.value)
    }

    @Test
    fun `unsupported device region defaults the filter to All`() = runTest(testDispatcher) {
        assertEquals(SearchCountry.ALL, viewModel(deviceRegionCode = "BR").selectedCountry.value)
        assertEquals(SearchCountry.ALL, viewModel(deviceRegionCode = null).selectedCountry.value)
    }

    @Test
    fun `a persisted country overrides the device region default`() = runTest(testDispatcher) {
        val prefs = FakePreferenceRepository(searchCountry = SearchCountry.JAPAN)

        val vm = viewModel(preferenceRepository = prefs, deviceRegionCode = "SG")

        assertEquals(SearchCountry.JAPAN, vm.selectedCountry.value)
    }

    @Test
    fun `selecting a country persists it`() = runTest(testDispatcher) {
        val prefs = FakePreferenceRepository()
        val vm = viewModel(preferenceRepository = prefs)

        vm.onCountrySelected(SearchCountry.THAILAND)

        assertEquals(SearchCountry.THAILAND, vm.selectedCountry.value)
        assertEquals(SearchCountry.THAILAND, prefs.observeSearchCountry().first())
    }

    @Test
    fun `selecting a country re-runs the displayed search with the new filter`() =
        runTest(testDispatcher) {
            val search = FakeProductSearchRepository()
            val vm = viewModel(searchRepository = search, deviceRegionCode = "SG")

            vm.onSearchQueryChange("nutella")
            vm.onSearchSubmit()
            vm.onCountrySelected(SearchCountry.ALL)

            assertEquals(2, search.callCount)
            assertEquals(listOf("nutella", "nutella"), search.queries)
            assertEquals(listOf(SearchCountry.SINGAPORE, SearchCountry.ALL), search.countries)
        }

    @Test
    fun `selecting a country does not search when no results are showing`() =
        runTest(testDispatcher) {
            val search = FakeProductSearchRepository()
            val vm = viewModel(searchRepository = search)

            vm.onCountrySelected(SearchCountry.INDIA)

            assertEquals(0, search.callCount)
        }

    @Test
    fun `re-selecting the current country is a no-op`() = runTest(testDispatcher) {
        val search = FakeProductSearchRepository()
        val vm = viewModel(searchRepository = search, deviceRegionCode = "SG")

        vm.onSearchQueryChange("nutella")
        vm.onSearchSubmit()
        vm.onCountrySelected(SearchCountry.SINGAPORE)

        assertEquals(1, search.callCount)
    }

    @Test
    fun `barcode search is unaffected by the country filter`() = runTest(testDispatcher) {
        val search = FakeProductSearchRepository()
        val vm = viewModel(searchRepository = search, deviceRegionCode = "SG")

        vm.onSearchQueryChange("5449000000996")
        val action = vm.onSearchSubmit()

        assertEquals(HomeSearchAction.NavigateToProduct("5449000000996"), action)
        assertEquals(0, search.callCount)

        // Changing the country afterwards must not turn the barcode into a name search.
        vm.onCountrySelected(SearchCountry.ALL)
        assertEquals(0, search.callCount)
    }
}

private class FakeProductSearchRepository(
    private val result: ProductSearchResult = ProductSearchResult.Empty,
) : ProductSearchRepository {
    var callCount = 0
    val queries = mutableListOf<String>()
    val countries = mutableListOf<SearchCountry>()

    override suspend fun searchByName(rawQuery: String, country: SearchCountry): ProductSearchResult {
        callCount++
        queries.add(rawQuery)
        countries.add(country)
        return result
    }
}

private class FakePreferenceRepository(
    initial: UserFoodPreferences = UserFoodPreferences(),
    searchCountry: SearchCountry? = null,
    private val prefs: MutableStateFlow<UserFoodPreferences> = MutableStateFlow(initial),
) : PreferenceRepository {
    private val _nutritionFields = MutableStateFlow(NutritionDisplayOption.DEFAULT_KEYS)
    private val _searchCountry = MutableStateFlow(searchCountry)
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
    override fun observeSearchCountry(): Flow<SearchCountry?> = _searchCountry
    override suspend fun saveSearchCountry(country: SearchCountry) {
        _searchCountry.value = country
    }
}
