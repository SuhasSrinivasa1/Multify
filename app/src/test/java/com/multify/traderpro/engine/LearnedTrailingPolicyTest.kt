package com.multify.traderpro.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LearnedTrailingPolicyTest {
    @Test fun learnedAverageIsTheLongArmThreshold() {
        assertEquals(2.98, LearnedTrailingPolicy.thresholdPct(2.98, 100.0, 1.0), 0.0001)
        assertFalse(LearnedTrailingPolicy.shouldArm(100.0, 102.97, 2.98))
        assertTrue(LearnedTrailingPolicy.shouldArm(100.0, 102.98, 2.98))
    }

    @Test fun movingAverageCanRaiseOrLowerAnUnarmedThreshold() {
        assertTrue(LearnedTrailingPolicy.shouldArm(100.0, 102.50, 2.0))
        assertFalse(LearnedTrailingPolicy.shouldArm(100.0, 102.50, 3.0))
    }

    @Test fun armedLongStopsNeverLoosen() {
        val first = LearnedTrailingPolicy.ratchetStop(100.0, 103.0, 1.0, null)
        val second = LearnedTrailingPolicy.ratchetStop(100.0, 104.0, 1.0, first)
        val third = LearnedTrailingPolicy.ratchetStop(100.0, 103.2, 1.0, second)
        assertTrue(second >= first)
        assertEquals(second, third, 0.0001)
    }

    @Test fun noLearnedAverageUsesConservativeFallback() {
        assertTrue(LearnedTrailingPolicy.thresholdPct(null, 100.0, 1.0) >= 0.60)
    }
}
