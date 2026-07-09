package com.sephylon.foodfitscan.ui.onboarding

import com.sephylon.foodfitscan.domain.model.NutritionDisplayOption
import com.sephylon.foodfitscan.domain.model.UserFoodPreferences
import com.sephylon.foodfitscan.domain.repository.PreferenceRepository
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {

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
    fun `initial page is 0`() = runTest(testDispatcher) {
        val vm = OnboardingViewModel(FakePreferenceRepository())
        assertEquals(0, vm.uiState.value.currentPage)
    }

    @Test
    fun `nextPage increments currentPage`() = runTest(testDispatcher) {
        val vm = OnboardingViewModel(FakePreferenceRepository())
        vm.nextPage()
        assertEquals(1, vm.uiState.value.currentPage)
    }

    @Test
    fun `nextPage does not exceed the last page`() = runTest(testDispatcher) {
        val vm = OnboardingViewModel(FakePreferenceRepository())
        repeat(OnboardingViewModel.PAGE_COUNT + 3) { vm.nextPage() }
        assertEquals(OnboardingViewModel.PAGE_COUNT - 1, vm.uiState.value.currentPage)
    }

    @Test
    fun `previousPage decrements currentPage`() = runTest(testDispatcher) {
        val vm = OnboardingViewModel(FakePreferenceRepository())
        vm.nextPage()
        vm.previousPage()
        assertEquals(0, vm.uiState.value.currentPage)
    }

    @Test
    fun `previousPage does not go below 0`() = runTest(testDispatcher) {
        val vm = OnboardingViewModel(FakePreferenceRepository())
        vm.previousPage()
        assertEquals(0, vm.uiState.value.currentPage)
    }

    @Test
    fun `toggleAllergen adds allergen to selection`() = runTest(testDispatcher) {
        val vm = OnboardingViewModel(FakePreferenceRepository())
        vm.toggleAllergen("en:milk")
        assertTrue("en:milk" in vm.uiState.value.selectedAllergens)
    }

    @Test
    fun `toggleAllergen removes already selected allergen`() = runTest(testDispatcher) {
        val vm = OnboardingViewModel(FakePreferenceRepository())
        vm.toggleAllergen("en:milk")
        vm.toggleAllergen("en:milk")
        assertFalse("en:milk" in vm.uiState.value.selectedAllergens)
    }

    @Test
    fun `toggleAvoidUltraProcessed flips the value`() = runTest(testDispatcher) {
        val vm = OnboardingViewModel(FakePreferenceRepository())
        assertFalse(vm.uiState.value.avoidUltraProcessed)
        vm.toggleAvoidUltraProcessed()
        assertTrue(vm.uiState.value.avoidUltraProcessed)
        vm.toggleAvoidUltraProcessed()
        assertFalse(vm.uiState.value.avoidUltraProcessed)
    }

    @Test
    fun `completeOnboarding sets onboarding_completed to true`() = runTest(testDispatcher) {
        val repo = FakePreferenceRepository()
        val vm = OnboardingViewModel(repo)

        vm.completeOnboarding()
        advanceUntilIdle()

        assertTrue(repo.onboardingCompleted)
    }

    @Test
    fun `skipOnboarding sets onboarding_completed to true`() = runTest(testDispatcher) {
        val repo = FakePreferenceRepository()
        val vm = OnboardingViewModel(repo)

        vm.skipOnboarding()
        advanceUntilIdle()

        assertTrue(repo.onboardingCompleted)
    }

    @Test
    fun `completeOnboarding saves selected allergens to preferences`() = runTest(testDispatcher) {
        val repo = FakePreferenceRepository()
        val vm = OnboardingViewModel(repo)

        vm.toggleAllergen("en:milk")
        vm.toggleAllergen("en:peanuts")
        vm.completeOnboarding()
        advanceUntilIdle()

        val saved = repo.savedPreferences!!
        assertTrue("en:milk" in saved.allergensToAvoid)
        assertTrue("en:peanuts" in saved.allergensToAvoid)
    }

    @Test
    fun `completeOnboarding saves avoidUltraProcessed to preferences`() = runTest(testDispatcher) {
        val repo = FakePreferenceRepository()
        val vm = OnboardingViewModel(repo)

        vm.toggleAvoidUltraProcessed()
        vm.completeOnboarding()
        advanceUntilIdle()

        assertTrue(repo.savedPreferences!!.avoidUltraProcessed)
    }

    @Test
    fun `completeOnboarding merges with existing preferences`() = runTest(testDispatcher) {
        val repo = FakePreferenceRepository(
            initial = UserFoodPreferences(maxSugarsPer100g = 5.0),
        )
        val vm = OnboardingViewModel(repo)

        vm.toggleAllergen("en:eggs")
        vm.completeOnboarding()
        advanceUntilIdle()

        val saved = repo.savedPreferences!!
        assertTrue("en:eggs" in saved.allergensToAvoid)
        assertEquals(5.0, saved.maxSugarsPer100g)
    }

    // ── Nutrition display fields ────────────────────────────────────────────

    @Test
    fun `nutrition fields default to the practical defaults`() = runTest(testDispatcher) {
        val vm = OnboardingViewModel(FakePreferenceRepository())
        assertEquals(NutritionDisplayOption.DEFAULT_KEYS, vm.uiState.value.selectedNutritionFields)
    }

    @Test
    fun `toggleNutritionField adds an unselected field`() = runTest(testDispatcher) {
        val vm = OnboardingViewModel(FakePreferenceRepository())
        vm.toggleNutritionField("fiber")
        assertTrue("fiber" in vm.uiState.value.selectedNutritionFields)
    }

    @Test
    fun `toggleNutritionField removes a selected field`() = runTest(testDispatcher) {
        val vm = OnboardingViewModel(FakePreferenceRepository())
        vm.toggleNutritionField("protein")
        assertFalse("protein" in vm.uiState.value.selectedNutritionFields)
    }

    @Test
    fun `toggleNutritionField cannot deselect the last remaining field`() = runTest(testDispatcher) {
        val repo = FakePreferenceRepository(initialNutritionFields = setOf("protein"))
        val vm = OnboardingViewModel(repo)

        vm.toggleNutritionField("protein")

        assertTrue("protein" in vm.uiState.value.selectedNutritionFields)
        assertEquals(1, vm.uiState.value.selectedNutritionFields.size)
    }

    @Test
    fun `completeOnboarding saves selected nutrition fields`() = runTest(testDispatcher) {
        val repo = FakePreferenceRepository()
        val vm = OnboardingViewModel(repo)

        vm.toggleNutritionField("fiber")
        vm.completeOnboarding()
        advanceUntilIdle()

        assertTrue("fiber" in repo.savedNutritionFields!!)
    }

    // ── Review flow: pre-filling stored preferences ─────────────────────────

    @Test
    fun `initial state loads stored preferences for review`() = runTest(testDispatcher) {
        val repo = FakePreferenceRepository(
            initial = UserFoodPreferences(
                allergensToAvoid = setOf("en:milk"),
                avoidUltraProcessed = true,
            ),
            initialNutritionFields = setOf("protein", "fiber"),
        )

        val vm = OnboardingViewModel(repo)
        advanceUntilIdle()

        assertTrue("en:milk" in vm.uiState.value.selectedAllergens)
        assertTrue(vm.uiState.value.avoidUltraProcessed)
        assertEquals(setOf("protein", "fiber"), vm.uiState.value.selectedNutritionFields)
    }

    @Test
    fun `setAvoidUltraProcessed sets the value directly`() = runTest(testDispatcher) {
        val vm = OnboardingViewModel(FakePreferenceRepository())
        vm.setAvoidUltraProcessed(true)
        assertTrue(vm.uiState.value.avoidUltraProcessed)
        vm.setAvoidUltraProcessed(false)
        assertFalse(vm.uiState.value.avoidUltraProcessed)
    }
}

