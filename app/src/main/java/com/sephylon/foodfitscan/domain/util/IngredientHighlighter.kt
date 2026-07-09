package com.sephylon.foodfitscan.domain.util

/**
 * Finds occurrences of the user's avoided allergens inside a product's free-text
 * ingredient list so the UI can warn about and highlight them. Matching is practical
 * rather than exhaustive: each allergen preference key maps to a small set of common
 * ingredient terms, and a term matches any word that starts with it ("milk" matches
 * "milk" in "milk powder"; "soy" matches "soy" and "soya" in "soya lecithin").
 */
object IngredientHighlighter {

    /** A matched span in the ingredient text; [endExclusive] follows String indexing. */
    data class HighlightSpan(val start: Int, val endExclusive: Int)

    /**
     * Ingredient words associated with each allergen preference key
     * (see [com.sephylon.foodfitscan.domain.model.AllergenOption]). Terms are matched
     * case-insensitively at word starts, including derived words ("eggs", "buttermilk").
     */
    private val termsByAllergenKey: Map<String, List<String>> = mapOf(
        "en:milk" to listOf("milk", "lactose", "whey", "casein", "butter", "cream", "yogurt", "yoghurt"),
        "en:eggs" to listOf("egg", "albumin", "ovalbumin"),
        "en:peanuts" to listOf("peanut", "groundnut"),
        "en:nuts" to listOf(
            "almond", "hazelnut", "walnut", "cashew", "pistachio", "pecan", "macadamia", "brazil nut",
        ),
        "en:soybeans" to listOf("soy", "tofu", "edamame"),
        "en:gluten" to listOf("wheat", "gluten", "barley", "rye", "spelt", "kamut", "semolina"),
        "en:fish" to listOf("fish", "salmon", "tuna", "cod", "anchovy", "sardine", "trout"),
        "en:crustaceans" to listOf("shrimp", "prawn", "crab", "lobster", "crayfish", "crustacean"),
        "en:sesame-seeds" to listOf("sesame", "tahini"),
    )

    /**
     * Returns the merged, ordered spans of [ingredientsText] that match any term for the
     * given avoided allergen keys. Empty when there is nothing to highlight.
     */
    fun findMatchSpans(
        ingredientsText: String?,
        avoidedAllergenKeys: Set<String>,
    ): List<HighlightSpan> {
        if (ingredientsText.isNullOrBlank() || avoidedAllergenKeys.isEmpty()) return emptyList()

        val terms = avoidedAllergenKeys
            .flatMap { key -> termsByAllergenKey[key.lowercase()].orEmpty() }
            .distinct()
        if (terms.isEmpty()) return emptyList()

        // One alternation regex, longest terms first so overlapping terms prefer the
        // longer match. Each term matches from a word boundary through the rest of the
        // word ("egg" -> "eggs", "butter" -> "buttermilk").
        val pattern = terms
            .sortedByDescending { it.length }
            .joinToString("|") { Regex.escape(it) }
        val regex = Regex("\\b(?:$pattern)[\\p{L}]*", RegexOption.IGNORE_CASE)

        val raw = regex.findAll(ingredientsText)
            .map { HighlightSpan(it.range.first, it.range.last + 1) }
            .sortedBy { it.start }
            .toList()
        return mergeOverlapping(raw)
    }

    /** True when [ingredientsText] mentions at least one avoided allergen term. */
    fun hasMatches(ingredientsText: String?, avoidedAllergenKeys: Set<String>): Boolean =
        findMatchSpans(ingredientsText, avoidedAllergenKeys).isNotEmpty()

    private fun mergeOverlapping(spans: List<HighlightSpan>): List<HighlightSpan> {
        if (spans.size < 2) return spans
        val merged = mutableListOf(spans.first())
        for (span in spans.drop(1)) {
            val last = merged.last()
            if (span.start <= last.endExclusive) {
                merged[merged.lastIndex] =
                    HighlightSpan(last.start, maxOf(last.endExclusive, span.endExclusive))
            } else {
                merged.add(span)
            }
        }
        return merged
    }
}
