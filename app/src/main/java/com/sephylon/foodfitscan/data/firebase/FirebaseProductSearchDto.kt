package com.sephylon.foodfitscan.data.firebase

/**
 * Lightweight projection of a `product_search_index` Firestore document. Fields are read
 * explicitly from the [com.google.firebase.firestore.DocumentSnapshot] (no reflection
 * mapping), so only the fields the app actually needs are represented here. [searchName]
 * is the pre-normalized name+brand written by the sync script and is used for client-side
 * relevance sorting.
 */
data class FirebaseProductSearchDto(
    val barcode: String? = null,
    val name: String? = null,
    val brand: String? = null,
    val imageUrl: String? = null,
    val searchName: String? = null,
    /** Size of the doc's `categoriesTags` array; used only as a display-quality signal. */
    val categoriesCount: Int = 0,
)
