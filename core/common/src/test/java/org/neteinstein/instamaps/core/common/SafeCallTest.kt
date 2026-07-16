package org.neteinstein.instamaps.core.common

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SafeCallTest {
    @Test
    fun `returns success when block completes normally`() =
        runTest {
            val result = safeCall { 42 }

            assertEquals(42, result.getOrNull())
        }

    @Test
    fun `maps thrown exception to AppError via mapError`() =
        runTest {
            val cause = IllegalStateException("boom")

            val result =
                safeCall(mapError = { AppError.Network(cause = it) }) {
                    throw cause
                }

            val error = result.exceptionOrNull()
            assertTrue(error is AppError.Network)
            assertEquals(cause, error?.cause)
        }

    @Test
    fun `uses default Unknown mapper when none supplied`() =
        runTest {
            val result = safeCall<Int> { throw RuntimeException("bad") }

            assertTrue(result.exceptionOrNull() is AppError.Unknown)
        }

    @Test
    fun `rethrows CancellationException instead of wrapping it`() =
        runTest {
            try {
                safeCall<Unit> { throw CancellationException("cancelled") }
                fail("Expected CancellationException to propagate")
            } catch (expected: CancellationException) {
                // expected: cooperative cancellation must never be swallowed as a Result.failure
            }
        }
}
