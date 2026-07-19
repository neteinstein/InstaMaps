package org.neteinstein.instamaps.core.common

import android.util.Log
import kotlinx.coroutines.CancellationException

/**
 * Runs [block] and wraps the outcome in a [Result], mapping any unexpected exception to an
 * [AppError] via [mapError]. Structured-concurrency cancellation is always rethrown rather than
 * captured as a failure, per the standard coroutines contract - swallowing it would break
 * cooperative cancellation of the calling scope.
 *
 * Every caught exception is logged here (with its full stack trace) before being mapped: this is
 * the single choke point every repository failure passes through, so it's the cheapest place to
 * guarantee the real cause is never lost even though [mapError] typically distills it down to a
 * short, fixed, user-facing [AppError.message].
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
        Log.e("InstaMaps", "safeCall caught an unexpected failure", throwable)
        Result.failure(mapError(throwable))
    }
