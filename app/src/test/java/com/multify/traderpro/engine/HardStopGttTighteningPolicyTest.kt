package com.multify.traderpro.engine

import org.junit.Assert.*
import org.junit.Test

class HardStopGttTighteningPolicyTest {
    private fun modify(
        trigger: Double?, mode: String = HoldingSmartOrderPolicy.MODE_HARD_STOP_GTT,
        status: String? = "ACTIVE", id: String? = "gtt-1"
    ) = HardStopGttTighteningPolicy.shouldModifyExisting(
        entryPrice = 100.0, smartOrderMode = mode, smartOrderId = id,
        smartOrderStatus = status, brokerTrigger = trigger
    )

    @Test fun existingFivePercentHardStopIsUpgradeCandidate() {
        assertTrue(modify(95.0))
        assertTrue(modify(97.5))
        assertTrue(modify(95.0, mode = HoldingSmartOrderPolicy.MODE_TRAILING_GTT))
        assertTrue(modify(95.0, status = "OPEN"))
    }

    @Test fun alreadyTightOrProfitTrailingStopsNeverLoosen() {
        assertFalse(modify(98.0))
        assertFalse(modify(98.001))
        assertFalse(modify(100.50, mode = HoldingSmartOrderPolicy.MODE_TRAILING_GTT))
        assertFalse(modify(101.0))
    }

    @Test fun unverifiedCancelledAndNonProtectedOrdersCannotBeSilentlyUpgraded() {
        assertFalse(modify(95.0, status = "TRIGGERED"))
        assertFalse(modify(95.0, status = "CANCELLED"))
        assertFalse(modify(95.0, status = null))
        assertFalse(modify(95.0, id = null))
        assertFalse(modify(95.0, mode = HoldingSmartOrderPolicy.MODE_RECOVERY_GTT))
        assertFalse(modify(null))
        assertFalse(modify(Double.NaN))
    }
}
