package com.multify.traderpro.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LearnedTrailingPolicyTest {
    @Test fun multifyMfeAverageUsesPointFivePercentMinimum() {
        assertEquals(0.50, LearnedTrailingPolicy.dynamicArmPct(0.33), 0.0001)
        assertEquals(0.82, LearnedTrailingPolicy.dynamicArmPct(0.82), 0.0001)
        assertEquals(0.50, LearnedTrailingPolicy.thresholdPct(0.33, 100.0, 1.0), 0.0001)
    }

    @Test fun quarterRupeeHighWaterStepControlsContinuousRatchets() {
        assertTrue(LearnedTrailingPolicy.shouldRatchet(false, 0.0, 100.50))
        assertFalse(LearnedTrailingPolicy.shouldRatchet(true, 100.50, 100.74))
        assertTrue(LearnedTrailingPolicy.shouldRatchet(true, 100.50, 100.75))
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

    @Test fun profitableMovesTightenAndProtectAtLeastHalfTheGain() {
        val stop = LearnedTrailingPolicy.ratchetStop(100.0, 102.0, 0.20, null)
        assertTrue(stop >= 101.0)
        assertTrue(LearnedTrailingPolicy.trailDistance(102.0, 0.20, 100.0) <= 1.0)
    }

    @Test fun twoRupeeHighWaterAdvanceRaisesStopByAtLeastOneRupee() {
        val priorStop = 101.50
        val next = LearnedTrailingPolicy.ratchetStop(100.0, 104.0, 10.0, priorStop, 102.0)
        assertTrue(next >= 102.50)
    }

    @Test fun protectedFloorCoversEstimatedCostsPlusBuffer() {
        assertTrue(LearnedTrailingPolicy.netBreakEvenFloor(100.0) > 100.12)
    }

    @Test fun dynamicGateUsesRequestedMultifyMfePercentOfInvestedAmount() {
        val entry = 1859.63
        val qty = 26
        val invested = entry * qty
        assertEquals(invested * 0.005, LearnedTrailingPolicy.minimumNetProfitRupees(entry, qty, 0.50), 0.0001)
        assertEquals(invested * 0.012, LearnedTrailingPolicy.minimumNetProfitRupees(entry, qty, 1.20), 0.0001)
    }

    @Test fun tatvaOldTinyGainStillDoesNotReachHalfPercentNetGate() {
        val entry = 1859.63
        val qty = 26
        assertFalse(LearnedTrailingPolicy.profitGateReached(entry, 1869.90, qty, 0.50))
        assertFalse(LearnedTrailingPolicy.profitGateReached(entry, 1861.71, qty, 0.50))
    }

    @Test fun minimumProfitExitPriceCoversDynamicNetThresholdAfterEstimatedCosts() {
        val entry = 1859.63
        val qty = 26
        val floor = LearnedTrailingPolicy.minimumExitPriceForNetProfitPct(entry, 0.75)
        val minimum = LearnedTrailingPolicy.minimumNetProfitRupees(entry, qty, 0.75)
        assertTrue(LearnedTrailingPolicy.estimatedNetProfit(entry, floor, qty) + 0.01 >= minimum)
        assertTrue(floor > entry * 1.0075)
    }

    @Test fun ratchetCanBeFlooredAtDynamicMultifyMfePrice() {
        val entry = 100.0
        val floor = LearnedTrailingPolicy.minimumExitPriceForNetProfitPct(entry, 0.80)
        val stop = LearnedTrailingPolicy.ratchetStop(
            entryPrice = entry,
            highWaterPrice = 103.0,
            atr = 0.20,
            currentStop = null,
            minimumProtectedStop = floor
        )
        assertTrue(stop >= floor)
    }

    @Test fun noLearnedAverageUsesConservativeFallback() {
        assertTrue(LearnedTrailingPolicy.thresholdPct(null, 100.0, 1.0) >= 0.60)
    }
}
