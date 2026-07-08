package com.sephylon.foodfitscan.domain.model

sealed class AlternativesResult {
    data object Idle : AlternativesResult()
    data object Loading : AlternativesResult()
    data class Success(val alternatives: List<AlternativeProduct>) : AlternativesResult()
    data object NoCategory : AlternativesResult()
    data object Empty : AlternativesResult()
    data class NetworkError(val message: String) : AlternativesResult()
    data class UnknownError(val message: String) : AlternativesResult()
}
