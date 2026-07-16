package org.neteinstein.instamaps.core.common

import org.junit.Assert.assertThrows
import org.junit.Test

class LatLngTest {
    @Test
    fun `accepts boundary values`() {
        LatLng(latitude = 90.0, longitude = 180.0)
        LatLng(latitude = -90.0, longitude = -180.0)
    }

    @Test
    fun `rejects out-of-range latitude`() {
        assertThrows(IllegalArgumentException::class.java) {
            LatLng(latitude = 90.1, longitude = 0.0)
        }
    }

    @Test
    fun `rejects out-of-range longitude`() {
        assertThrows(IllegalArgumentException::class.java) {
            LatLng(latitude = 0.0, longitude = 180.1)
        }
    }
}
