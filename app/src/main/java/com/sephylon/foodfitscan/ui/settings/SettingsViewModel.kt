package com.sephylon.foodfitscan.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sephylon.foodfitscan.AppDependencies
import com.sephylon.foodfitscan.domain.model.AdditiveOption
import com.sephylon.foodfitscan.domain.model.AllergenOption
import com.sephylon.foodfitscan.domain.model.NutritionDisplayOption
import com.sephylon.foodfitscan.domain.model.UserFoodPreferences
import com.sephylon.foodfitscan.domain.repository.PreferenceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val allergenOptions: List<AllergenOption> = AllergenOption.ALL,
    val selectedAllergens: Set<String> = emptySet(),
    val additiveOptions: List<AdditiveOption> = AdditiveOption.ALL,
    val selectedAdditives: Set<String> = emptySet(),
    val avoidUltraProcessed: Boolean = false,
    val maxSugarInput: String = "",
    val maxSaltInput: String = "",
    val maxSaturatedFatInput: String = "",
    val sugarInputError: Boolean = false,
    val saltInputError: Boolean = false,
    val saturatedFatInputError: Boolean = false,
    val nutritionFieldOptions: List<NutritionDisplayOption> = NutritionDisplayOption.ALL,
    val selectedNutritionFields: Set<String> = NutritionDisplayOption.DEFAULT_KEYS,
    val showLastFieldWarning: Boolean = false,
    val showResetDialog: Boolean = false,
    val isLoaded: Boolean = false,
)

class SettingsViewModel(
    private val repository: PreferenceRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val prefs = repository.getUserPreferences().first()
            val nutritionFields = repository.observeSelectedNutritionFields().first()
            _uiState.update {
                it.copy(
                    selectedAllergens = prefs.allergensToAvoid,
                    selectedAdditives = prefs.additivesToAvoid,
                    avoidUltraProcessed = prefs.avoidUltraProcessed,
                    maxSugarInput = prefs.maxSugarsPer100g?.toString() ?: "",
                    maxSaltInput = prefs.maxSaltPer100g?.toString() ?: "",
                    maxSaturatedFatInput = prefs.maxSaturatedFatPer100g?.toString() ?: "",
                    selectedNutritionFields = nutritionFields,
                    isLoaded = true,
                )
            }
        }
    }

    fun toggleAllergen(key: String) {
        val selected = _uiState.value.selectedAllergens
        val updated = if (key in selected) selected - key else selected + key
        _uiState.update { it.copy(selectedAllergens = updated) }
        savePreferences()
    }

    fun toggleAdditive(key: String) {
        val selected = _uiState.value.selectedAdditives
        val updated = if (key in selected) selected - key else selected + key
        _uiState.update { it.copy(selectedAdditives = updated) }
        savePreferences()
    }

    fun toggleAvoidUltraProcessed() {
        _uiState.update { it.copy(avoidUltraProcessed = !it.avoidUltraProcessed) }
        savePreferences()
    }

    fun onSugarInputChanged(value: String) {
        val error = isInvalidNutritionInput(value)
        _uiState.update { it.copy(maxSugarInput = value, sugarInputError = error) }
        if (!error) savePreferences()
    }

    fun onSaltInputChanged(value: String) {
        val error = isInvalidNutritionInput(value)
        _uiState.update { it.copy(maxSaltInput = value, saltInputError = error) }
        if (!error) savePreferences()
    }

    fun onSaturatedFatInputChanged(value: String) {
        val error = isInvalidNutritionInput(value)
        _uiState.update { it.copy(maxSaturatedFatInput = value, saturatedFatInputError = error) }
        if (!error) savePreferences()
    }

    fun showResetDialog() {
        _uiState.update { it.copy(showResetDialog = true) }
    }

    fun dismissResetDialog() {
        _uiState.update { it.copy(showResetDialog = false) }
    }

    fun resetPreferences() {
        viewModelScope.launch {
            repository.saveUserPreferences(UserFoodPreferences())
            _uiState.update {
                it.copy(
                    selectedAllergens = emptySet(),
                    selectedAdditives = emptySet(),
                    avoidUltraProcessed = false,
                    maxSugarInput = "",
                    maxSaltInput = "",
                    maxSaturatedFatInput = "",
                    sugarInputError = false,
                    saltInputError = false,
                    saturatedFatInputError = false,
                    showResetDialog = false,
                )
            }
        }
    }

    /**
     * Toggles a nutrition display field. At least one field must remain selected —
     * attempting to deselect the last field is rejected and surfaces a warning.
     */
    fun toggleNutritionField(key: String) {
        val selected = _uiState.value.selectedNutritionFields
        if (key in selected) {
            if (selected.size <= 1) {
                _uiState.update { it.copy(showLastFieldWarning = true) }
                return
            }
            updateNutritionFields(selected - key)
        } else {
            updateNutritionFields(selected + key)
        }
    }

    fun resetNutritionFields() {
        updateNutritionFields(NutritionDisplayOption.DEFAULT_KEYS)
    }

    private fun updateNutritionFields(fields: Set<String>) {
        _uiState.update { it.copy(selectedNutritionFields = fields, showLastFieldWarning = false) }
        viewModelScope.launch {
            repository.saveSelectedNutritionFields(fields)
        }
    }

    private fun savePreferences() {
        val state = _uiState.value
        viewModelScope.launch {
            repository.saveUserPreferences(
                UserFoodPreferences(
                    allergensToAvoid = state.selectedAllergens,
                    additivesToAvoid = state.selectedAdditives,
                    avoidUltraProcessed = state.avoidUltraProcessed,
                    maxSugarsPer100g = state.maxSugarInput.toDoubleOrNull(),
                    maxSaltPer100g = state.maxSaltInput.toDoubleOrNull(),
                    maxSaturatedFatPer100g = state.maxSaturatedFatInput.toDoubleOrNull(),
                )
            )
        }
    }

    private fun isInvalidNutritionInput(value: String): Boolean {
        if (value.isEmpty()) return false
        val parsed = value.toDoubleOrNull() ?: return true
        return parsed < 0
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                SettingsViewModel(AppDependencies.preferenceRepository)
            }
        }
    }
}