private class FakePreferenceRepository(
    initial: UserFoodPreferences = UserFoodPreferences(),
    initialNutritionFields: Set<String> = NutritionDisplayOption.DEFAULT_KEYS,
) : PreferenceRepository {
    private val _prefs = MutableStateFlow(initial)
    private val _onboarding = MutableStateFlow(false)
    private val _nutritionFields = MutableStateFlow(initialNutritionFields)
    var savedPreferences: UserFoodPreferences? = null
    var savedNutritionFields: Set<String>? = null
    val onboardingCompleted: Boolean get() = _onboarding.value

    override fun getUserPreferences(): Flow<UserFoodPreferences> = _prefs

    override suspend fun saveUserPreferences(preferences: UserFoodPreferences) {
        savedPreferences = preferences
        _prefs.value = preferences
    }

    override fun observeOnboardingCompleted(): Flow<Boolean> = _onboarding

    override suspend fun setOnboardingCompleted(completed: Boolean) {
        _onboarding.value = completed
    }

    override fun observeSelectedNutritionFields(): Flow<Set<String>> = _nutritionFields

    override suspend fun saveSelectedNutritionFields(fields: Set<String>) {
        savedNutritionFields = fields
        _nutritionFields.value = fields.ifEmpty { NutritionDisplayOption.DEFAULT_KEYS }
    }
}
