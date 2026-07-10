package com.sephylon.foodfitscan.ui.settings

import com.sephylon.foodfitscan.domain.model.AllergenOption
import com.sephylon.foodfitscan.domain.model.NutritionDisplayOption
import com.sephylon.foodfitscan.domain.model.SearchCountry
import com.sephylon.foodfitscan.domain.model.UserFoodPreferences
import com.sephylon.foodfitscan.domain.repository.PreferenceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
class SettingsViewModelTest {

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
    fun `initial state reflects default preferences`() = runTest(testDispatcher) {
        val vm = SettingsViewModel(FakePreferenceRepository())

        val state = vm.uiState.value
        assertTrue(state.selectedAllergens.isEmpty())
        assertTrue(state.selectedAdditives.isEmpty())
        assertFalse(state.avoidUltraProcessed)
        assertEquals("", state.maxSugarInput)
        assertEquals("", state.maxSaltInput)
        assertEquals("", state.maxSaturatedFatInput)
    }

    @Test
    fun `initial state loads stored allergens`() = runTest(testDispatcher) {
        val repo = FakePreferenceRepository(
            UserFoodPreferences(allergensToAvoid = setOf("en:milk", "en:eggs"))
        )
        val vm = SettingsViewModel(repo)

        assertTrue("en:milk" in vm.uiState.value.selectedAllergens)
        assertTrue("en:eggs" in vm.uiState.value.selectedAllergens)
    }

    @Test
    fun `initial state loads stored nutrition caps`() = runTest(testDispatcher) {
        val repo = FakePreferenceRepository(
            UserFoodPreferences(maxSugarsPer100g = 5.0, maxSaltPer100g = 1.5, maxSaturatedFatPer100g = 3.0)
        )
        val vm = SettingsViewModel(repo)

        assertEquals("5.0", vm.uiState.value.maxSugarInput)
        assertEquals("1.5", vm.uiState.value.maxSaltInput)
        assertEquals("3.0", vm.uiState.value.maxSaturatedFatInput)
    }

    @Test
    fun `selecting allergen adds it to selected set`() = runTest(testDispatcher) {
        val repo = FakePreferenceRepository()
        val vm = SettingsViewModel(repo)

        vm.toggleAllergen("en:milk")

        assertTrue("en:milk" in vm.uiState.value.selectedAllergens)
    }

    @Test
    fun `deselecting allergen removes it from selected set`() = runTest(testDispatcher) {
        val repo = FakePreferenceRepository(
            UserFoodPreferences(allergensToAvoid = setOf("en:milk"))
        )
        val vm = SettingsViewModel(repo)

        vm.toggleAllergen("en:milk")

        assertFalse("en:milk" in vm.uiState.value.selectedAllergens)
    }

    @Test
    fun `toggling allergen saves to repository`() = runTest(testDispatcher) {
        val repo = FakePreferenceRepository()
        val vm = SettingsViewModel(repo)

        vm.toggleAllergen("en:peanuts")

        assertTrue("en:peanuts" in repo.savedPreferences!!.allergensToAvoid)
    }

    @Test
    fun `selecting additive adds it to selected set`() = runTest(testDispatcher) {
        val repo = FakePreferenceRepository()
        val vm = SettingsViewModel(repo)

        vm.toggleAdditive("preservatives")

        assertTrue("preservatives" in vm.uiState.value.selectedAdditives)
    }

    @Test
    fun `deselecting additive removes it from selected set`() = runTest(testDispatcher) {
        val repo = FakePreferenceRepository(
            UserFoodPreferences(additivesToAvoid = setOf("preservatives"))
        )
        val vm = SettingsViewModel(repo)

        vm.toggleAdditive("preservatives")

        assertFalse("preservatives" in vm.uiState.value.selectedAdditives)
    }

    @Test
    fun `toggling additive saves to repository`() = runTest(testDispatcher) {
        val repo = FakePreferenceRepository()
        val vm = SettingsViewModel(repo)

        vm.toggleAdditive("artificial-colours")

        assertTrue("artificial-colours" in repo.savedPreferences!!.additivesToAvoid)
    }

    @Test
    fun `toggling avoid NOVA 4 flips the value`() = runTest(testDispatcher) {
        val repo = FakePreferenceRepository()
        val vm = SettingsViewModel(repo)

        assertFalse(vm.uiState.value.avoidUltraProcessed)
        vm.toggleAvoidUltraProcessed()
        assertTrue(vm.uiState.value.avoidUltraProcessed)
        vm.toggleAvoidUltraProcessed()
        assertFalse(vm.uiState.value.avoidUltraProcessed)
    }

