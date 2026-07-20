package org.neteinstein.instamaps.core.common

import org.junit.Assert.assertEquals
import org.junit.Test

class AppErrorTest {
    @Test
    fun `describeOrDefault appends the cause message to the prefix`() {
        val throwable = RuntimeException("unsupported url")

        assertEquals("Video download failed: unsupported url", throwable.describeOrDefault("Video download failed"))
    }

    @Test
    fun `describeOrDefault falls back to the prefix alone when the message is null`() {
        val throwable = RuntimeException()

        assertEquals("Video download failed", throwable.describeOrDefault("Video download failed"))
    }

    @Test
    fun `describeOrDefault falls back to the prefix alone when the message is blank`() {
        val throwable = RuntimeException("   ")

        assertEquals("Video download failed", throwable.describeOrDefault("Video download failed"))
    }

    @Test
    fun `describeOrDefault uses only the last non-blank line of a multi-line message`() {
        val throwable = RuntimeException("Downloading webpage\n\nERROR: Unsupported URL\n")

        assertEquals("Video download failed: ERROR: Unsupported URL", throwable.describeOrDefault("Video download failed"))
    }

    @Test
    fun `describeOrDefault trims surrounding whitespace from the detail`() {
        val throwable = RuntimeException("   padded reason   ")

        assertEquals("Video download failed: padded reason", throwable.describeOrDefault("Video download failed"))
    }
}
