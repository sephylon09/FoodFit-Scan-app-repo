package com.sephylon.foodfitscan.ui.home

import com.sephylon.foodfitscan.domain.model.NutritionDisplayOption
import com.sephylon.foodfitscan.domain.model.UserFoodPreferences
import com.sephylon.foodfitscan.domain.repository.PreferenceRepository
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
import org.junit.Assert.assertNull
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

    @Test
    fun `showPreferencesCard is true when all preferences are default`() = runTest(testDispatcher) {
        val repo = FakePreferenceRepository(UserFoodPreferences())
        val vm = HomeViewModel(repo)

        val result = vm.showPreferencesCard.first { true }

        assertTrue(result)
    }

    @Test
    fun `showPreferencesCard is false when allergens are set`() = runTest(testDispatcher) {
        val repo = FakePreferenceRepository(
            UserFoodPreferences(allergensToAvoid = setOf("en:milk")),
        )
        val vm = HomeViewModel(repo)

        val result = vm.showPreferencesCard.first { true }

        assertFalse(result)
    }

    @Test
    fun `showPreferencesCard is false when avoidUltraProcessed is true`() = runTest(testDispatcher) {
        val repo = FakePreferenceRepository(
            UserFoodPreferences(avoidUltraProcessed = true),
        )
        val vm = HomeViewModel(repo)

        val result = vm.showPreferencesCard.first { true }

        assertFalse(result)
    }

    @Test
    fun `showPreferencesCard is false when nutrition cap is set`() = runTest(testDispatcher) {
        val repo = FakePreferenceRepository(
            UserFoodPreferences(maxSugarsPer100g = 10.0),
        )
        val vm = HomeViewModel(repo)

        val result = vm.showPreferencesCard.first { true }

        assertFalse(result)
    }

    @Test
    fun `showPreferencesCard updates when preferences change`() = runTest(testDispatcher) {
        val prefsFlow = MutableStateFlow(UserFoodPreferences())
        val repo = FakePreferenceRepository(prefs = prefsFlow)
        val vm = HomeViewModel(repo)

        assertTrue(vm.showPreferencesCard.first { true })

        prefsFlow.value = UserFoodPreferences(allergensToAvoid = setOf("en:milk"))

        assertFalse(vm.showPreferencesCard.first { true })
    }

    // ── Search submit logic ─────────────────────────────────────────────────────

    @Test
    fun `blank search returns Blank and shows validation message`() = runTest(testDispatcher) {
        val vm = HomeViewModel(FakePreferenceRepository())

        vm.onSearchQueryChange("   ")
        val result = vm.onSearchSubmit()

        assertEquals(HomeSearchResult.Blank, result)
        assertEquals(HomeViewModel.BLANK_MESSAGE, vm.searchMessage.value)
    }

    @Test
    fun `empty search returns Blank and shows validation message`() = runTest(testDispatcher) {
        val vm = HomeViewModel(FakePreferenceRepository())

        val result = vm.onSearchSubmit()

        assertEquals(HomeSearchResult.Blank, result)
        assertEquals(HomeViewModel.BLANK_MESSAGE, vm.searchMessage.value)
    }

    @Test
    fun `valid barcode search navigates to product detail`() = runTest(testDispatcher) {
        val vm = HomeViewModel(FakePreferenceRepository())

        vm.onSearchQueryChange("5449000000996")
        val result = vm.onSearchSubmit()

        assertEquals(HomeSearchResult.NavigateToProduct("5449000000996"), result)
        assertNull(vm.searchMessage.value)
    }

    @Test
    fun `valid barcode with surrounding spaces is normalized before navigation`() =
        runTest(testDispatcher) {
            val vm = HomeViewModel(FakePreferenceRepository())

            vm.onSearchQueryChange("  5449000000996  ")
            val result = vm.onSearchSubmit()

            assertEquals(HomeSearchResult.NavigateToProduct("5449000000996"), result)
        }

    @Test
    fun `product name search shows placeholder message`() = runTest(testDispatcher) {
        val vm = HomeViewModel(FakePreferenceRepository())

        vm.onSearchQueryChange("chocolate bar")
        val result = vm.onSearchSubmit()

        assertEquals(HomeSearchResult.ProductNameUnsupported, result)
        assertEquals(HomeViewModel.PRODUCT_NAME_MESSAGE, vm.searchMessage.value)
    }

    @Test
    fun `editing the query clears a previous message`() = runTest(testDispatcher) {
        val vm = HomeViewModel(FakePreferenceRepository())

        vm.onSearchQueryChange("chocolate bar")
        vm.onSearchSubmit()
        assertEquals(HomeViewModel.PRODUCT_NAME_MESSAGE, vm.searchMessage.value)

        vm.onSearchQueryChange("chocolate bars")

        assertNull(vm.searchMessage.value)
    }

    @Test
    fun `query state reflects typed text`() = runTest(testDispatcher) {
        val vm = HomeViewModel(FakePreferenceRepository())

        vm.onSearchQueryChange("nutella")

        assertEquals("nutella", vm.searchQuery.value)
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