    @Test
    fun `toggling avoid NOVA 4 saves to repository`() = runTest(testDispatcher) {
        val repo = FakePreferenceRepository()
        val vm = SettingsViewModel(repo)

        vm.toggleAvoidUltraProcessed()

        assertTrue(repo.savedPreferences!!.avoidUltraProcessed)
    }

    @Test
    fun `valid sugar input saves to repository`() = runTest(testDispatcher) {
        val repo = FakePreferenceRepository()
        val vm = SettingsViewModel(repo)

        vm.onSugarInputChanged("12.5")

        assertFalse(vm.uiState.value.sugarInputError)
        assertEquals(12.5, repo.savedPreferences!!.maxSugarsPer100g)
    }

    @Test
    fun `invalid sugar input shows error and does not save`() = runTest(testDispatcher) {
        val repo = FakePreferenceRepository()
        val vm = SettingsViewModel(repo)

        vm.onSugarInputChanged("abc")

        assertTrue(vm.uiState.value.sugarInputError)
        assertNull(repo.savedPreferences?.maxSugarsPer100g)
    }

    @Test
    fun `negative sugar input shows error`() = runTest(testDispatcher) {
        val repo = FakePreferenceRepository()
        val vm = SettingsViewModel(repo)

        vm.onSugarInputChanged("-5")

        assertTrue(vm.uiState.value.sugarInputError)
    }

    @Test
    fun `empty sugar input clears limit and saves null`() = runTest(testDispatcher) {
        val repo = FakePreferenceRepository(UserFoodPreferences(maxSugarsPer100g = 10.0))
        val vm = SettingsViewModel(repo)

        vm.onSugarInputChanged("")

        assertFalse(vm.uiState.value.sugarInputError)
        assertNull(repo.savedPreferences?.maxSugarsPer100g)
    }

    @Test
    fun `valid salt and saturated fat inputs save correctly`() = runTest(testDispatcher) {
        val repo = FakePreferenceRepository()
        val vm = SettingsViewModel(repo)

        vm.onSaltInputChanged("1.5")
        vm.onSaturatedFatInputChanged("3.0")

        assertEquals(1.5, repo.savedPreferences!!.maxSaltPer100g)
        assertEquals(3.0, repo.savedPreferences!!.maxSaturatedFatPer100g)
    }

    @Test
    fun `reset preferences restores defaults`() = runTest(testDispatcher) {
        val repo = FakePreferenceRepository(
            UserFoodPreferences(
                allergensToAvoid = setOf("en:milk"),
                avoidUltraProcessed = true,
                maxSugarsPer100g = 10.0,
            )
        )
        val vm = SettingsViewModel(repo)

        vm.resetPreferences()

        val state = vm.uiState.value
        assertTrue(state.selectedAllergens.isEmpty())
        assertFalse(state.avoidUltraProcessed)
        assertEquals("", state.maxSugarInput)
        assertFalse(state.showResetDialog)
        assertEquals(UserFoodPreferences(), repo.savedPreferences)
    }

    @Test
    fun `allergen options list contains all expected allergens`() = runTest(testDispatcher) {
        val vm = SettingsViewModel(FakePreferenceRepository())
        val keys = vm.uiState.value.allergenOptions.map { it.key }

        assertTrue("en:milk" in keys)
        assertTrue("en:gluten" in keys)
        assertTrue("en:peanuts" in keys)
        assertEquals(AllergenOption.ALL.size, keys.size)
    }

    // ── Nutrition display fields ────────────────────────────────────────────────

    @Test
    fun `default nutrition fields used when no saved preference exists`() = runTest(testDispatcher) {
        val vm = SettingsViewModel(FakePreferenceRepository())

        assertEquals(NutritionDisplayOption.DEFAULT_KEYS, vm.uiState.value.selectedNutritionFields)
    }

    @Test
    fun `initial state loads stored nutrition fields`() = runTest(testDispatcher) {
        val repo = FakePreferenceRepository(initialNutritionFields = setOf("protein", "carbohydrates"))
        val vm = SettingsViewModel(repo)

        assertEquals(setOf("protein", "carbohydrates"), vm.uiState.value.selectedNutritionFields)
    }

