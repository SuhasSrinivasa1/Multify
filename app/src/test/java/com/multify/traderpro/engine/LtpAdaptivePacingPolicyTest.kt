package com.multify.traderpro.engine

import org.junit.Assert.*
import org.junit.Test

class LtpAdaptivePacingPolicyTest {
    @Test fun rampsOnlyAfterCleanMinute() {
        var state = LtpAdaptivePacingPolicy.State(850L, 1_000L)
        assertEquals(state, LtpAdaptivePacingPolicy.onSuccess(state, 30_000L))
        state = LtpAdaptivePacingPolicy.onSuccess(state, 61_000L)
        assertEquals(750L, state.gapMs)
        repeat(8) { i -> state = LtpAdaptivePacingPolicy.onSuccess(state, 122_000L + i*61_000L) }
        assertEquals(220L, state.gapMs)
    }
    @Test fun broker429ReducesRateAndRequiresFreshCleanWindow() {
        val slower = LtpAdaptivePacingPolicy.on429(LtpAdaptivePacingPolicy.State(300L,1_000L),31_000L)
        assertEquals(850L, slower.gapMs)
        assertEquals(slower, LtpAdaptivePacingPolicy.onSuccess(slower,80_000L))
        assertEquals(750L, LtpAdaptivePacingPolicy.onSuccess(slower,91_000L).gapMs)
        assertEquals(3_000L,LtpAdaptivePacingPolicy.on429(LtpAdaptivePacingPolicy.State(2_500L,1_000L),10_000L).gapMs)
    }
}
