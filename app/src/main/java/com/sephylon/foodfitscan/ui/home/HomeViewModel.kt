package com.sephylon.foodfitscan.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sephylon.foodfitscan.AppDependencies
import com.sephylon.foodfitscan.domain.model.ProductSearchResult
import com.sephylon.foodfitscan.domain.model.SearchCountry
import com.sephylon.foodfitscan.domain.model.UserFoodPreferences
import com.sephylon.foodfitscan.domain.repository.PreferenceRepository
import com.sephylon.foodfitscan.domain.repository.ProductSearchRepository
import com.sephylon.foodfitscan.domain.util.BarcodeValidator
import com.sephylon.foodfitscan.domain.util.SearchTextNormalizer
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Synchronous outcome of the user pressing search. [NavigateToProduct] tells the UI to
 * open ProductDetail for a scanned/typed barcode; [Handled] means the outcome is reflected
 * in [HomeViewModel.searchState] (validation message, loading, results, or error).
 */
sealed interface HomeSearchAction {
    data class NavigateToProduct(val barcode: String) : HomeSearchAction
    data object Handled : HomeSearchAction
}

class HomeViewModel(
    private val preferenceRepository: PreferenceRepository,
    private val productSearchRepository: ProductSearchRepository,
    deviceRegionCode: String?,
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchState = MutableStateFlow<ProductSearchResult>(ProductSearchResult.Idle)
    val searchState: StateFlow<ProductSearchResult> = _searchState.asStateFlow()

    /**
     * Country used until the user picks one explicitly. Derived from the device locale's
     * region only — no location permission, no GPS.
     */
    private val deviceCountry = SearchCountry.fromRegionCode(deviceRegionCode)

    private val _selectedCountry = MutableStateFlow(deviceCountry)
    val selectedCountry: StateFlow<SearchCountry> = _selectedCountry.asStateFlow()

    /**
     * Product-name query behind the currently displayed results, so [retryLastSearch] and
     * [onCountrySelected] can re-run it. Null whenever no results are on screen.
     */
    private var lastSearchQuery: String? = null
    private var searchJob: Job? = null

    val showPreferencesCard: StateFlow<Boolean> =
        preferenceRepository.getUserPreferences()
            .map { it.isAllDefault() }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = false,
            )

    init {
        // A stored choice overrides the device default. Null emissions mean "never chosen"
        // (or a country we no longer support), so the seeded default simply stays.
        viewModelScope.launch {
            preferenceRepository.observeSearchCountry()
                .filterNotNull()
                .collect { _selectedCountry.value = it }
        }
    }

    /** Updates the query text. Never triggers a search — search only runs on submit. */
    fun onSearchQueryChange(value: String) {
        _searchQuery.value = value
        // Editing the query clears any prior message/results and returns to the intro.
        if (_searchState.value != ProductSearchResult.Idle) {
            _searchState.value = ProductSearchResult.Idle
        }
        lastSearchQuery = null
    }

    /**
     * Classifies the current query when the user presses the search icon or the keyboard
     * search action. This is the ONLY place a search is triggered — there is no
     * search-as-you-type.
     *
     * - blank -> validation message
     * - valid barcode -> navigate straight to ProductDetail (no Firebase call)
     * - too short (no searchable word >= [SearchTextNormalizer.MIN_PREFIX_LENGTH]) -> validation
     * - otherwise -> product-name search against Firestore, filtered by [selectedCountry]
     */
    fun onSearchSubmit(): HomeSearchAction {
        val query = _searchQuery.value
        return when {
            query.isBlank() -> {
                _searchState.value = ProductSearchResult.ValidationError(BLANK_MESSAGE)
                HomeSearchAction.Handled
            }
            BarcodeValidator.isValidBarcode(query) -> {
                // Barcode lookups are country-agnostic and bypass the search index entirely.
                _searchState.value = ProductSearchResult.Idle
                lastSearchQuery = null
                HomeSearchAction.NavigateToProduct(BarcodeValidator.normalizeBarcode(query))
            }
            SearchTextNormalizer.queryPrefix(query) == null -> {
                _searchState.value = ProductSearchResult.ValidationError(MIN_LENGTH_MESSAGE)
                HomeSearchAction.Handled
            }
            else -> {
                runProductNameSearch(query)
                HomeSearchAction.Handled
            }
        }
    }

    /**
     * Applies a new country filter, persists it, and refreshes the results already on
     * screen so they respect the new filter.
     */
    fun onCountrySelected(country: SearchCountry) {
        if (_selectedCountry.value == country) return
        _selectedCountry.value = country
        viewModelScope.launch { preferenceRepository.saveSearchCountry(country) }
        lastSearchQuery?.let { runProductNameSearch(it) }
    }

    /** Re-runs the most recent product-name search (used by the error-state retry button). */
    fun retryLastSearch() {
        val query = lastSearchQuery ?: return
        runProductNameSearch(query)
    }

    private fun runProductNameSearch(query: String) {
        lastSearchQuery = query
        // Cancel any in-flight search so overlapping submits don't race.
        searchJob?.cancel()
        _searchState.value = ProductSearchResult.Loading
        searchJob = viewModelScope.launch {
            _searchState.value =
                productSearchRepository.searchByName(query, _selectedCountry.value)
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
        const val MIN_LENGTH_MESSAGE = "Enter at least 3 characters to search."

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                HomeViewModel(
                    preferenceRepository = AppDependencies.preferenceRepository,
                    productSearchRepository = AppDependencies.productSearchRepository,
                    deviceRegionCode = AppDependencies.deviceRegionProvider.regionCode(),
                )
            }
        }
    }
}
