package com.sephylon.foodfitscan.data.repository

import com.sephylon.foodfitscan.data.firebase.FirebaseProductSearchDto
import com.sephylon.foodfitscan.data.firebase.ProductSearchFirestoreClient
import com.sephylon.foodfitscan.domain.model.ProductSearchResult
import com.sephylon.foodfitscan.domain.model.SearchCountry
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class ProductSearchRepositoryImplTest {

    @Test
    fun `maps firestore dtos to product search items`() = runTest {
        val client = FakeClient(
            result = listOf(
                FirebaseProductSearchDto(
                    barcode = " 5000159407236 ",
                    name = " KitKat ",
                    brand = " Nestle ",
                    imageUrl = "https://img/kitkat.jpg",
                    searchName = "kitkat nestle",
                ),
            ),
        )
        val repo = ProductSearchRepositoryImpl(client)

        val result = repo.searchByName("kitkat")

        assertTrue(result is ProductSearchResult.Success)
        val item = (result as ProductSearchResult.Success).items.single()
        assertEquals("5000159407236", item.barcode)
        assertEquals("KitKat", item.name)
        assertEquals("Nestle", item.brand)
        assertEquals("https://img/kitkat.jpg", item.imageUrl)
    }

    @Test
    fun `blank brand and image become null`() = runTest {
        val client = FakeClient(
            result = listOf(
                FirebaseProductSearchDto(
                    barcode = "111",
                    name = "Plain Rice",
                    brand = "  ",
                    imageUrl = "",
                    searchName = "plain rice",
                    // Categories keep this displayable despite the blank brand/image.
                    categoriesCount = 2,
                ),
            ),
        )
        val repo = ProductSearchRepositoryImpl(client)

        val item = (repo.searchByName("rice") as ProductSearchResult.Success).items.single()

        assertEquals(null, item.brand)
        assertEquals(null, item.imageUrl)
    }

    @Test
    fun `drops documents missing barcode or name`() = runTest {
        val client = FakeClient(
            result = listOf(
                FirebaseProductSearchDto(barcode = null, name = "No Barcode", brand = "Acme", searchName = "no barcode"),
                FirebaseProductSearchDto(barcode = "222", name = null, brand = "Acme", searchName = "no name"),
                FirebaseProductSearchDto(barcode = "333", name = "Valid", brand = "Acme", searchName = "valid acme"),
            ),
        )
        val repo = ProductSearchRepositoryImpl(client)

        val items = (repo.searchByName("valid") as ProductSearchResult.Success).items

        assertEquals(1, items.size)
        assertEquals("333", items.single().barcode)
    }

    @Test
    fun `hides products with no image no brand and no categories`() = runTest {
        val client = FakeClient(
            result = listOf(
                FirebaseProductSearchDto(
                    barcode = "1",
                    name = "Nutella Lookalike",
                    searchName = "nutella lookalike",
                ),
                FirebaseProductSearchDto(
                    barcode = "2",
                    name = "Nutella",
                    brand = "Ferrero",
                    searchName = "nutella ferrero",
                ),
            ),
        )
        val repo = ProductSearchRepositoryImpl(client)

        val items = (repo.searchByName("nutella") as ProductSearchResult.Success).items

        assertEquals(listOf("2"), items.map { it.barcode })
    }

    @Test
    fun `hides junk names even when other data is present`() = runTest {
        val client = FakeClient(
            result = listOf(
                FirebaseProductSearchDto(
                    barcode = "1",
                    name = "test",
                    brand = "Brand",
                    imageUrl = "https://img/1.jpg",
                    searchName = "test brand",
                ),
                FirebaseProductSearchDto(
                    barcode = "2",
                    name = "1234567890128",
                    brand = "Brand",
                    imageUrl = "https://img/2.jpg",
                    searchName = "1234567890128 brand",
                ),
            ),
        )
        val repo = ProductSearchRepositoryImpl(client)

        assertEquals(ProductSearchResult.Empty, repo.searchByName("test"))
    }

    @Test
    fun `ranks image tier first then brand tier then weak tier`() = runTest {
        val brandOnly = FirebaseProductSearchDto(
            barcode = "1",
            name = "Nutella",
            brand = "Ferrero",
            searchName = "nutella ferrero",
        )
        val withImage = FirebaseProductSearchDto(
            barcode = "2",
            name = "Nutella Hazelnut Spread",
            brand = "Ferrero",
            imageUrl = "https://img/nutella.jpg",
            searchName = "nutella hazelnut spread ferrero",
        )
        val categoriesOnly = FirebaseProductSearchDto(
            barcode = "3",
            name = "Nutella Style Spread",
            searchName = "nutella style spread",
            categoriesCount = 3,
        )
        val client = FakeClient(result = listOf(brandOnly, withImage, categoriesOnly))
        val repo = ProductSearchRepositoryImpl(client)

        val items = (repo.searchByName("nutella") as ProductSearchResult.Success).items

        assertEquals(listOf("2", "1", "3"), items.map { it.barcode })
    }

    @Test
    fun `within a tier ranks contains-match before non-match and shorter names first`() = runTest {
        val longMatch = FirebaseProductSearchDto(
            barcode = "1",
            name = "Nutella Hazelnut Spread Extra Large Jar",
            brand = "Ferrero",
            searchName = "nutella hazelnut spread extra large jar ferrero",
        )
        val shortMatch = FirebaseProductSearchDto(
            barcode = "2",
            name = "Nutella",
            brand = "Ferrero",
            searchName = "nutella ferrero",
        )
        val nonMatch = FirebaseProductSearchDto(
            barcode = "3",
            name = "Hazelnut Cookies",
            brand = "Other",
            searchName = "hazelnut cookies other",
        )
        val client = FakeClient(result = listOf(longMatch, shortMatch, nonMatch))
        val repo = ProductSearchRepositoryImpl(client)

        val items = (repo.searchByName("nutella") as ProductSearchResult.Success).items

        assertEquals(listOf("2", "1", "3"), items.map { it.barcode })
    }

    @Test
    fun `empty firestore result returns Empty`() = runTest {
        val repo = ProductSearchRepositoryImpl(FakeClient(result = emptyList()))

        assertEquals(ProductSearchResult.Empty, repo.searchByName("nutella"))
    }

    @Test
    fun `query without searchable term returns Empty without querying firestore`() = runTest {
        val client = FakeClient(result = emptyList())
        val repo = ProductSearchRepositoryImpl(client)

        val result = repo.searchByName("a b")

        assertEquals(ProductSearchResult.Empty, result)
        assertEquals(0, client.callCount)
    }

    @Test
    fun `io exception maps to NetworkError`() = runTest {
        val repo = ProductSearchRepositoryImpl(FakeClient(error = IOException("offline")))

        val result = repo.searchByName("nutella")

        assertTrue(result is ProductSearchResult.NetworkError)
    }

    @Test
    fun `unexpected exception maps to UnknownError`() = runTest {
        val repo = ProductSearchRepositoryImpl(FakeClient(error = IllegalStateException("boom")))

        val result = repo.searchByName("nutella")

        assertTrue(result is ProductSearchResult.UnknownError)
    }

    @Test
    fun `uses first searchable word as the prefix key`() = runTest {
        val client = FakeClient(result = emptyList())
        val repo = ProductSearchRepositoryImpl(client)

        repo.searchByName("Nutella Hazelnut Spread")

        assertEquals("nutella", client.lastPrefix)
    }

    @Test
    fun `fetches an enlarged candidate pool so filtering keeps lists full`() = runTest {
        val client = FakeClient(result = emptyList())
        val repo = ProductSearchRepositoryImpl(client)

        repo.searchByName("nutella")

        assertEquals(40, client.lastLimit)
    }

    // ── Country filtering ───────────────────────────────────────────────────

    @Test
    fun `a selected country keeps only results carrying its country tag`() = runTest {
        val client = FakeClient(result = listOf(sgProduct, jpProduct, sgAndMyProduct))
        val repo = ProductSearchRepositoryImpl(client)

        val items = (repo.searchByName("milk", SearchCountry.SINGAPORE) as ProductSearchResult.Success).items

        assertEquals(listOf("sg", "sg-my"), items.map { it.barcode }.sorted())
    }

    @Test
    fun `All does not filter by country`() = runTest {
        val client = FakeClient(result = listOf(sgProduct, jpProduct, sgAndMyProduct))
        val repo = ProductSearchRepositoryImpl(client)

        val items = (repo.searchByName("milk", SearchCountry.ALL) as ProductSearchResult.Success).items

        assertEquals(3, items.size)
    }

    @Test
    fun `country defaults to All when the caller omits it`() = runTest {
        val client = FakeClient(result = listOf(sgProduct, jpProduct))
        val repo = ProductSearchRepositoryImpl(client)

        val items = (repo.searchByName("milk") as ProductSearchResult.Success).items

        assertEquals(2, items.size)
    }

    @Test
    fun `a selected country hides documents with no country tags`() = runTest {
        val untagged = FirebaseProductSearchDto(
            barcode = "none",
            name = "Milk Drink",
            brand = "Acme",
            searchName = "milk drink acme",
        )
        val client = FakeClient(result = listOf(untagged))
        val repo = ProductSearchRepositoryImpl(client)

        assertEquals(ProductSearchResult.Empty, repo.searchByName("milk", SearchCountry.JAPAN))
    }

    @Test
    fun `filtering out every result for a country returns Empty`() = runTest {
        val client = FakeClient(result = listOf(sgProduct))
        val repo = ProductSearchRepositoryImpl(client)

        assertEquals(ProductSearchResult.Empty, repo.searchByName("milk", SearchCountry.ITALY))
    }

    @Test
    fun `ranking still applies within a country-filtered result set`() = runTest {
        val tags = listOf("en:japan")
        val brandOnly = FirebaseProductSearchDto(
            barcode = "1",
            name = "Milk Tea",
            brand = "Kirin",
            searchName = "milk tea kirin",
            countryTags = tags,
        )
        val withImage = FirebaseProductSearchDto(
            barcode = "2",
            name = "Milk Tea Original Bottle",
            brand = "Kirin",
            imageUrl = "https://img/2.jpg",
            searchName = "milk tea original bottle kirin",
            countryTags = tags,
        )
        val categoriesOnly = FirebaseProductSearchDto(
            barcode = "3",
            name = "Milk Pudding",
            searchName = "milk pudding",
            categoriesCount = 2,
            countryTags = tags,
        )
        val client = FakeClient(result = listOf(brandOnly, withImage, categoriesOnly))
        val repo = ProductSearchRepositoryImpl(client)

        val items = (repo.searchByName("milk", SearchCountry.JAPAN) as ProductSearchResult.Success).items

        assertEquals(listOf("2", "1", "3"), items.map { it.barcode })
    }

    @Test
    fun `country searches request a larger candidate pool than unfiltered ones`() = runTest {
        val client = FakeClient(result = emptyList())
        val repo = ProductSearchRepositoryImpl(client)

        repo.searchByName("nutella", SearchCountry.SINGAPORE)

        assertEquals(100, client.lastLimit)
    }

    private val sgProduct = FirebaseProductSearchDto(
        barcode = "sg",
        name = "Milk Powder",
        brand = "Acme",
        searchName = "milk powder acme",
        countryTags = listOf("en:singapore"),
    )

    private val jpProduct = FirebaseProductSearchDto(
        barcode = "jp",
        name = "Milk Coffee",
        brand = "Acme",
        searchName = "milk coffee acme",
        countryTags = listOf("en:japan"),
    )

    private val sgAndMyProduct = FirebaseProductSearchDto(
        barcode = "sg-my",
        name = "Milk Biscuits",
        brand = "Acme",
        searchName = "milk biscuits acme",
        countryTags = listOf("en:malaysia", "en:singapore"),
    )

    private class FakeClient(
        private val result: List<FirebaseProductSearchDto> = emptyList(),
        private val error: Throwable? = null,
    ) : ProductSearchFirestoreClient {
        var callCount = 0
        var lastPrefix: String? = null
        var lastLimit: Int? = null

        override suspend fun searchByPrefix(prefix: String, limit: Int): List<FirebaseProductSearchDto> {
            callCount++
            lastPrefix = prefix
            lastLimit = limit
            error?.let { throw it }
            return result
        }
    }
}
