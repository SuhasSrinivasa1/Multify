package com.multify.traderpro.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForecastAutoBuyPolicyTest {
    @Test fun entryUsesUpwardGttWithMarketChild() {
        assertTrue(ForecastAutoBuyPolicy.SMART_ORDER_TYPE == "GTT")
        assertTrue(ForecastAutoBuyPolicy.TRIGGER_DIRECTION == "UP")
        assertTrue(ForecastAutoBuyPolicy.CHILD_ORDER_TYPE == "MARKET")
    }

    @Test fun activeGttCanBeCancelledBeforeTrigger() {
        assertTrue(ForecastAutoBuyPolicy.canCancelBeforeTrigger("ACTIVE"))
        assertFalse(ForecastAutoBuyPolicy.canCancelBeforeTrigger("TRIGGERED"))
        assertTrue(ForecastAutoBuyPolicy.isCompleted("COMPLETED"))
    }

    @Test fun staleOrDisabledEntryMustCancel() {
        assertTrue(
            ForecastAutoBuyPolicy.shouldCancel(
                stateDate = "2026-10-20",
                today = "2026-10-21",
                autoBuyEnabled = true,
                safetyHalt = false,
                forecastActive = true,
                executionEligible = true,
                forceCancel = false
            )
        )
        assertTrue(
            ForecastAutoBuyPolicy.shouldCancel(
                stateDate = "2026-10-21",
                today = "2026-10-21",
                autoBuyEnabled = false,
                safetyHalt = false,
                forecastActive = true,
                executionEligible = true,
                forceCancel = false
            )
        )
        assertFalse(
            ForecastAutoBuyPolicy.shouldCancel(
                stateDate = "2026-10-21",
                today = "2026-10-21",
                autoBuyEnabled = true,
                safetyHalt = false,
                forecastActive = true,
                executionEligible = true,
                forceCancel = false
            )
        )
    }
}
