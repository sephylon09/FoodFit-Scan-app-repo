package com.sephylon.foodfitscan.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sephylon.foodfitscan.AppDependencies
import com.sephylon.foodfitscan.domain.model.ScanHistoryItem
import com.sephylon.foodfitscan.domain.repository.ProductRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HistoryViewModel(
    private val repository: ProductRepository,
) : ViewModel() {

    val historyItems: StateFlow<List<ScanHistoryItem>> = repository
        .observeScanHistory(100)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearScanHistory()
        }
    }

    fun deleteItem(id: Long) {
        viewModelScope.launch {
            repository.deleteScanHistoryItem(id)
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                HistoryViewModel(AppDependencies.productRepository)
            }
        }
    }
}
