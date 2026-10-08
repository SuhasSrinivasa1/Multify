package com.multify.traderpro.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LearnedTrailingPolicyTest {
    @Test fun learnedAverageIsTheArmThreshold() {
        assertEquals(2.98, LearnedTrailingPolicy.thresholdPct(2.98, 100.0, 1.0), 0.0001)
        assertFalse(LearnedTrailingPolicy.shouldArm("LONG", 100.0, 102.97, 2.98))
        assertTrue(LearnedTrailingPolicy.shouldArm("LONG", 100.0, 102.98, 2.98))
        assertFalse(LearnedTrailingPolicy.shouldArm("SHORT", 100.0, 97.03, 2.98))
        assertTrue(LearnedTrailingPolicy.shouldArm("SHORT", 100.0, 97.02, 2.98))
    }

    @Test fun movingAverageCanRaiseOrLowerAnUnarmedThreshold() {
        assertTrue(LearnedTrailingPolicy.shouldArm("LONG", 100.0, 102.50, 2.0))
        assertFalse(LearnedTrailingPolicy.shouldArm("LONG", 100.0, 102.50, 3.0))
    }

    @Test fun armedStopsOnlyMoveInTheFavourableDirection() {
        val long1 = LearnedTrailingPolicy.ratchetStop("LONG", 100.0, 103.0, 1.0, null)
        val long2 = LearnedTrailingPolicy.ratchetStop("LONG", 100.0, 104.0, 1.0, long1)
        val long3 = LearnedTrailingPolicy.ratchetStop("LONG", 100.0, 103.2, 1.0, long2)
        assertTrue(long2 >= long1)
        assertEquals(long2, long3, 0.0001)

        val short1 = LearnedTrailingPolicy.ratchetStop("SHORT", 100.0, 97.0, 1.0, null)
        val short2 = LearnedTrailingPolicy.ratchetStop("SHORT", 100.0, 96.0, 1.0, short1)
        val short3 = LearnedTrailingPolicy.ratchetStop("SHORT", 100.0, 96.8, 1.0, short2)
        assertTrue(short2 <= short1)
        assertEquals(short2, short3, 0.0001)
    }

    @Test fun noLearnedAverageUsesConservativeFallback() {
        assertTrue(LearnedTrailingPolicy.thresholdPct(null, 100.0, 1.0) >= 0.60)
    }
}
