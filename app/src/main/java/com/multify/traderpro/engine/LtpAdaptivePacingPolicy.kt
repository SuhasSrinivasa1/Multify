package com.multify.traderpro.engine

/**
 * Transport-only AIMD pacing for Groww /ltp and Beta read-only live requests.
 * Account caps are upper bounds, not a promise of broker acceptance. Start below
 * the ceiling, cautiously approach it after clean minutes, and retreat on 429.
 * Per-account instances are managed by their own network governors.
 */
object LtpAdaptivePacingPolicy {
    const val START_GAP_MS = 1_300L
    const val MIN_GAP_MS = 950L
    const val MAX_GAP_MS = 8_000L
    const val CLEAN_WINDOW_MS = 120_000L
    private const val CLEAN_STEP_MS = 50L

    data class State(val gapMs: Long, val lastAdjustmentAtMs: Long)

    fun onSuccess(current: State, nowMs: Long): State =
        if (nowMs - current.lastAdjustmentAtMs >= CLEAN_WINDOW_MS)
            State((current.gapMs - CLEAN_STEP_MS).coerceAtLeast(MIN_GAP_MS), nowMs)
        else current

    fun on429(current: State, nowMs: Long): State =
        State((current.gapMs * 2).coerceIn(START_GAP_MS, MAX_GAP_MS), nowMs)
}
