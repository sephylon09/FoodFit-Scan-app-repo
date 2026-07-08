package com.sephylon.foodfitscan.domain.model

data class SuitabilityResult(
    val level: SuitabilityLevel,
    val reasons: List<String> = emptyList(),
    val noPreferencesConfigured: Boolean = false,
) {
    companion object {
        fun unknown() = SuitabilityResult(level = SuitabilityLevel.UNKNOWN)
    }
}
