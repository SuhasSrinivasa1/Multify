package com.multify.traderpro.engine

/** Beta-only adaptive LTP pacing; the 6/s and 40/min governor caps are unchanged. */
object BetaLtpAdaptivePacingPolicy {
    const val START_GAP_MS = 1_300L
    const val MIN_GAP_MS = 950L
    const val MAX_GAP_MS = 8_000L
    const val CLEAN_WINDOW_MS = 60_000L
    private const val CLEAN_DECAY_FACTOR = 0.80
    data class State(val gapMs: Long, val lastAdjustmentAtMs: Long)
    fun onSuccess(current: State, nowMs: Long): State {
        if (nowMs - current.lastAdjustmentAtMs < CLEAN_WINDOW_MS) return current
        val next = (current.gapMs * CLEAN_DECAY_FACTOR).toLong().coerceIn(MIN_GAP_MS, MAX_GAP_MS)
        return State(next, nowMs)
    }
    fun on429(current: State, nowMs: Long): State =
        State((current.gapMs * 2L).coerceIn(START_GAP_MS, MAX_GAP_MS), nowMs)
}
