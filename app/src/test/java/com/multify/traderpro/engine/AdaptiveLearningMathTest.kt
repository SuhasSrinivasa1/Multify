package com.multify.traderpro.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class AdaptiveLearningMathTest {
    @Test fun realizedReturnPrefersRecordedMultifyReturnAndFallsBackToPrices() {
        assertEquals(-0.90, AdaptiveLearningMath.realizedReturnPct(-0.90, 440.95, 437.0)!!, 0.0001)
        assertEquals(1.0, AdaptiveLearningMath.realizedReturnPct(null, 100.0, 101.0)!!, 0.0001)
    }

    @Test fun rollingMeanIncludesLosses() {
        assertEquals(0.25, AdaptiveLearningMath.rollingMean(listOf(-1.0, 0.0, 1.0, 1.0), 0.0), 0.0001)
    }

    @Test fun rollingMeanUsesObservedLongValues() {
        assertEquals(2.0, AdaptiveLearningMath.rollingMean(listOf(1.0, 2.0, 3.0), 0.0), 0.0001)
        assertEquals(4.0, AdaptiveLearningMath.rollingMean(emptyList(), 4.0), 0.0001)
    }

    @Test fun robustLongStatisticsAreDeterministic() {
        val values = listOf(1.0, 2.0, 3.0, 4.0, 20.0)
        assertEquals(3.0, AdaptiveLearningMath.median(values, 0.0), 0.0001)
        assertEquals(6.0, AdaptiveLearningMath.trimmedMean(values, 0.0, 0.0), 0.0001)
        assertEquals(2.0, AdaptiveLearningMath.quantile(values, .25, 0.0), 0.0001)
        assertEquals(4.0, AdaptiveLearningMath.quantile(values, .75, 0.0), 0.0001)
        val ewma = AdaptiveLearningMath.ewma(listOf(1.0, 1.0, 5.0), 0.0, .5)
        assertEquals(3.0, ewma, 0.0001)
    }
}
