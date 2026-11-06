package com.multify.traderpro.engine

import com.multify.traderpro.data.network.QuotePayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CircuitAwareBuyPolicyTest {
    private val now = 1_700_000_000_000L
    @Test fun reproducedGrowwCircuitBreachCannotBeSent() {
        val quote = QuotePayload(lastPrice = 233.26, offerPrice = 233.30,
            upperCircuitLimit = 233.38, lowerCircuitLimit = 155.60)
        val choice = CircuitAwareBuyPolicy.decide(quote, now - 200, now)
        assertEquals(233.38, choice.upperCircuit, 0.001)
        assertTrue(choice.limitPrice <= 233.38)
        assertTrue(choice.limitPrice >= 233.30)
        assertTrue(choice.limitPrice < 239.09)
    }
    @Test fun respectsSlippageAndBrokerTick() {
        val choice = CircuitAwareBuyPolicy.decide(QuotePayload(
            lastPrice = 210.0, offerPrice = 210.05,
            upperCircuitLimit = 220.0), now - 100, now)
        assertTrue(choice.limitPrice >= 210.05)
        assertTrue(choice.limitPrice <= 210.05 * 1.0015 + 1e-7)
        assertEquals(kotlin.math.round(choice.limitPrice * 20.0), choice.limitPrice * 20.0, 0.00001)
    }
    @Test fun failsClosedForStaleOrMissingCircuit() {
        val quote = QuotePayload(lastPrice = 233.26, offerPrice = 233.30, upperCircuitLimit = 233.38)
        val stale = runCatching { CircuitAwareBuyPolicy.decide(quote, now - 9_000, now) }.exceptionOrNull()
        assertEquals("BUY_QUOTE_STALE", stale?.message)
        val missing = runCatching { CircuitAwareBuyPolicy.decide(
            quote.copy(upperCircuitLimit = null), now - 100, now) }.exceptionOrNull()
        assertEquals("BUY_UPPER_CIRCUIT_UNKNOWN", missing?.message)
    }
    @Test fun noMarketablePriceIfAskExceedsCircuit() {
        val quote = QuotePayload(lastPrice = 233.26, offerPrice = 233.55, upperCircuitLimit = 233.38)
        assertEquals("BUY_OFFER_ABOVE_CIRCUIT",
            runCatching { CircuitAwareBuyPolicy.decide(quote, now, now) }.exceptionOrNull()?.message)
    }
    @Test fun commonStopAndTrailingRemainUnchanged() {
        assertEquals(95.0, HardStopLossPolicy.stopPrice(100.0), 0.0001)
        assertEquals(0.55, SteppedTrailingPolicy.ARM_TRIGGER_PCT, 0.0)
        assertEquals(0.50, SteppedTrailingPolicy.INITIAL_STOP_GAIN_PCT, 0.0)
    }
}
