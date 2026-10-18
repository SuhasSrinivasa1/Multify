package com.multify.traderpro.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LearnedTrailingPolicyTest {
    @Test fun trailDoesNotArmBeforePointFiveFivePercent() {
        assertNull(LearnedTrailingPolicy.desiredLockedPctForGain(0.54))
        assertFalse(LearnedTrailingPolicy.isArmed(100.49, 100.0))
    }

    @Test fun pointFiveFiveArmsAndLocksPointFive() {
        assertEquals(0.50, LearnedTrailingPolicy.desiredLockedPctForGain(0.55)!!, 0.0001)
        assertEquals(100.50, LearnedTrailingPolicy.stopPrice(100.0, 0.50), 0.0001)
        assertTrue(LearnedTrailingPolicy.isArmed(100.50, 100.0))
    }

    @Test fun staircaseMovesEveryPointOnePercent() {
        assertEquals(0.50, LearnedTrailingPolicy.desiredLockedPctForGain(0.649)!!, 0.0001)
        assertEquals(0.60, LearnedTrailingPolicy.desiredLockedPctForGain(0.65)!!, 0.0001)
        assertEquals(0.70, LearnedTrailingPolicy.desiredLockedPctForGain(0.75)!!, 0.0001)
        assertEquals(1.00, LearnedTrailingPolicy.desiredLockedPctForGain(1.05)!!, 0.0001)
        assertEquals(1.50, LearnedTrailingPolicy.desiredLockedPctForGain(1.55)!!, 0.0001)
    }

    @Test fun highWaterPriceMapsToSameStaircase() {
        assertEquals(0.50, LearnedTrailingPolicy.desiredLockedPct(100.0, 100.55)!!, 0.0001)
        assertEquals(0.60, LearnedTrailingPolicy.desiredLockedPct(100.0, 100.65)!!, 0.0001)
        assertEquals(0.70, LearnedTrailingPolicy.desiredLockedPct(100.0, 100.75)!!, 0.0001)
    }

    @Test fun ratchetOnlyMovesUp() {
        assertTrue(LearnedTrailingPolicy.shouldRatchet(100.0, 100.50, 0.60))
        assertFalse(LearnedTrailingPolicy.shouldRatchet(100.0, 100.60, 0.60))
        assertFalse(LearnedTrailingPolicy.shouldRatchet(100.0, 100.70, 0.60))
    }

    @Test fun lockedPercentIsRecoveredFromStop() {
        assertEquals(0.70, LearnedTrailingPolicy.lockedPct(100.0, 100.70)!!, 0.0001)
        assertNull(LearnedTrailingPolicy.lockedPct(100.0, 99.0))
    }

    @Test fun estimatedNetProfitStillAccountsForCashMarketCosts() {
        val net = LearnedTrailingPolicy.estimatedNetProfit(100.0, 101.0, 100)
        assertTrue(net > 80.0)
        assertTrue(net < 100.0)
    }
}
