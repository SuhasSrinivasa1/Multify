package com.multify.traderpro.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LearnedTrailingPolicyTest {
    @Test fun realizedAverageIsTheLongArmThreshold() {
        assertEquals(0.33, LearnedTrailingPolicy.thresholdPct(0.33, 100.0, 1.0), 0.0001)
        assertFalse(LearnedTrailingPolicy.shouldArm(100.0, 100.32, 0.33))
        assertTrue(LearnedTrailingPolicy.shouldArm(100.0, 100.33, 0.33))
    }

    @Test fun oneRupeeHighWaterStepControlsRatchets() {
        assertTrue(LearnedTrailingPolicy.shouldRatchet(false, 0.0, 100.33))
        assertFalse(LearnedTrailingPolicy.shouldRatchet(true, 100.33, 101.32))
        assertTrue(LearnedTrailingPolicy.shouldRatchet(true, 100.33, 101.33))
    }

    @Test fun adaptiveDistanceUsesLargestProtectionDistance() {
        assertEquals(1.0, LearnedTrailingPolicy.trailDistance(100.0, 1.0), 0.0001)
        assertEquals(7.0, LearnedTrailingPolicy.trailDistance(2000.0, 2.0), 0.0001)
        assertEquals(2.8, LearnedTrailingPolicy.trailDistance(500.0, 4.0), 0.0001)
    }

    @Test fun armedLongStopsNeverLoosen() {
        val first = LearnedTrailingPolicy.ratchetStop(100.0, 101.0, 1.0, null)
        val second = LearnedTrailingPolicy.ratchetStop(100.0, 102.0, 1.0, first)
        val third = LearnedTrailingPolicy.ratchetStop(100.0, 101.2, 1.0, second)
        assertTrue(first > 100.0)
        assertTrue(second >= first)
        assertEquals(second, third, 0.0001)
    }

    @Test fun protectedFloorCoversEstimatedCostsPlusBuffer() {
        assertTrue(LearnedTrailingPolicy.netBreakEvenFloor(100.0) > 100.12)
    }

    @Test fun noLearnedAverageUsesConservativeFallback() {
        assertTrue(LearnedTrailingPolicy.thresholdPct(null, 100.0, 1.0) >= 0.60)
    }
}
