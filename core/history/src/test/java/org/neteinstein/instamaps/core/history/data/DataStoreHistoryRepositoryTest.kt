package org.neteinstein.instamaps.core.history.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.cash.turbine.test
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.neteinstein.instamaps.core.history.domain.HistoryLocation
import org.neteinstein.instamaps.core.history.domain.MAX_HISTORY_ENTRIES
import java.io.File

/**
 * Exercises the DataStore Preferences + JSON (de)serialization plumbing against a real
 * file-backed [androidx.datastore.core.DataStore] on a plain JVM - mirrors
 * `DataStoreAppSettingsRepositoryTest`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DataStoreHistoryRepositoryTest {
    private lateinit var file: File
    private lateinit var repository: DataStoreHistoryRepository

    @Before
    fun setUp() {
        file = File.createTempFile("history_test", ".preferences_pb")
        file.delete()
        val dataStore =
            PreferenceDataStoreFactory.create(
                scope = CoroutineScope(UnconfinedTestDispatcher()),
                produceFile = { file },
            )
        repository = DataStoreHistoryRepository(dataStore)
    }

    @After
    fun tearDown() {
        file.delete()
    }

    @Test
    fun `observeEntries emits an empty list before anything is recorded`() =
        runTest {
            repository.observeEntries().test {
                assertEquals(emptyList<Any>(), awaitItem())
            }
        }

    @Test
    fun `recordEntry persists a new entry with its url and locations`() =
        runTest {
            val locations = listOf(HistoryLocation(name = "Time Out Market", address = "Lisbon, Portugal"))

            repository.recordEntry(url = "https://instagram.com/reel/abc", locations = locations)

            repository.observeEntries().test {
                val entries = awaitItem()
                assertEquals(1, entries.size)
                assertEquals("https://instagram.com/reel/abc", entries.first().url)
                assertEquals(locations, entries.first().locations)
                assertTrue(entries.first().id.isNotBlank())
                assertTrue(entries.first().timestamp > 0L)
            }
        }

    @Test
    fun `recordEntry with no locations still persists the url`() =
        runTest {
            repository.recordEntry(url = "https://instagram.com/reel/not-found")

            repository.observeEntries().test {
                val entries = awaitItem()
                assertEquals(1, entries.size)
                assertEquals(emptyList<HistoryLocation>(), entries.first().locations)
            }
        }

    @Test
    fun `newest entry is listed first`() =
        runTest {
            repository.recordEntry(url = "https://instagram.com/reel/first")
            repository.recordEntry(url = "https://instagram.com/reel/second")

            repository.observeEntries().test {
                val entries = awaitItem()
                assertEquals(listOf("https://instagram.com/reel/second", "https://instagram.com/reel/first"), entries.map { it.url })
            }
        }

    @Test
    fun `list is capped at MAX_HISTORY_ENTRIES, dropping the oldest first`() =
        runTest {
            repeat(MAX_HISTORY_ENTRIES + 5) { index ->
                repository.recordEntry(url = "https://instagram.com/reel/$index")
            }

            repository.observeEntries().test {
                val entries = awaitItem()
                assertEquals(MAX_HISTORY_ENTRIES, entries.size)
                // Newest-first: entry 0 (the oldest) should have been dropped.
                assertEquals("https://instagram.com/reel/${MAX_HISTORY_ENTRIES + 4}", entries.first().url)
                assertTrue(entries.none { it.url == "https://instagram.com/reel/0" })
            }
        }
}
