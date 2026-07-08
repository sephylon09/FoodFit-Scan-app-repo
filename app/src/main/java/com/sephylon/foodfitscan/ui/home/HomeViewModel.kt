package com.sephylon.foodfitscan.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sephylon.foodfitscan.AppDependencies
import com.sephylon.foodfitscan.domain.model.UserFoodPreferences
import com.sephylon.foodfitscan.domain.repository.PreferenceRepository
import com.sephylon.foodfitscan.domain.util.BarcodeValidator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Result of the user pressing search on the Home screen. The UI is responsible for
 * performing navigation when the result is [NavigateToProduct]; the other outcomes
 * only surface an inline message (already applied to [HomeViewModel.searchMessage]).
 */
sealed interface HomeSearchResult {
    data class NavigateToProduct(val barcode: String) : HomeSearchResult
    data object Blank : HomeSearchResult
    data object ProductNameUnsupported : HomeSearchResult
}

class HomeViewModel(private val preferenceRepository: PreferenceRepository) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchMessage = MutableStateFlow<String?>(null)
    val searchMessage: StateFlow<String?> = _searchMessage.asStateFlow()

    val showPreferencesCard: StateFlow<Boolean> =
        preferenceRepository.getUserPreferences()
            .map { it.isAllDefault() }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = false,
            )

    /** Updates the query text. Never triggers a search — search only runs on submit. */
    fun onSearchQueryChange(value: String) {
        _searchQuery.value = value
        // Clear any stale message as soon as the user edits the query again.
        if (_searchMessage.value != null) {
            _searchMessage.value = null
        }
    }

    /**
     * Classifies the current query when the user presses the search icon or the
     * keyboard search action. This is the ONLY place a search is triggered — there is
     * no search-as-you-type.
     *
     * TODO(Firebase phase): add a lightweight Firebase-backed product index search here.
     *   - Only after the user presses search (keep: no search-as-you-type).
     *   - Query the index by product name (the current "not a barcode" branch).
     *   - Show tappable results; tapping a result opens ProductDetailScreen by its barcode.
     *   Do NOT add the Firebase dependency until that phase.
     */
    fun onSearchSubmit(): HomeSearchResult {
        val query = _searchQuery.value
        return when {
            query.isBlank() -> {
                _searchMessage.value = BLANK_MESSAGE
                HomeSearchResult.Blank
            }
            BarcodeValidator.isValidBarcode(query) -> {
                _searchMessage.value = null
                HomeSearchResult.NavigateToProduct(BarcodeValidator.normalizeBarcode(query))
            }
            else -> {
                // TODO(Firebase phase): replace this placeholder with real product-name search.
                _searchMessage.value = PRODUCT_NAME_MESSAGE
                HomeSearchResult.ProductNameUnsupported
            }
        }
    }

    private fun UserFoodPreferences.isAllDefault() =
        allergensToAvoid.isEmpty() &&
            additivesToAvoid.isEmpty() &&
            !avoidUltraProcessed &&
            maxSugarsPer100g == null &&
            maxSaltPer100g == null &&
            maxSaturatedFatPer100g == null

    companion object {
        const val BLANK_MESSAGE = "Enter a product name or barcode."
        const val PRODUCT_NAME_MESSAGE = "Product name search will be added in a later phase."

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { HomeViewModel(AppDependencies.preferenceRepository) }
        }
    }
}
