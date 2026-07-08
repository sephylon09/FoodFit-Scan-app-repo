package com.sephylon.foodfitscan.domain.model

enum class SuitabilityLevel {
    GOOD_MATCH,
    CAUTION,
    AVOID,
    UNKNOWN;

    internal val priority: Int
        get() = when (this) {
            AVOID -> 3
            CAUTION -> 2
            UNKNOWN -> 1
            GOOD_MATCH -> 0
        }
}
