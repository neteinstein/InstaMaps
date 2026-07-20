package org.neteinstein.instamaps.core.instagramauth.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.cash.turbine.test
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.io.File

/** Reversible fake so tests can assert the repository never persists a plaintext cookie. */
private class FakeInstagramSessionCipher : InstagramSessionCipher {
    var failDecryption: Boolean = false

    override fun encrypt(plainText: String): String = "encrypted:$plainText"

    override fun decrypt(cipherText: String): String? {
        if (failDecryption) return null
        return cipherText.removePrefix("encrypted:")
    }
}

/**
 * Exercises the DataStore Preferences plumbing (encrypt-before-write, blank/clear semantics,
 * self-healing on an undecryptable cookie) against a real file-backed
 * [androidx.datastore.core.DataStore] on a plain JVM, with a fake [InstagramSessionCipher] -
 * mirrors `core:settings`'s `DataStoreAppSettingsRepositoryTest`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EncryptedInstagramAuthRepositoryTest {
    private lateinit var file: File
    private lateinit var cipher: FakeInstagramSessionCipher
    private lateinit var repository: EncryptedInstagramAuthRepository

    @Before
    fun setUp() {
        file = File.createTempFile("instagram_auth_test", ".preferences_pb")
        file.delete()
        val dataStore =
            PreferenceDataStoreFactory.create(
                scope = CoroutineScope(UnconfinedTestDispatcher()),
                produceFile = { file },
            )
        cipher = FakeInstagramSessionCipher()
        repository = EncryptedInstagramAuthRepository(dataStore, cipher)
    }

    @After
    fun tearDown() {
        file.delete()
    }

    @Test
    fun `observeIsAuthenticated emits false before anything is saved`() =
        runTest {
            repository.observeIsAuthenticated().test {
                assertEquals(false, awaitItem())
            }
        }

    @Test
    fun `getCookieHeader returns null before anything is saved`() =
        runTest {
            assertNull(repository.getCookieHeader())
        }

    @Test
    fun `saveSession persists an encrypted cookie`() =
        runTest {
            repository.saveSession("sessionid=abc123")

            repository.observeIsAuthenticated().test {
                assertEquals(true, awaitItem())
            }
            assertEquals("sessionid=abc123", repository.getCookieHeader())
        }

    @Test
    fun `saveSession trims the cookie header`() =
        runTest {
            repository.saveSession("  sessionid=abc123  ")

            assertEquals("sessionid=abc123", repository.getCookieHeader())
        }

    @Test
    fun `saveSession with a blank value clears any previously saved session`() =
        runTest {
            repository.saveSession("sessionid=abc123")
            repository.saveSession("   ")

            assertNull(repository.getCookieHeader())
            repository.observeIsAuthenticated().test {
                assertEquals(false, awaitItem())
            }
        }

    @Test
    fun `clearSession removes the saved session`() =
        runTest {
            repository.saveSession("sessionid=abc123")
            repository.clearSession()

            assertNull(repository.getCookieHeader())
            repository.observeIsAuthenticated().test {
                assertEquals(false, awaitItem())
            }
        }

    @Test
    fun `getCookieHeader self-heals by clearing the session when decryption fails`() =
        runTest {
            repository.saveSession("sessionid=abc123")
            cipher.failDecryption = true

            assertNull(repository.getCookieHeader())

            // The dead entry should now be cleared, so a later successful decrypt still reports
            // "logged out" rather than resurrecting stale state.
            cipher.failDecryption = false
            assertNull(repository.getCookieHeader())
        }

    @Test
    fun `observeIsAuthenticated is false when the saved cookie can't be decrypted`() =
        runTest {
            repository.saveSession("sessionid=abc123")
            cipher.failDecryption = true

            repository.observeIsAuthenticated().test {
                assertEquals(false, awaitItem())
            }
        }
}
