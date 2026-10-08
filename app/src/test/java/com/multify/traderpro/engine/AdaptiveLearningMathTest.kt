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

    @Test fun averagingLevelsAreEveryTwoPercentFromAnchor() {
        assertEquals(98.0, AdaptiveLearningMath.nextAverageTrigger(100.0, 0), 0.0001)
        assertEquals(96.0, AdaptiveLearningMath.nextAverageTrigger(100.0, 1), 0.0001)
        assertEquals(90.0, AdaptiveLearningMath.nextAverageTrigger(100.0, 4), 0.0001)
    }
}
