package com.multify.traderpro.engine

import org.junit.Assert.*
import org.junit.Test

class FixedExecutionPresentationPolicyTest {
    @Test fun successfulAndPartialBuysNeverOfferAnotherBuy() {
        assertTrue(FixedExecutionPresentationPolicy.blocksManualBuy(FixedExecutionPresentationPolicy.EXECUTED))
        assertTrue(FixedExecutionPresentationPolicy.blocksManualBuy(FixedExecutionPresentationPolicy.PARTIAL_FILL))
        assertEquals("BUY EXECUTED · DISABLED", FixedExecutionPresentationPolicy.buttonLabel(
            "ABC", "BUY_EXECUTED", FixedExecutionPresentationPolicy.EXECUTED,
            null, null, false, true, true, true, true, false))
        assertEquals("PARTIAL FILL · 4/35 · DISABLED", FixedExecutionPresentationPolicy.buttonLabel(
            "ABC", "BUY_EXECUTED", FixedExecutionPresentationPolicy.PARTIAL_FILL,
            4, 35, false, true, true, true, true, false))
    }

    @Test fun brokerAndCapacityBlocksAreVisibleAndNonExecutable() {
        assertEquals("EXTERNAL HOLDING · BUY BLOCKED", FixedExecutionPresentationPolicy.buttonLabel(
            "ABC", "ACTIVE", FixedExecutionPresentationPolicy.BLOCKED_EXTERNAL_HOLDING,
            null, null, false, true, true, true, true, false))
        assertEquals("MAX HOLDINGS · WAITING FOR SLOT", FixedExecutionPresentationPolicy.buttonLabel(
            "ABC", "ACTIVE", FixedExecutionPresentationPolicy.BLOCKED_MAX_HOLDINGS,
            null, null, false, true, true, true, true, false))
        assertFalse(FixedExecutionPresentationPolicy.canBuy(
            1L, true, "ACTIVE", true,
            FixedExecutionPresentationPolicy.BLOCKED_MAX_HOLDINGS, false, false))
        assertEquals("BROKER COOLDOWN · RETRY PENDING", FixedExecutionPresentationPolicy.buttonLabel(
            "ABC", "ACTIVE", FixedExecutionPresentationPolicy.BROKER_COOLDOWN,
            null, null, false, true, true, true, true, false))
    }

    @Test fun targetMarketAndEntryCutoffLabelsAreDistinct() {
        assertEquals("TARGET HIT · BUY disabled", FixedExecutionPresentationPolicy.buttonLabel(
            "ABC", "TARGET_HIT", FixedExecutionPresentationPolicy.NOT_ATTEMPTED,
            null, null, false, true, true, true, true, false))
        assertEquals("ENTRY CUTOFF REACHED · BUY disabled", FixedExecutionPresentationPolicy.buttonLabel(
            "ABC", "ACTIVE", FixedExecutionPresentationPolicy.NOT_ATTEMPTED,
            null, null, false, true, false, true, true, false))
        assertEquals("MARKET CLOSED · BUY disabled", FixedExecutionPresentationPolicy.buttonLabel(
            "ABC", "ACTIVE", FixedExecutionPresentationPolicy.NOT_ATTEMPTED,
            null, null, false, false, false, true, true, false))
    }

    @Test fun olderEventsAndOpenPositionsCannotBeBoughtAgain() {
        assertFalse(FixedExecutionPresentationPolicy.canBuy(
            1L, true, "ACTIVE", true,
            FixedExecutionPresentationPolicy.NOT_ATTEMPTED, true, false))
        assertFalse(FixedExecutionPresentationPolicy.canBuy(
            1L, true, "ACTIVE", true,
            FixedExecutionPresentationPolicy.NOT_ATTEMPTED, false, true))
    }
}
