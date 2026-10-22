package com.multify.traderpro.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class FixedScanPerformancePolicyTest {
    @Test
    fun highEndEightCoreDeviceUsesSixWayConcurrency() {
        assertEquals(
            6,
            FixedScanPerformancePolicy.evaluationConcurrency(8)
        )
    }

    @Test
    fun smallerDeviceFallsBackToFourWayConcurrency() {
        assertEquals(
            4,
            FixedScanPerformancePolicy.evaluationConcurrency(6)
        )
    }
}
