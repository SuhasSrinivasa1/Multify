package com.multify.traderpro.engine

import com.multify.traderpro.data.network.QuotePayload
import org.junit.Assert.*
import org.junit.Test

class FixedQuoteMarketabilityPresentationPolicyTest {
    private val now=1_700_000_000_000L
    @Test fun noSellOfferAtVerifiedBrokerUpperBandIsOnlyAnObservation() {
        val r=FixedQuoteMarketabilityPresentationPolicy.assess(
            QuotePayload(lastPrice=241.52,upperCircuitLimit=241.52),now-500,now)
        assertEquals("UPPER_BAND_NO_SELL_OFFER",r.state)
    }
    @Test fun noOfferWithUnknownCircuitNeverInventsBand() {
        val r=FixedQuoteMarketabilityPresentationPolicy.assess(
            QuotePayload(lastPrice=298.05),now-400,now)
        assertEquals("NO_SELL_OFFER_OBSERVED",r.state)
        assertNull(r.brokerUpperCircuit)
    }
    @Test fun staleClientQuoteDoesNotClaimCircuitStillLocked() {
        val r=FixedQuoteMarketabilityPresentationPolicy.assess(
            QuotePayload(lastPrice=241.52,upperCircuitLimit=241.52),now-9_000,now)
        assertEquals("STALE_CLIENT_QUOTE",r.state)
    }
    @Test fun actualBestOfferDoesNotPromiseFill() {
        val r=FixedQuoteMarketabilityPresentationPolicy.assess(
            QuotePayload(lastPrice=240.0,upperCircuitLimit=241.52,offerPrice=240.2),
            now-100,now)
        assertEquals("SELL_OFFER_OBSERVED_NOT_FILL_GUARANTEE",r.state)
        assertEquals(240.2,r.bestObservedOffer!!,0.001)
    }
    @Test fun missingQuoteIsUnknown() {
        assertEquals("NO_RECENT_QUOTE",
            FixedQuoteMarketabilityPresentationPolicy.assess(null,null,now).state)
    }
}
