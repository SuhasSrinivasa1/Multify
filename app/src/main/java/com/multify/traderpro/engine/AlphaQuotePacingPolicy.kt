package com.multify.traderpro.engine

/**
 * Smooths Alpha's per-symbol quote bursts. v8.3.18 logs showed up to eight
 * /live-data/quote starts in one second immediately before HTTP 429 responses.
 * This is transport admission only: no Fixed scoring, publication, BUY, SELL or GTT rule.
 */
object AlphaQuotePacingPolicy {
    const val START_GAP_MS = 650L
    const val MIN_GAP_MS = 500L
    const val MAX_GAP_MS = 8_000L
    const val CLEAN_WINDOW_MS = 120_000L
    const val CLEAN_STEP_MS = 50L

    data class State(val gapMs: Long, val lastAdjustmentAtMs: Long)

    fun waitMs(nowMs: Long, lastGrantedAtMs: Long, activeGapMs: Long): Long =
        if (lastGrantedAtMs <= 0L) 0L
        else (lastGrantedAtMs + activeGapMs.coerceIn(MIN_GAP_MS, MAX_GAP_MS) - nowMs)
            .coerceAtLeast(0L)

    fun onSuccess(current: State, nowMs: Long): State =
        if (nowMs - current.lastAdjustmentAtMs >= CLEAN_WINDOW_MS)
            State((current.gapMs - CLEAN_STEP_MS).coerceAtLeast(MIN_GAP_MS), nowMs)
        else current

    fun on429(current: State, nowMs: Long): State =
        State((current.gapMs * 2L).coerceIn(MIN_GAP_MS, MAX_GAP_MS), nowMs)
}
