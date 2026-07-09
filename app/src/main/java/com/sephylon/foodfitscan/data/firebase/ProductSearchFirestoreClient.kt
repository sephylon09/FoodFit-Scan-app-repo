package com.sephylon.foodfitscan.data.firebase

import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Thin, testable boundary around Cloud Firestore for the product-name search index.
 * Keeps Firebase types out of the repository/domain/UI layers.
 */
interface ProductSearchFirestoreClient {
    /**
     * Reads up to [limit] documents from `product_search_index` whose `searchPrefixes`
     * array contains [prefix]. Throws [IOException] for connectivity problems so callers
     * can map them to a network-error state.
     */
    suspend fun searchByPrefix(prefix: String, limit: Int): List<FirebaseProductSearchDto>
}

/**
 * Firestore-backed implementation. The app only ever READS from [COLLECTION]; it never
 * writes. The default [FirebaseFirestore] instance is initialized automatically by the
 * Firebase SDK from `app/google-services.json` (no manual init, no service-account key in
 * the app).
 *
 * Suggested Firestore security rules — apply these in the Firebase console, NOT from the
 * app:
 * ```
 * rules_version = '2';
 * service cloud.firestore {
 *   match /databases/{database}/documents {
 *     match /product_search_index/{doc} {
 *       allow read: if true;    // public read of the lightweight search index
 *       allow write: if false;  // clients never write; only the trusted sync job does
 *     }
 *   }
 * }
 * ```
 */
internal class FirestoreProductSearchClient(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
) : ProductSearchFirestoreClient {

    override suspend fun searchByPrefix(prefix: String, limit: Int): List<FirebaseProductSearchDto> {
        val snapshot = firestore.collection(COLLECTION)
            .whereArrayContains(FIELD_SEARCH_PREFIXES, prefix)
            .limit(limit.toLong())
            .get()
            .awaitResult()

        return snapshot.documents.map { doc ->
            FirebaseProductSearchDto(
                barcode = doc.getString(FIELD_BARCODE),
                name = doc.getString(FIELD_NAME),
                brand = doc.getString(FIELD_BRAND),
                imageUrl = doc.getString(FIELD_IMAGE_URL),
                searchName = doc.getString(FIELD_SEARCH_NAME),
                categoriesCount = (doc.get(FIELD_CATEGORIES_TAGS) as? List<*>)?.size ?: 0,
            )
        }
    }

    companion object {
        const val COLLECTION = "product_search_index"
        private const val FIELD_SEARCH_PREFIXES = "searchPrefixes"
        private const val FIELD_BARCODE = "barcode"
        private const val FIELD_NAME = "name"
        private const val FIELD_BRAND = "brand"
        private const val FIELD_IMAGE_URL = "imageUrl"
        private const val FIELD_SEARCH_NAME = "searchName"
        private const val FIELD_CATEGORIES_TAGS = "categoriesTags"
    }
}

/**
 * Suspends until this Play Services [Task] completes. Firestore "offline / unreachable"
 * failures are re-thrown as [IOException] so the repository can surface a network error;
 * all other failures propagate unchanged.
 */
private suspend fun <T> Task<T>.awaitResult(): T =
    suspendCancellableCoroutine { cont ->
        addOnSuccessListener { result -> cont.resume(result) }
        addOnFailureListener { error ->
            cont.resumeWithException(error.asConnectivityIOExceptionOrSelf())
        }
    }

private fun Throwable.asConnectivityIOExceptionOrSelf(): Throwable {
    if (this is FirebaseFirestoreException &&
        (code == FirebaseFirestoreException.Code.UNAVAILABLE ||
            code == FirebaseFirestoreException.Code.DEADLINE_EXCEEDED)
    ) {
        return IOException(message ?: "Firestore unavailable", this)
    }
    return this
}
