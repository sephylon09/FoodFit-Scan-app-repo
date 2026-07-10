package com.sephylon.foodfitscan.domain.model

/**
 * The country filter applied to product-name search results.
 *
 * [countryTag] is the Open Food Facts tag stored in each `product_search_index` document's
 * `countryTags` array; [regionCode] is the ISO 3166-1 alpha-2 region reported by the device
 * locale, used only to pick the initial selection. [ALL] disables country filtering and has
 * neither.
 */
enum class SearchCountry(
    val displayName: String,
    val countryTag: String?,
    val regionCode: String?,
) {
    ALL("All", null, null),
    SINGAPORE("Singapore", "en:singapore", "SG"),
    MALAYSIA("Malaysia", "en:malaysia", "MY"),
    INDONESIA("Indonesia", "en:indonesia", "ID"),
    THAILAND("Thailand", "en:thailand", "TH"),
    JAPAN("Japan", "en:japan", "JP"),
    SOUTH_KOREA("South Korea", "en:south-korea", "KR"),
    CHINA("China", "en:china", "CN"),
    TAIWAN("Taiwan", "en:taiwan", "TW"),
    HONG_KONG("Hong Kong", "en:hong-kong", "HK"),
    AUSTRALIA("Australia", "en:australia", "AU"),
    NEW_ZEALAND("New Zealand", "en:new-zealand", "NZ"),
    UNITED_STATES("United States", "en:united-states", "US"),
    UNITED_KINGDOM("United Kingdom", "en:united-kingdom", "GB"),
    INDIA("India", "en:india", "IN"),
    FRANCE("France", "en:france", "FR"),
    GERMANY("Germany", "en:germany", "DE"),
    ITALY("Italy", "en:italy", "IT");

    /** Stable identifier persisted in DataStore. */
    val key: String get() = name

    /**
     * Whether a search-index document belongs to this country. [ALL] matches everything,
     * including documents whose `countryTags` array is missing or empty.
     */
    fun matches(countryTags: List<String>): Boolean {
        val tag = countryTag ?: return true
        return countryTags.any { it.trim().equals(tag, ignoreCase = true) }
    }

    companion object {
        /** Every option, in dropdown order — [ALL] first, then the supported countries. */
        val OPTIONS: List<SearchCountry> = entries.toList()

        private val byRegionCode: Map<String, SearchCountry> = buildMap {
            // `entries` here would resolve to the map builder's entries, so qualify it.
            SearchCountry.entries.forEach { country ->
                country.regionCode?.let { put(it, country) }
            }
            // Some devices report the UK's legacy region code instead of the ISO "GB".
            put("UK", UNITED_KINGDOM)
        }

        private val byKey: Map<String, SearchCountry> = entries.associateBy { it.key }

        /**
         * Maps a device region (e.g. `"SG"`) to its search country. Unknown, malformed, and
         * absent regions fall back to [ALL] — nothing here reads location, only the locale.
         */
        fun fromRegionCode(regionCode: String?): SearchCountry {
            val code = regionCode?.trim()?.uppercase().orEmpty()
            return byRegionCode[code] ?: ALL
        }

        /** Resolves a persisted [key], or null when it is absent or no longer recognised. */
        fun fromKey(key: String?): SearchCountry? = key?.let { byKey[it] }
    }
}
