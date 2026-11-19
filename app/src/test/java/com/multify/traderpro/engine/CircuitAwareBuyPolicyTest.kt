package com.multify.traderpro.engine

import com.multify.traderpro.data.network.QuoteDepthLevel
import com.multify.traderpro.data.network.QuoteMarketDepth
import com.multify.traderpro.data.network.QuotePayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CircuitAwareBuyPolicyTest {
    private val now = 1_700_000_000_000L

    @Test fun reproducedGrowwCircuitBreachUsesBestOfferNotCircuitCeiling() {
        val quote = QuotePayload(
            lastPrice = 228.47,
            offerPrice = 228.50,
            upperCircuitLimit = 233.38,
            lowerCircuitLimit = 155.60
        )
        val choice = CircuitAwareBuyPolicy.decide(quote, now - 200, now)

        assertEquals(233.38, choice.upperCircuit, 0.001)
        assertEquals(228.50, choice.bestOffer, 0.001)
        assertEquals(228.50, choice.limitPrice, 0.001)
        assertTrue(choice.limitPrice < choice.upperCircuit)
        assertTrue(choice.limitPrice < 239.09)
    }

    @Test fun choosesLowestSellDepthOfferWithoutAddingPremium() {
        val quote = QuotePayload(
            lastPrice = 210.0,
            offerPrice = 210.10,
            upperCircuitLimit = 220.0,
            depth = QuoteMarketDepth(
                sell = listOf(
                    QuoteDepthLevel(price = 210.08, quantity = 20.0),
                    QuoteDepthLevel(price = 210.05, quantity = 10.0),
                    QuoteDepthLevel(price = 210.15, quantity = 50.0)
                )
            )
        )
        val choice = CircuitAwareBuyPolicy.decide(quote, now - 100, now)

        assertEquals(210.05, choice.bestOffer, 0.001)
        assertEquals(210.05, choice.limitPrice, 0.001)
        assertEquals(0.0, CircuitAwareBuyPolicy.MAX_BUY_SLIPPAGE_BPS, 0.0)
    }

    @Test fun failsClosedWhenNoExecutableOfferIsAvailable() {
        val quote = QuotePayload(
            lastPrice = 210.0,
            upperCircuitLimit = 220.0
        )
        val error = runCatching {
            CircuitAwareBuyPolicy.decide(quote, now - 100, now)
        }.exceptionOrNull()
        assertEquals("BUY_BEST_OFFER_UNKNOWN", error?.message)
    }

    @Test fun failsClosedForStaleOrMissingCircuit() {
        val quote = QuotePayload(
            lastPrice = 233.26,
            offerPrice = 233.30,
            upperCircuitLimit = 233.38
        )
        val stale = runCatching {
            CircuitAwareBuyPolicy.decide(quote, now - 9_000, now)
        }.exceptionOrNull()
        assertEquals("BUY_QUOTE_STALE", stale?.message)

        val missing = runCatching {
            CircuitAwareBuyPolicy.decide(
                quote.copy(upperCircuitLimit = null), now - 100, now
            )
        }.exceptionOrNull()
        assertEquals("BUY_UPPER_CIRCUIT_UNKNOWN", missing?.message)
    }

    @Test fun rejectsOfferOutsideCircuitBand() {
        val above = QuotePayload(
            lastPrice = 233.26,
            offerPrice = 233.55,
            upperCircuitLimit = 233.38,
            lowerCircuitLimit = 155.60
        )
        assertEquals(
            "BUY_OFFER_ABOVE_CIRCUIT",
            runCatching {
                CircuitAwareBuyPolicy.decide(above, now, now)
            }.exceptionOrNull()?.message
        )

        val below = QuotePayload(
            lastPrice = 220.10,
            offerPrice = 219.99,
            upperCircuitLimit = 233.38,
            lowerCircuitLimit = 220.00
        )
        assertEquals(
            "BUY_OFFER_BELOW_CIRCUIT",
            runCatching {
                CircuitAwareBuyPolicy.decide(below, now, now)
            }.exceptionOrNull()?.message
        )
    }

    @Test fun commonStopNowMinusTwoAndProfitableTrailingRemainUnchanged() {
        assertEquals(98.0, HardStopLossPolicy.stopPrice(100.0), 0.0001)
        assertEquals(0.55, SteppedTrailingPolicy.ARM_TRIGGER_PCT, 0.0)
        assertEquals(0.50, SteppedTrailingPolicy.INITIAL_STOP_GAIN_PCT, 0.0)
    }
}
