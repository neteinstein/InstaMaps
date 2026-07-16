package org.neteinstein.instamaps.core.common

import kotlinx.coroutines.CancellationException

/**
 * Runs [block] and wraps the outcome in a [Result], mapping any unexpected exception to an
 * [AppError] via [mapError]. Structured-concurrency cancellation is always rethrown rather than
 * captured as a failure, per the standard coroutines contract - swallowing it would break
 * cooperative cancellation of the calling scope.
 */
suspend inline fun <T> safeCall(
    mapError: (Throwable) -> AppError = { AppError.Unknown(cause = it) },
    block: suspend () -> T,
): Result<T> =
    try {
        Result.success(block())
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (throwable: Throwable) {
        Result.failure(mapError(throwable))
    }
