package com.multify.traderpro.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GrowwDeliveryCostPolicyTest {
    @Test fun recoveryPriceClearsOneHundredRupeesNetAfterEstimatedCharges() {
        val entry = 100.0
        val qty = 2_000
        val target = GrowwDeliveryCostPolicy.recoverySellPrice(entry, qty, 100.0)
        assertTrue(GrowwDeliveryCostPolicy.estimatedNetProfit(entry, target, qty) >= 100.0)
        assertTrue(GrowwDeliveryCostPolicy.estimatedNetProfit(entry, target - 0.05, qty) < 100.0)
    }

    @Test fun recoveryPriceUsesFivePaiseSafeTick() {
        val target = GrowwDeliveryCostPolicy.recoverySellPrice(739.10, 200, 100.0)
        val ticks = target / GrowwDeliveryCostPolicy.SAFE_TICK_RUPEES
        assertEquals(kotlin.math.round(ticks), ticks, 0.000001)
    }
}
