package com.sephylon.foodfitscan.domain.util

/**
 * Normalizes free-text product-name search input to match the index built by the
 * GitHub Actions sync script (`scripts/sync-off-to-firestore.js`). Keep the rules here
 * in sync with that script's `normalizeText` / `buildPrefixes`:
 *   - lowercase
 *   - "&" -> " and "
 *   - drop punctuation (anything but a-z, 0-9, whitespace)
 *   - collapse whitespace and trim
 * The sync stores per-word prefixes only for words of at least [MIN_PREFIX_LENGTH]
 * characters, capped at [MAX_PREFIX_LENGTH] characters.
 */
object SearchTextNormalizer {

    /** Shortest word the index keeps a prefix for; also the minimum searchable length. */
    const val MIN_PREFIX_LENGTH = 3

    /** Longest prefix the sync script stores per word. */
    private const val MAX_PREFIX_LENGTH = 24

    private val nonAlphanumeric = Regex("[^a-z0-9\\s]")
    private val whitespace = Regex("\\s+")

    fun normalize(raw: String): String =
        raw.lowercase()
            .replace("&", " and ")
            .replace(nonAlphanumeric, " ")
            .replace(whitespace, " ")
            .trim()

    /**
     * The Firestore `whereArrayContains` key for [raw]: the first normalized word long
     * enough to appear in `searchPrefixes`, capped at [MAX_PREFIX_LENGTH]. Returns null
     * when the input has no searchable word (blank or all words shorter than
     * [MIN_PREFIX_LENGTH]).
     */
    fun queryPrefix(raw: String): String? =
        normalize(raw)
            .split(" ")
            .firstOrNull { it.length >= MIN_PREFIX_LENGTH }
            ?.take(MAX_PREFIX_LENGTH)
}