    @Test
    fun `nutrition field options list contains all nine fields`() = runTest(testDispatcher) {
        val vm = SettingsViewModel(FakePreferenceRepository())

        assertEquals(NutritionDisplayOption.ALL, vm.uiState.value.nutritionFieldOptions)
        assertEquals(9, vm.uiState.value.nutritionFieldOptions.size)
    }

    @Test
    fun `selecting a nutrition field adds it and persists`() = runTest(testDispatcher) {
        val repo = FakePreferenceRepository(initialNutritionFields = setOf("protein"))
        val vm = SettingsViewModel(repo)

        vm.toggleNutritionField("fiber")

        assertTrue("fiber" in vm.uiState.value.selectedNutritionFields)
        assertTrue("fiber" in repo.savedNutritionFields!!)
    }

    @Test
    fun `deselecting a non-last nutrition field removes it and persists`() = runTest(testDispatcher) {
        val repo = FakePreferenceRepository(initialNutritionFields = setOf("protein", "fiber"))
        val vm = SettingsViewModel(repo)

        vm.toggleNutritionField("fiber")

        assertFalse("fiber" in vm.uiState.value.selectedNutritionFields)
        assertEquals(setOf("protein"), repo.savedNutritionFields)
    }

    @Test
    fun `cannot deselect the last nutrition field and shows warning`() = runTest(testDispatcher) {
        val repo = FakePreferenceRepository(initialNutritionFields = setOf("protein"))
        val vm = SettingsViewModel(repo)

        vm.toggleNutritionField("protein")

        assertTrue("protein" in vm.uiState.value.selectedNutritionFields)
        assertTrue(vm.uiState.value.showLastFieldWarning)
        assertNull(repo.savedNutritionFields)
    }

    @Test
    fun `selecting a field clears the last-field warning`() = runTest(testDispatcher) {
        val repo = FakePreferenceRepository(initialNutritionFields = setOf("protein"))
        val vm = SettingsViewModel(repo)

        vm.toggleNutritionField("protein") // triggers warning
        assertTrue(vm.uiState.value.showLastFieldWarning)

        vm.toggleNutritionField("sugars") // selecting clears warning
        assertFalse(vm.uiState.value.showLastFieldWarning)
    }

    @Test
    fun `reset nutrition fields restores defaults and persists`() = runTest(testDispatcher) {
        val repo = FakePreferenceRepository(initialNutritionFields = setOf("protein", "carbohydrates"))
        val vm = SettingsViewModel(repo)

        vm.resetNutritionFields()

        assertEquals(NutritionDisplayOption.DEFAULT_KEYS, vm.uiState.value.selectedNutritionFields)
        assertEquals(NutritionDisplayOption.DEFAULT_KEYS, repo.savedNutritionFields)
    }
}

private class FakePreferenceRepository(
    initial: UserFoodPreferences = UserFoodPreferences(),
    initialNutritionFields: Set<String> = NutritionDisplayOption.DEFAULT_KEYS,
) : PreferenceRepository {
    private val _prefs = MutableStateFlow(initial)
    private val _onboardingCompleted = MutableStateFlow(false)
    private val _nutritionFields = MutableStateFlow(initialNutritionFields)
    private val _searchCountry = MutableStateFlow<SearchCountry?>(null)
    var savedPreferences: UserFoodPreferences? = null
    var savedNutritionFields: Set<String>? = null

    override fun getUserPreferences(): Flow<UserFoodPreferences> = _prefs

    override suspend fun saveUserPreferences(preferences: UserFoodPreferences) {
        savedPreferences = preferences
        _prefs.value = preferences
    }

    override fun observeOnboardingCompleted(): Flow<Boolean> = _onboardingCompleted

    override suspend fun setOnboardingCompleted(completed: Boolean) {
        _onboardingCompleted.value = completed
    }

    override fun observeSelectedNutritionFields(): Flow<Set<String>> = _nutritionFields

    override suspend fun saveSelectedNutritionFields(fields: Set<String>) {
        savedNutritionFields = fields
        // Mirror the real repository fallback: an empty saved set resolves to defaults.
        _nutritionFields.value = fields.ifEmpty { NutritionDisplayOption.DEFAULT_KEYS }
    }

    override fun observeSearchCountry(): Flow<SearchCountry?> = _searchCountry

    override suspend fun saveSearchCountry(country: SearchCountry) {
        _searchCountry.value = country
    }
}
