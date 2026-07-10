package com.sephylon.foodfitscan.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.sephylon.foodfitscan.domain.model.NutritionDisplayOption
import com.sephylon.foodfitscan.domain.model.SearchCountry
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Verifies the real [PreferenceRepositoryImpl] nutrition-display and search-country
 * behaviour against a temporary file-backed DataStore (no Android runtime, no internet).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PreferenceRepositoryImplTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private lateinit var testScope: TestScope
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var repository: PreferenceRepositoryImpl

    @Before
    fun setup() {
        testScope = TestScope(UnconfinedTestDispatcher())
        dataStore = PreferenceDataStoreFactory.create(
            scope = testScope.backgroundScope,
            produceFile = { File(tmpFolder.root, "test.preferences_pb") },
        )
        repository = PreferenceRepositoryImpl(dataStore)
    }

    @Test
    fun `nutrition fields default to DEFAULT_KEYS when nothing is saved`() = testScope.runTest {
        val fields = repository.observeSelectedNutritionFields().first()
        assertEquals(NutritionDisplayOption.DEFAULT_KEYS, fields)
    }

    @Test
    fun `saved nutrition fields persist and are read back`() = testScope.runTest {
        repository.saveSelectedNutritionFields(setOf("protein", "carbohydrates"))

        val fields = repository.observeSelectedNutritionFields().first()
        assertEquals(setOf("protein", "carbohydrates"), fields)
    }

    @Test
    fun `saving an empty set falls back to default fields`() = testScope.runTest {
        repository.saveSelectedNutritionFields(setOf("protein"))
        repository.saveSelectedNutritionFields(emptySet())

        val fields = repository.observeSelectedNutritionFields().first()
        assertEquals(NutritionDisplayOption.DEFAULT_KEYS, fields)
    }

    @Test
    fun `saving nutrition fields does not affect food preferences`() = testScope.runTest {
        // Display-only setting must not leak into scoring-related preferences.
        repository.saveSelectedNutritionFields(setOf("fiber"))

        val prefs = repository.getUserPreferences().first()
        assertEquals(emptySet<String>(), prefs.allergensToAvoid)
        assertEquals(false, prefs.avoidUltraProcessed)
    }

    // ── Search country ──────────────────────────────────────────────────────

    @Test
    fun `search country is null until the user picks one`() = testScope.runTest {
        assertNull(repository.observeSearchCountry().first())
    }

    @Test
    fun `saved search country persists and is read back`() = testScope.runTest {
        repository.saveSearchCountry(SearchCountry.SOUTH_KOREA)

        assertEquals(SearchCountry.SOUTH_KOREA, repository.observeSearchCountry().first())
    }

    @Test
    fun `saving All persists as an explicit choice rather than as no choice`() = testScope.runTest {
        repository.saveSearchCountry(SearchCountry.JAPAN)
        repository.saveSearchCountry(SearchCountry.ALL)

        assertEquals(SearchCountry.ALL, repository.observeSearchCountry().first())
    }
}
