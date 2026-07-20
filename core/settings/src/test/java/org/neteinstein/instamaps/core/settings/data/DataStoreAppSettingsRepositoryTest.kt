package org.neteinstein.instamaps.core.settings.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Exercises the DataStore Preferences plumbing itself (trim/blank/persist semantics) against a
 * real file-backed [androidx.datastore.core.DataStore] on a plain JVM - no Robolectric/Android
 * framework needed, unlike the SDK-glue repositories elsewhere in this codebase.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DataStoreAppSettingsRepositoryTest {
    private lateinit var file: File
    private lateinit var repository: DataStoreAppSettingsRepository

    @Before
    fun setUp() {
        file = File.createTempFile("app_settings_test", ".preferences_pb")
        file.delete()
        val dataStore =
            PreferenceDataStoreFactory.create(
                scope = kotlinx.coroutines.CoroutineScope(UnconfinedTestDispatcher()),
                produceFile = { file },
            )
        repository = DataStoreAppSettingsRepository(dataStore)
    }

    @After
    fun tearDown() {
        file.delete()
    }

    @Test
    fun `observeGeminiApiKey emits null before anything is saved`() =
        runTest {
            repository.observeGeminiApiKey().test {
                assertEquals(null, awaitItem())
            }
        }

    @Test
    fun `saveGeminiApiKey persists a trimmed key`() =
        runTest {
            repository.saveGeminiApiKey("  AIzaSyExample  ")

            repository.observeGeminiApiKey().test {
                assertEquals("AIzaSyExample", awaitItem())
            }
        }

    @Test
    fun `saveGeminiApiKey with a blank value clears a previously saved key`() =
        runTest {
            repository.saveGeminiApiKey("AIzaSyExample")
            repository.saveGeminiApiKey("   ")

            repository.observeGeminiApiKey().test {
                assertEquals(null, awaitItem())
            }
        }
}
