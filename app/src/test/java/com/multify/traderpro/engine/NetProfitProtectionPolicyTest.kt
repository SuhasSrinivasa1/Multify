package com.multify.traderpro.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetProfitProtectionPolicyTest {
    @Test fun artemisExampleDoesNotClaimNetProfitProtectionBeforeNetTargetAndBuffer() {
        val entry = 299.29
        val qty = 32
        val decision = NetProfitProtectionPolicy.evaluate(entry, qty, 301.05)
        assertFalse(decision.armed)
        assertTrue(decision.triggerPrice > 301.05)
        assertTrue(decision.netFloorPrice > entry)
    }

    @Test fun armsOnlyAfterCostAndSlippageAdjustedTriggerHasBeenReached() {
        val entry = 299.29
        val qty = 32
        val unarmed = NetProfitProtectionPolicy.evaluate(entry, qty, 301.05)
        val armed = NetProfitProtectionPolicy.evaluate(entry, qty, unarmed.triggerPrice + 0.10)
        assertTrue(armed.armed)
        assertEquals(0.50, armed.netTargetPct, 1e-9)
        assertTrue(GrowwDeliveryCostPolicy.estimatedNetProfit(entry, armed.netFloorPrice, qty) + 1e-6 >=
            entry * qty * NetProfitProtectionPolicy.MIN_NET_RETURN_PCT / 100.0)
        assertTrue(armed.triggerPrice > armed.netFloorPrice)
    }

    @Test fun ratchetUsesExistingTenBasisPointStepCadence() {
        val entry = 100.0
        val qty = 200
        val base = NetProfitProtectionPolicy.evaluate(entry, qty, 102.0)
        val higher = NetProfitProtectionPolicy.evaluate(entry, qty, 103.0)
        assertTrue(base.armed)
        assertTrue(higher.armed)
        assertTrue(higher.netTargetPct >= base.netTargetPct)
        assertTrue(higher.steps >= base.steps)
    }
}
