package org.neteinstein.instamaps.core.update.domain

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.neteinstein.instamaps.core.common.AppError
import java.io.File

private class FakeUpdateRepository(
    private val checkResult: Result<UpdateCheckResult> = Result.success(UpdateCheckResult.UpToDate("1.0.0")),
    private val downloadResult: Result<File> = Result.success(File("/tmp/InstaMaps.apk")),
    private val clearResult: Result<Unit> = Result.success(Unit),
) : UpdateRepository {
    var downloadedUpdate: AppUpdate? = null
    var clearDownloadedUpdateCallCount = 0

    override suspend fun checkForUpdate(): Result<UpdateCheckResult> = checkResult

    override suspend fun downloadUpdate(update: AppUpdate): Result<File> {
        downloadedUpdate = update
        return downloadResult
    }

    override suspend fun clearDownloadedUpdate(): Result<Unit> {
        clearDownloadedUpdateCallCount++
        return clearResult
    }
}

class CheckForUpdateUseCaseTest {
    @Test
    fun `returns the update-available result from the repository`() =
        runTest {
            val update = AppUpdate(versionName = "1.0.17", apkDownloadUrl = "https://example.com/app.apk")
            val repository = FakeUpdateRepository(checkResult = Result.success(UpdateCheckResult.UpdateAvailable(update)))
            val useCase = CheckForUpdateUseCase(repository)

            val result = useCase()

            assertEquals(UpdateCheckResult.UpdateAvailable(update), result.getOrNull())
        }

    @Test
    fun `returns the up-to-date result from the repository`() =
        runTest {
            val repository = FakeUpdateRepository(checkResult = Result.success(UpdateCheckResult.UpToDate("1.0.16")))
            val useCase = CheckForUpdateUseCase(repository)

            val result = useCase()

            assertEquals(UpdateCheckResult.UpToDate("1.0.16"), result.getOrNull())
        }

    @Test
    fun `propagates repository failure unchanged`() =
        runTest {
            val error = AppError.Network("GitHub API error 500")
            val repository = FakeUpdateRepository(checkResult = Result.failure(error))
            val useCase = CheckForUpdateUseCase(repository)

            val result = useCase()

            assertTrue(result.exceptionOrNull() is AppError.Network)
        }
}

class DownloadAppUpdateUseCaseTest {
    @Test
    fun `delegates to the repository with the given update`() =
        runTest {
            val repository = FakeUpdateRepository()
            val useCase = DownloadAppUpdateUseCase(repository)
            val update = AppUpdate(versionName = "1.0.17", apkDownloadUrl = "https://example.com/app.apk")

            useCase(update)

            assertEquals(update, repository.downloadedUpdate)
        }

    @Test
    fun `returns the downloaded file from the repository`() =
        runTest {
            val apkFile = File("/tmp/InstaMaps-1.0.17.apk")
            val repository = FakeUpdateRepository(downloadResult = Result.success(apkFile))
            val useCase = DownloadAppUpdateUseCase(repository)

            val result = useCase(AppUpdate(versionName = "1.0.17", apkDownloadUrl = "https://example.com/app.apk"))

            assertEquals(apkFile, result.getOrNull())
        }

    @Test
    fun `propagates repository failure unchanged`() =
        runTest {
            val error = AppError.Network("APK download failed with HTTP 404")
            val repository = FakeUpdateRepository(downloadResult = Result.failure(error))
            val useCase = DownloadAppUpdateUseCase(repository)

            val result = useCase(AppUpdate(versionName = "1.0.17", apkDownloadUrl = "https://example.com/app.apk"))

            assertTrue(result.exceptionOrNull() is AppError.Network)
        }
}

class ClearDownloadedUpdateUseCaseTest {
    @Test
    fun `delegates to the repository`() =
        runTest {
            val repository = FakeUpdateRepository()
            val useCase = ClearDownloadedUpdateUseCase(repository)

            useCase()

            assertEquals(1, repository.clearDownloadedUpdateCallCount)
        }

    @Test
    fun `returns success from the repository`() =
        runTest {
            val repository = FakeUpdateRepository()
            val useCase = ClearDownloadedUpdateUseCase(repository)

            val result = useCase()

            assertTrue(result.isSuccess)
        }

    @Test
    fun `propagates repository failure unchanged`() =
        runTest {
            val error = AppError.Unknown(cause = RuntimeException("disk error"))
            val repository = FakeUpdateRepository(clearResult = Result.failure(error))
            val useCase = ClearDownloadedUpdateUseCase(repository)

            val result = useCase()

            assertTrue(result.exceptionOrNull() is AppError.Unknown)
        }
}
