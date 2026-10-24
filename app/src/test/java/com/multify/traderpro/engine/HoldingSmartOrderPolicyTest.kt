package com.multify.traderpro.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HoldingSmartOrderPolicyTest {
    @Test fun recoveryGttWaitsForRiseWhenStockIsBelowTarget() {
        assertEquals("UP", HoldingSmartOrderPolicy.recoveryDirection(98.0, 101.0))
    }

    @Test fun recoveryGttProtectsTargetOnDeclineWhenAlreadyAboveTarget() {
        assertEquals("DOWN", HoldingSmartOrderPolicy.recoveryDirection(102.0, 101.0))
    }

    @Test fun onlyKnownProtectiveGttCountsAsServerProtection() {
        assertTrue(HoldingSmartOrderPolicy.isServerProtected(HoldingSmartOrderPolicy.MODE_HARD_STOP_GTT, "gtt_0"))
        assertTrue(HoldingSmartOrderPolicy.isServerProtected(HoldingSmartOrderPolicy.MODE_TRAILING_GTT, "gtt_1"))
        assertTrue(HoldingSmartOrderPolicy.isServerProtected(HoldingSmartOrderPolicy.MODE_RECOVERY_GTT, "gtt_2"))
        assertFalse(HoldingSmartOrderPolicy.isServerProtected(HoldingSmartOrderPolicy.MODE_NONE, "gtt_3"))
        assertFalse(HoldingSmartOrderPolicy.isServerProtected(HoldingSmartOrderPolicy.MODE_TRAILING_GTT, null))
    }
}
