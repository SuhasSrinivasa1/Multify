package com.multify.traderpro.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FixedOperatorButtonPolicyTest {
    private fun decision(
        status: String = "ACTIVE",
        older: Boolean = false,
        eligible: Boolean = true,
        held: Int? = null,
        total: Int? = 1,
        max: Int? = 2,
        market: Boolean = true,
        entry: Boolean = true,
        ready: Boolean = true
    ) = FixedOperatorButtonPolicy.decide(
        "EXIDEIND", status, eligible, older, market, entry, ready, held, total, max
    )

    @Test fun activeAndReadyRemainsActionable() {
        val x = decision()
        assertTrue(x.enabled)
        assertEquals("BUY EXIDEIND · CNC", x.label)
    }
    @Test fun boughtRecommendationIsNotCalledSessionEnded() {
        val x = decision(status = "BUY_EXECUTED")
        assertFalse(x.enabled)
        assertEquals("BUY EXECUTED · no repeat BUY", x.label)
    }
    @Test fun displayedQuantityIsBrokerOwnedOpenPositionNotRequestedSize() {
        val x = decision(status = "BUY_EXECUTED", held = 4)
        assertFalse(x.enabled)
        assertEquals("BUY EXECUTED · 4 shares held", x.label)
    }
    @Test fun ownExistingHoldingIsNotBuyable() {
        val x = decision(held = 7)
        assertFalse(x.enabled)
        assertEquals("Existing app holding · BUY blocked", x.label)
    }
    @Test fun fullHoldingCapacityBlocksOperatorButton() {
        val x = decision(total = 2, max = 2)
        assertFalse(x.enabled)
        assertEquals("Holding capacity 2/2 · BUY blocked", x.label)
    }
    @Test fun unknownHoldingCountDoesNotInventAFullPortfolio() {
        assertTrue(decision(total = null).enabled)
    }
    @Test fun targetHitAndPriorEventCannotBeBought() {
        assertEquals("TARGET HIT · BUY disabled", decision(status = "TARGET_HIT").label)
        assertFalse(decision(older = true).enabled)
    }
    @Test fun actualEntryWindowAndBrokerReadinessRemainRequired() {
        assertFalse(decision(market = false).enabled)
        assertFalse(decision(entry = false).enabled)
        assertFalse(decision(ready = false).enabled)
        assertFalse(decision(eligible = false).enabled)
    }
    @Test fun unknownNonactiveStatusMustNeverClaimSessionEnded() {
        val x = decision(status = "PENDING_RECONCILIATION")
        assertFalse(x.enabled)
        assertEquals("Fixed PENDING RECONCILIATION · BUY disabled", x.label)
    }
}
