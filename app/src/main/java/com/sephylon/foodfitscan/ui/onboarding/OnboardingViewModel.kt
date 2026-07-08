package com.sephylon.foodfitscan.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sephylon.foodfitscan.AppDependencies
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
    val avoidUltraProcessed: Boolean = false,
)

class OnboardingViewModel(
    private val preferenceRepository: PreferenceRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    fun nextPage() {
        _uiState.update { it.copy(currentPage = it.currentPage + 1) }
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
            preferenceRepository.setOnboardingCompleted(true)
        }
    }

    fun skipOnboarding() {
        viewModelScope.launch {
            preferenceRepository.setOnboardingCompleted(true)
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { OnboardingViewModel(AppDependencies.preferenceRepository) }
        }
    }
}
