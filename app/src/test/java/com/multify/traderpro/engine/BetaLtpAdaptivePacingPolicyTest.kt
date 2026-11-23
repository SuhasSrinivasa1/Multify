package com.multify.traderpro.engine
import org.junit.Assert.*
import org.junit.Test
class BetaLtpAdaptivePacingPolicyTest {
    @Test fun cleanMinutesRecoverLongCooldownPacingInMinutesNotHours() {
        var state = BetaLtpAdaptivePacingPolicy.State(5_700L, 1_000L)
        assertEquals(state, BetaLtpAdaptivePacingPolicy.onSuccess(state, 60_999L))
        repeat(7) { index -> state = BetaLtpAdaptivePacingPolicy.onSuccess(state, 61_000L + index * 60_000L) }
        assertTrue(state.gapMs <= BetaLtpAdaptivePacingPolicy.START_GAP_MS)
        assertTrue(state.gapMs >= BetaLtpAdaptivePacingPolicy.MIN_GAP_MS)
    }
    @Test fun broker429DoublesGapAndCleanWindowGraduallyRecovers() {
        val slowed = BetaLtpAdaptivePacingPolicy.on429(BetaLtpAdaptivePacingPolicy.State(5_700L, 1_000L), 10_000L)
        assertEquals(8_000L, slowed.gapMs)
        assertEquals(slowed, BetaLtpAdaptivePacingPolicy.onSuccess(slowed, 69_999L))
        assertEquals(6_400L, BetaLtpAdaptivePacingPolicy.onSuccess(slowed, 70_000L).gapMs)
    }
    @Test fun pacingNeverFallsBelowFloorOrExceedsMaximum() {
        var state = BetaLtpAdaptivePacingPolicy.State(1_300L, 0L)
        repeat(20) { index -> state = BetaLtpAdaptivePacingPolicy.onSuccess(state, (index + 1L) * 60_000L) }
        assertEquals(BetaLtpAdaptivePacingPolicy.MIN_GAP_MS, state.gapMs)
        assertEquals(BetaLtpAdaptivePacingPolicy.MAX_GAP_MS,
            BetaLtpAdaptivePacingPolicy.on429(BetaLtpAdaptivePacingPolicy.State(8_000L, 0L), 1L).gapMs)
    }
}
