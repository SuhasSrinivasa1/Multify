package com.multify.traderpro.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForecastTargetMaturityPolicyTest {
    @Test fun twoEarlyOutcomesCannotOverrideRollingMultifyMfeBenchmark() {
        assertFalse(ForecastTargetMaturityPolicy.isMature(2, 2, 2))
        assertFalse(ForecastTargetMaturityPolicy.isMature(5, 2, 5))
        assertFalse(ForecastTargetMaturityPolicy.isMature(5, 3, 2))
    }

    @Test fun targetEvidenceNeedsFiveSamplesAcrossThreeDaysAndThreeSymbols() {
        assertTrue(ForecastTargetMaturityPolicy.isMature(5, 3, 3))
        assertTrue(ForecastTargetMaturityPolicy.isMature(12, 5, 8))
    }
}
