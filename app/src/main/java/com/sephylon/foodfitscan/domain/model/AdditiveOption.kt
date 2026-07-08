package com.sephylon.foodfitscan.domain.model

data class AdditiveOption(
    val key: String,
    val displayName: String,
) {
    companion object {
        val ALL = listOf(
            AdditiveOption("artificial-sweeteners", "Artificial sweeteners"),
            AdditiveOption("artificial-colours", "Artificial colours"),
            AdditiveOption("preservatives", "Preservatives"),
            AdditiveOption("flavour-enhancers", "Flavour enhancers"),
        )
    }
}
