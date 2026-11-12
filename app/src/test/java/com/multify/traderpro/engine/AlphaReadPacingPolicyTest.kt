package com.multify.traderpro.engine

import org.junit.Assert.*
import org.junit.Test

class AlphaReadPacingPolicyTest {
    @Test fun onlyAlphaReadEndpointsArePaced() {
        val p = AlphaReadPacingPolicy
        assertEquals(p.Lane.QUOTE, p.lane("GET", "/v1/live-data/quote"))
        assertEquals(p.Lane.ACCOUNT_READ, p.lane("GET", "/v1/holdings/user"))
        assertEquals(p.Lane.ACCOUNT_READ, p.lane("GET", "/v1/positions/user"))
        assertEquals(p.Lane.ACCOUNT_READ, p.lane("GET", "/v1/order-advance/list"))
        assertEquals(p.Lane.ACCOUNT_READ, p.lane("GET", "/v1/order-advance/status/CASH/GTT/internal/123"))
        assertNull(p.lane("GET", "/v1/live-data/ltp"))
        assertNull(p.lane("GET", "/v1/historical/candles"))
        assertNull(p.lane("GET", "/v1/order/detail/123")) // post-fill order evidence stays immediate
        assertNull(p.lane("POST", "/v1/order/create")) // BUY unaffected
        assertNull(p.lane("POST", "/v1/order-advance/create")) // protective GTT create unaffected
        assertNull(p.lane("PUT", "/v1/order-advance/modify")) // protective GTT modify unaffected
    }

    @Test fun quoteStartsAreSpacedAnd429SlowsTheLane() {
        val p = AlphaReadPacingPolicy
        val lane = p.Lane.QUOTE
        var s = p.State(p.startGapMs(lane), 1000L)
        assertEquals(0L, p.waitMs(1000L, 0L, s.gapMs))
        assertEquals(850L, p.waitMs(1000L, 1000L, s.gapMs))
        assertEquals(0L, p.waitMs(1850L, 1000L, s.gapMs))
        s = p.on429(lane, s, 1850L)
        assertEquals(1800L, s.gapMs)
        assertEquals(s, p.onSuccess(lane, s, 5000L))
        assertEquals(1750L, p.onSuccess(lane, s, 121850L).gapMs)
    }

    @Test fun accountReadsShareOneLaneAndProtectedWritesRemainExempt() {
        val p = AlphaReadPacingPolicy
        val lane = p.Lane.ACCOUNT_READ
        val s = p.on429(lane, p.State(p.startGapMs(lane), 1000L), 1500L)
        assertEquals(2000L, s.gapMs)
        assertEquals(1200L, p.waitMs(2300L, 1500L, s.gapMs))
        assertEquals(0L, p.waitMs(3500L, 1500L, s.gapMs))
    }
}
