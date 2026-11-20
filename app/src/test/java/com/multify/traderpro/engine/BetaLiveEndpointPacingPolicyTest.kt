package com.multify.traderpro.engine

import org.junit.Assert.*
import org.junit.Test

class BetaLiveEndpointPacingPolicyTest {
    @Test fun quote429DoesNotSlowLtpDiscoveryLane() {
        val initial = BetaLiveEndpointPacingPolicy.initial(1_000L)
        val afterQuote429 = BetaLiveEndpointPacingPolicy.on429(
            initial, BetaLiveEndpointPacingPolicy.Endpoint.QUOTE, 2_000L)
        assertEquals(1_300L, BetaLiveEndpointPacingPolicy.gapMs(
            afterQuote429, BetaLiveEndpointPacingPolicy.Endpoint.LTP))
        assertEquals(2_600L, BetaLiveEndpointPacingPolicy.gapMs(
            afterQuote429, BetaLiveEndpointPacingPolicy.Endpoint.QUOTE))
    }

    @Test fun ltp429DoesNotSlowQuoteLane() {
        val initial = BetaLiveEndpointPacingPolicy.initial(1_000L)
        val afterLtp429 = BetaLiveEndpointPacingPolicy.on429(
            initial, BetaLiveEndpointPacingPolicy.Endpoint.LTP, 2_000L)
        assertEquals(2_600L, BetaLiveEndpointPacingPolicy.gapMs(
            afterLtp429, BetaLiveEndpointPacingPolicy.Endpoint.LTP))
        assertEquals(1_300L, BetaLiveEndpointPacingPolicy.gapMs(
            afterLtp429, BetaLiveEndpointPacingPolicy.Endpoint.QUOTE))
    }

    @Test fun cleanEndpointRecoversIndependently() {
        val initial = BetaLiveEndpointPacingPolicy.initial(1_000L)
        val slowed = BetaLiveEndpointPacingPolicy.on429(
            initial, BetaLiveEndpointPacingPolicy.Endpoint.QUOTE, 2_000L)
        val recovered = BetaLiveEndpointPacingPolicy.onSuccess(
            slowed, BetaLiveEndpointPacingPolicy.Endpoint.QUOTE, 122_001L)
        assertEquals(1_300L, BetaLiveEndpointPacingPolicy.gapMs(
            recovered, BetaLiveEndpointPacingPolicy.Endpoint.QUOTE))
        assertEquals(1_300L, BetaLiveEndpointPacingPolicy.gapMs(
            recovered, BetaLiveEndpointPacingPolicy.Endpoint.LTP))
    }
}
