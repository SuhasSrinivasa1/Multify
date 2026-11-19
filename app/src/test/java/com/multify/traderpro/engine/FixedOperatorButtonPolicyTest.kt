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
        assertEquals("BUY EXIDEIND · Verify offer", x.label)
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
    @Test fun noSellSideAtUpperBandMustBeExplainedNotCalledSessionEnded() {
        val x = FixedOperatorButtonPolicy.decide("RUNWALENTR", "ACTIVE",
            true, false, true, true, true,
            quoteMarketabilityState = "UPPER_BAND_NO_SELL_OFFER")
        assertFalse(x.enabled)
        assertEquals("No seller at upper band · BUY unavailable", x.label)
    }
    @Test fun validSellOfferAllowsNormalBuyButtonButStillUsesBrokerPreflight() {
        val x = FixedOperatorButtonPolicy.decide("LIBERTSHOE", "ACTIVE",
            true, false, true, true, true,
            quoteMarketabilityState = "SELL_OFFER_OBSERVED_NOT_FILL_GUARANTEE")
        assertTrue(x.enabled)
        assertEquals("BUY LIBERTSHOE · CNC", x.label)
    }
    @Test fun staleQuoteDoesNotBlockManualFreshBrokerCheck() {
        val x = FixedOperatorButtonPolicy.decide("GLOBUSSPR", "ACTIVE",
            true, false, true, true, true,
            quoteMarketabilityState = "STALE_CLIENT_QUOTE")
        assertTrue(x.enabled)
        assertEquals("BUY GLOBUSSPR · Verify offer", x.label)
    }
    @Test fun unknownNonactiveStatusMustNeverClaimSessionEnded() {
        val x = decision(status = "PENDING_RECONCILIATION")
        assertFalse(x.enabled)
        assertEquals("Fixed PENDING RECONCILIATION · BUY disabled", x.label)
    }
}
