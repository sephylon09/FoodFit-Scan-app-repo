package com.sephylon.foodfitscan.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sephylon.foodfitscan.AppDependencies
import com.sephylon.foodfitscan.domain.model.NutritionDisplayOption
import com.sephylon.foodfitscan.domain.repository.PreferenceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OnboardingUiState(
    val currentPage: Int = 0,
    val selectedAllergens: Set<String> = emptySet(),
    val selectedNutritionFields: Set<String> = NutritionDisplayOption.DEFAULT_KEYS,
    val avoidUltraProcessed: Boolean = false,
)

class OnboardingViewModel(
    private val preferenceRepository: PreferenceRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    init {
        // Pre-fill with any stored preferences so "Review onboarding" reflects
        // the user's current setup instead of starting blank.
        viewModelScope.launch {
            val prefs = preferenceRepository.getUserPreferences().first()
            val nutritionFields = preferenceRepository.observeSelectedNutritionFields().first()
            _uiState.update {
                it.copy(
                    selectedAllergens = prefs.allergensToAvoid,
                    avoidUltraProcessed = prefs.avoidUltraProcessed,
                    selectedNutritionFields = nutritionFields,
                )
            }
        }
    }

    fun nextPage() {
        _uiState.update {
            it.copy(currentPage = (it.currentPage + 1).coerceAtMost(PAGE_COUNT - 1))
        }
    }

    fun previousPage() {
        if (_uiState.value.currentPage > 0) {
            _uiState.update { it.copy(currentPage = it.currentPage - 1) }
        }
    }

    fun toggleAllergen(key: String) {
        val selected = _uiState.value.selectedAllergens
        val updated = if (key in selected) selected - key else selected + key
        _uiState.update { it.copy(selectedAllergens = updated) }
    }

    /**
     * Toggles a nutrition display field. At least one field must stay selected, so
     * deselecting the last one is ignored (mirrors the Settings screen rule).
     */
    fun toggleNutritionField(key: String) {
        val selected = _uiState.value.selectedNutritionFields
        if (key in selected && selected.size <= 1) return
        val updated = if (key in selected) selected - key else selected + key
        _uiState.update { it.copy(selectedNutritionFields = updated) }
    }

    fun setAvoidUltraProcessed(avoid: Boolean) {
        _uiState.update { it.copy(avoidUltraProcessed = avoid) }
    }

    fun toggleAvoidUltraProcessed() {
        _uiState.update { it.copy(avoidUltraProcessed = !it.avoidUltraProcessed) }
    }

    fun completeOnboarding() {
        viewModelScope.launch {
            val existing = preferenceRepository.getUserPreferences().first()
            preferenceRepository.saveUserPreferences(
                existing.copy(
                    allergensToAvoid = _uiState.value.selectedAllergens,
                    avoidUltraProcessed = _uiState.value.avoidUltraProcessed,
                ),
            )
            preferenceRepository.saveSelectedNutritionFields(_uiState.value.selectedNutritionFields)
            preferenceRepository.setOnboardingCompleted(true)
        }
    }

    fun skipOnboarding() {
        viewModelScope.launch {
            preferenceRepository.setOnboardingCompleted(true)
        }
    }

    companion object {
        /** Intro, allergens, nutrition fields, ultra-processed, data credit. */
        const val PAGE_COUNT = 5

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { OnboardingViewModel(AppDependencies.preferenceRepository) }
        }
    }
}
