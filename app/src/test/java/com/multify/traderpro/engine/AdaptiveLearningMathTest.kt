package com.multify.traderpro.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class AdaptiveLearningMathTest {
    @Test fun fullLongMoveRetracementTargetsOriginalEntry() {
        assertEquals(100.0, AdaptiveLearningMath.shortTarget(110.0, 100.0, 110.0, 1.0), 0.0001)
    }

    @Test fun shortRetracementNeverExceedsHundredPercent() {
        assertEquals(1.0, AdaptiveLearningMath.shortRetracementFraction(listOf(1.2, 1.0)), 0.0001)
    }

    @Test fun rollingMeanUsesObservedValuesWithoutAveragingDownSemantics() {
        assertEquals(2.0, AdaptiveLearningMath.rollingMean(listOf(1.0, 2.0, 3.0), 0.0), 0.0001)
        assertEquals(4.0, AdaptiveLearningMath.rollingMean(emptyList(), 4.0), 0.0001)
    }
}
