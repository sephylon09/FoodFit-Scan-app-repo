package com.sephylon.foodfitscan

import com.sephylon.foodfitscan.domain.model.NutritionDisplayOption
import com.sephylon.foodfitscan.domain.model.UserFoodPreferences
import com.sephylon.foodfitscan.domain.repository.PreferenceRepository
import com.sephylon.foodfitscan.ui.navigation.Screen
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
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelTest {

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
    fun `startDestination is onboarding when onboarding not completed`() = runTest(testDispatcher) {
        val repo = FakePreferenceRepository(onboardingCompleted = false)
        val vm = AppViewModel(repo)

        val result = vm.startDestination.first { it != null }

        assertEquals(Screen.Onboarding.route, result)
    }

    @Test
    fun `startDestination is home when onboarding completed`() = runTest(testDispatcher) {
        val repo = FakePreferenceRepository(onboardingCompleted = true)
        val vm = AppViewModel(repo)

        val result = vm.startDestination.first { it != null }

        assertEquals(Screen.Home.route, result)
    }

    @Test
    fun `default onboarding_completed is false`() = runTest(testDispatcher) {
        val repo = FakePreferenceRepository()
        val vm = AppViewModel(repo)

        val result = vm.startDestination.first { it != null }

        assertEquals(Screen.Onboarding.route, result)
    }
}

private class FakePreferenceRepository(
    onboardingCompleted: Boolean = false,
) : PreferenceRepository {
    private val _prefs = MutableStateFlow(UserFoodPreferences())
    private val _onboarding = MutableStateFlow(onboardingCompleted)
    private val _nutritionFields = MutableStateFlow(NutritionDisplayOption.DEFAULT_KEYS)

    override fun getUserPreferences(): Flow<UserFoodPreferences> = _prefs
    override suspend fun saveUserPreferences(preferences: UserFoodPreferences) {
        _prefs.value = preferences
    }
    override fun observeOnboardingCompleted(): Flow<Boolean> = _onboarding
    override suspend fun setOnboardingCompleted(completed: Boolean) {
        _onboarding.value = completed
    }
    override fun observeSelectedNutritionFields(): Flow<Set<String>> = _nutritionFields
    override suspend fun saveSelectedNutritionFields(fields: Set<String>) {
        _nutritionFields.value = fields.ifEmpty { NutritionDisplayOption.DEFAULT_KEYS }
    }
}
