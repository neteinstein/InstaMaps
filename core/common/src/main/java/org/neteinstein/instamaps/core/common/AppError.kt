package org.neteinstein.instamaps.core.common

/**
 * Domain-level error hierarchy shared by every layer/module. Repository implementations
 * catch SDK/library-specific exceptions (Retrofit, youtubedl-android, ML Kit, Places SDK...)
 * and translate them into one of these so the presentation layer never needs to know about
 * a specific SDK's exception types.
 */
sealed class AppError(override val message: String, override val cause: Throwable? = null) : Exception(message, cause) {
    class Network(message: String = "Network request failed", cause: Throwable? = null) : AppError(message, cause)

    class NotFound(message: String = "Nothing was found", cause: Throwable? = null) : AppError(message, cause)

    class InvalidInput(message: String, cause: Throwable? = null) : AppError(message, cause)

    class Parsing(message: String = "Failed to parse content", cause: Throwable? = null) : AppError(message, cause)

    class MissingConfiguration(message: String, cause: Throwable? = null) : AppError(message, cause)

    class PlatformUnavailable(message: String, cause: Throwable? = null) : AppError(message, cause)

    class Unknown(message: String = "Something went wrong", cause: Throwable? = null) : AppError(message, cause)
}
