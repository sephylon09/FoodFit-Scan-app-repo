package com.sephylon.foodfitscan.data.repository

import com.sephylon.foodfitscan.data.firebase.FirebaseProductSearchDto
import com.sephylon.foodfitscan.data.firebase.ProductSearchFirestoreClient
import com.sephylon.foodfitscan.domain.model.ProductSearchItem
import com.sephylon.foodfitscan.domain.model.ProductSearchResult
import com.sephylon.foodfitscan.domain.model.SearchCountry
import com.sephylon.foodfitscan.domain.repository.ProductSearchRepository
import com.sephylon.foodfitscan.domain.util.SearchResultQuality
import com.sephylon.foodfitscan.domain.util.SearchTextNormalizer
import java.io.IOException

/**
 * Queries Firestore for a single normalized prefix (the first searchable word) via
 * `whereArrayContains("searchPrefixes", prefix)`, then curates the results client-side.
 *
 * Country filtering is applied app-side rather than as a second `whereArrayContains` on
 * `countryTags`, because Firestore rejects two array-contains filters in one query. To keep
 * country-filtered lists usefully full, a country query fetches a larger candidate pool
 * ([COUNTRY_SEARCH_LIMIT]) than an unfiltered one ([SEARCH_LIMIT]).
 *
 * Filtering ([SearchResultQuality.isDisplayable]) hides junk/near-empty index entries —
 * placeholder names, barcode-as-name docs, and products with no image, no brand, and no
 * categories.
 *
 * Ranking, in order:
 *   1. display tier — products with an image, then image-less products with a brand,
 *      then the rest ([SearchResultQuality.displayTier])
 *   2. docs whose name/brand contain the full search text
 *   3. shorter product names first
 *   4. name alphabetically
 */
internal class ProductSearchRepositoryImpl(
    private val client: ProductSearchFirestoreClient,
) : ProductSearchRepository {

    override suspend fun searchByName(
        rawQuery: String,
        country: SearchCountry,
    ): ProductSearchResult {
        val prefix = SearchTextNormalizer.queryPrefix(rawQuery)
            ?: return ProductSearchResult.Empty
        val normalizedQuery = SearchTextNormalizer.normalize(rawQuery)

        return try {
            val items = client.searchByPrefix(prefix, candidateLimit(country))
                .filter { !it.barcode.isNullOrBlank() && !it.name.isNullOrBlank() }
                .filter { country.matches(it.countryTags) }
                .filter {
                    SearchResultQuality.isDisplayable(
                        name = it.name,
                        brand = it.brand,
                        imageUrl = it.imageUrl,
                        categoriesCount = it.categoriesCount,
                    )
                }
                .sortedWith(relevanceComparator(normalizedQuery))
                .map { it.toProductSearchItem() }

            if (items.isEmpty()) ProductSearchResult.Empty
            else ProductSearchResult.Success(items)
        } catch (e: IOException) {
            ProductSearchResult.NetworkError(e.message ?: "Network error")
        } catch (e: Exception) {
            ProductSearchResult.UnknownError(e.message ?: "Unknown error")
        }
    }

    private fun candidateLimit(country: SearchCountry): Int =
        if (country == SearchCountry.ALL) SEARCH_LIMIT else COUNTRY_SEARCH_LIMIT

    private fun relevanceComparator(normalizedQuery: String): Comparator<FirebaseProductSearchDto> =
        // display tier first (image > brand-only > weak), then matches-query (true sorts
        // before false via descending), then shorter name, then alphabetical.
        compareBy<FirebaseProductSearchDto> { SearchResultQuality.displayTier(it.brand, it.imageUrl) }
            .thenByDescending { matchesQuery(it, normalizedQuery) }
            .thenBy { it.name?.length ?: Int.MAX_VALUE }
            .thenBy { it.name?.lowercase() ?: "" }

    private fun matchesQuery(dto: FirebaseProductSearchDto, normalizedQuery: String): Boolean {
        if (normalizedQuery.isBlank()) return false
        val haystack = dto.searchName?.takeIf { it.isNotBlank() }
            ?: SearchTextNormalizer.normalize("${dto.name.orEmpty()} ${dto.brand.orEmpty()}")
        return haystack.contains(normalizedQuery)
    }

    private fun FirebaseProductSearchDto.toProductSearchItem() = ProductSearchItem(
        barcode = barcode!!.trim(),
        name = name!!.trim(),
        brand = brand?.trim()?.takeIf { it.isNotEmpty() },
        imageUrl = imageUrl?.trim()?.takeIf { it.isNotEmpty() },
    )

    companion object {
        // Fetch more than we expect to show: quality filtering removes weak docs, so a
        // larger candidate pool keeps result lists usefully full.
        private const val SEARCH_LIMIT = 40

        // Country filtering happens after the fetch, so it discards part of the pool as
        // well. Start from a bigger pool whenever a country is selected.
        private const val COUNTRY_SEARCH_LIMIT = 100
    }
}
