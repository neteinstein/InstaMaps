package org.neteinstein.instamaps.core.instagramauth.domain

import app.cash.turbine.test
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private class FakeInstagramAuthRepository(
    initialCookie: String? = null,
) : InstagramAuthRepository {
    private val isAuthenticated = MutableStateFlow(initialCookie != null)
    private var cookie: String? = initialCookie
    var lastSaved: String? = null
    var clearCallCount: Int = 0

    override fun observeIsAuthenticated(): Flow<Boolean> = isAuthenticated

    override suspend fun getCookieHeader(): String? = cookie

    override suspend fun saveSession(cookieHeader: String) {
        lastSaved = cookieHeader
        cookie = cookieHeader
        isAuthenticated.value = true
    }

    override suspend fun clearSession() {
        clearCallCount++
        cookie = null
        isAuthenticated.value = false
    }
}

class ObserveInstagramAuthStateUseCaseTest {
    @Test
    fun `emits the value currently held by the repository`() =
        runTest {
            val repository = FakeInstagramAuthRepository(initialCookie = "sessionid=abc123")
            val useCase = ObserveInstagramAuthStateUseCase(repository)

            useCase().test {
                assertEquals(true, awaitItem())
            }
        }

    @Test
    fun `emits false when nothing has been saved`() =
        runTest {
            val repository = FakeInstagramAuthRepository(initialCookie = null)
            val useCase = ObserveInstagramAuthStateUseCase(repository)

            useCase().test {
                assertEquals(false, awaitItem())
            }
        }
}

class SaveInstagramSessionUseCaseTest {
    @Test
    fun `delegates to the repository`() =
        runTest {
            val repository = FakeInstagramAuthRepository()
            val useCase = SaveInstagramSessionUseCase(repository)

            useCase("sessionid=abc123")

            assertEquals("sessionid=abc123", repository.lastSaved)
        }
}

class ClearInstagramSessionUseCaseTest {
    @Test
    fun `delegates to the repository`() =
        runTest {
            val repository = FakeInstagramAuthRepository(initialCookie = "sessionid=abc123")
            val useCase = ClearInstagramSessionUseCase(repository)

            useCase()

            assertEquals(1, repository.clearCallCount)
            assertNull(repository.getCookieHeader())
        }
}
