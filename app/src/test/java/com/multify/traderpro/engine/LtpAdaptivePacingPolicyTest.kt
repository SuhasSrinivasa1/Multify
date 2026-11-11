package com.multify.traderpro.engine

import org.junit.Assert.*
import org.junit.Test

class LtpAdaptivePacingPolicyTest {
    @Test fun rampsOnlyAfterFiveCleanMinutesAndNeverBelowOneSecond() {
        var state = LtpAdaptivePacingPolicy.State(1_250L, 1_000L)
        assertEquals(state, LtpAdaptivePacingPolicy.onSuccess(state, 300_999L))
        state = LtpAdaptivePacingPolicy.onSuccess(state, 301_000L)
        assertEquals(1_200L, state.gapMs)
        repeat(4) { i -> state = LtpAdaptivePacingPolicy.onSuccess(state, 601_000L + i * 300_000L) }
        assertEquals(1_000L, state.gapMs)
        state = LtpAdaptivePacingPolicy.onSuccess(state, 2_000_000L)
        assertEquals(1_000L, state.gapMs)
    }

    @Test fun broker429DoublesGapAndRequiresFreshFiveMinuteWindow() {
        val slower = LtpAdaptivePacingPolicy.on429(
            LtpAdaptivePacingPolicy.State(1_250L, 1_000L), 31_000L
        )
        assertEquals(2_500L, slower.gapMs)
        assertEquals(slower, LtpAdaptivePacingPolicy.onSuccess(slower, 330_999L))
        assertEquals(2_450L, LtpAdaptivePacingPolicy.onSuccess(slower, 331_000L).gapMs)
        assertEquals(5_000L, LtpAdaptivePacingPolicy.on429(
            LtpAdaptivePacingPolicy.State(2_500L, 1_000L), 10_000L
        ).gapMs)
    }
}
