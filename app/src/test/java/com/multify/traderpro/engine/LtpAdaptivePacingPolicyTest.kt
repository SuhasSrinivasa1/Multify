package com.multify.traderpro.engine

import org.junit.Assert.*
import org.junit.Test

class LtpAdaptivePacingPolicyTest {
    @Test fun rampsOnlyAfterTwoCleanMinutesAndNeverBelowConservativeFloor() {
        var state = LtpAdaptivePacingPolicy.State(1300L, 1000L)
        assertEquals(state, LtpAdaptivePacingPolicy.onSuccess(state, 60000L))
        state = LtpAdaptivePacingPolicy.onSuccess(state, 121000L)
        assertEquals(1250L, state.gapMs)
        repeat(8) { i -> state = LtpAdaptivePacingPolicy.onSuccess(state, 242000L + i*121000L) }
        assertEquals(950L, state.gapMs)
    }
    @Test fun broker429ImmediatelySlowsAndRequiresNewCleanWindow() {
        val slower = LtpAdaptivePacingPolicy.on429(LtpAdaptivePacingPolicy.State(300L,1000L),31000L)
        assertEquals(1300L, slower.gapMs)
        assertEquals(slower, LtpAdaptivePacingPolicy.onSuccess(slower,80000L))
        assertEquals(1250L, LtpAdaptivePacingPolicy.onSuccess(slower,151000L).gapMs)
        assertEquals(8000L, LtpAdaptivePacingPolicy.on429(
            LtpAdaptivePacingPolicy.State(5000L,1000L),10000L).gapMs)
    }
}
