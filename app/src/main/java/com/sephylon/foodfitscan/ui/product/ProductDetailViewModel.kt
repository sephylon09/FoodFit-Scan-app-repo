package com.sephylon.foodfitscan.ui.product

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sephylon.foodfitscan.AppDependencies
import com.sephylon.foodfitscan.domain.model.AlternativesResult
import com.sephylon.foodfitscan.domain.model.NutritionDisplayOption
import com.sephylon.foodfitscan.domain.model.ProductDetails
import com.sephylon.foodfitscan.domain.model.ProductLookupResult
import com.sephylon.foodfitscan.domain.model.SuitabilityResult
import com.sephylon.foodfitscan.domain.repository.PreferenceRepository
import com.sephylon.foodfitscan.domain.repository.ProductRepository
import com.sephylon.foodfitscan.domain.rules.SuitabilityScorer
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

sealed class ProductDetailUiState {
    data object Loading : ProductDetailUiState()
    data class ProductFound(
        val product: ProductDetails,
        val isFromStaleCache: Boolean = false,
        val suitabilityResult: SuitabilityResult,
        val selectedNutritionKeys: Set<String> = NutritionDisplayOption.DEFAULT_KEYS,
        /** User's avoided allergen keys, for ingredient warnings/highlighting in the UI. */
        val avoidedAllergenKeys: Set<String> = emptySet(),
    ) : ProductDetailUiState()
    data class NotFound(val barcode: String) : ProductDetailUiState()
    data class NetworkError(val message: String) : ProductDetailUiState()
    data class UnknownError(val message: String) : ProductDetailUiState()
}

class ProductDetailViewModel(
    private val barcode: String,
    private val productRepository: ProductRepository,
    private val preferenceRepository: PreferenceRepository,
    private val scorer: SuitabilityScorer = SuitabilityScorer(),
) : ViewModel() {

    private val _uiState = MutableStateFlow<ProductDetailUiState>(ProductDetailUiState.Loading)
    val uiState: StateFlow<ProductDetailUiState> = _uiState.asStateFlow()

    private val _alternativesState = MutableStateFlow<AlternativesResult>(AlternativesResult.Idle)
    val alternativesState: StateFlow<AlternativesResult> = _alternativesState.asStateFlow()

    private var loadJob: Job? = null
    private var alternativesJob: Job? = null

    init {
        loadProduct()
    }

    fun retry() {
        loadProduct()
    }

    fun loadAlternatives() {
        if (_alternativesState.value is AlternativesResult.Loading) return
        val product = (uiState.value as? ProductDetailUiState.ProductFound)?.product ?: return
        alternativesJob?.cancel()
        alternativesJob = viewModelScope.launch {
            _alternativesState.value = AlternativesResult.Loading
            val prefs = preferenceRepository.getUserPreferences().first()
            _alternativesState.value = productRepository.findAlternativesFor(product, prefs)
        }
    }

    private fun loadProduct() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.value = ProductDetailUiState.Loading
            when (val result = productRepository.getProduct(barcode)) {
                is ProductLookupResult.Found -> {
                    // Observe preferences and nutrition display fields so the screen updates
                    // live when the user changes settings. Suitability scoring uses only the
                    // food preferences; the selected nutrition fields are display-only.
                    combine(
                        preferenceRepository.getUserPreferences(),
                        preferenceRepository.observeSelectedNutritionFields(),
                    ) { prefs, nutritionKeys ->
                        ProductDetailUiState.ProductFound(
                            product = result.product,
                            isFromStaleCache = result.isFromStaleCache,
                            suitabilityResult = scorer.score(result.product, prefs),
                            selectedNutritionKeys = nutritionKeys,
                            avoidedAllergenKeys = prefs.allergensToAvoid,
                        )
                    }.collect { _uiState.value = it }
                }
                is ProductLookupResult.NotFound ->
                    _uiState.value = ProductDetailUiState.NotFound(barcode)
                is ProductLookupResult.NetworkError ->
                    _uiState.value = ProductDetailUiState.NetworkError(result.message)
                is ProductLookupResult.UnknownError ->
                    _uiState.value = ProductDetailUiState.UnknownError(result.message)
            }
        }
    }

    companion object {
        fun factory(
            barcode: String,
            productRepository: ProductRepository = AppDependencies.productRepository,
            preferenceRepository: PreferenceRepository = AppDependencies.preferenceRepository,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ProductDetailViewModel(
                    barcode = barcode,
                    productRepository = productRepository,
                    preferenceRepository = preferenceRepository,
                )
            }
        }
    }
}
