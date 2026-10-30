package com.multify.traderpro.engine

/**
 * Extra permit before Alpha /v1/live-data/ltp calls. The generic LIVE_DATA budget
 * did not prevent endpoint 429s, so spread out batch discovery calls.
 * Pure deterministic policy; it never changes order submission or SELL stops.
 * These bounds reduce burst risk, but broker 429s remain possible.
 */
object AlphaLtpPacingPolicy {
    const val MIN_GAP_MS = 1_200L
    const val MAX_PER_MINUTE = 40

    fun waitMs(nowMs: Long, lastGrantedAtMs: Long, oldestInMinuteAtMs: Long?, usedInMinute: Int): Long {
        val gap = if (lastGrantedAtMs > 0L)
            (lastGrantedAtMs + MIN_GAP_MS - nowMs).coerceAtLeast(0L) else 0L
        val minute = if (usedInMinute >= MAX_PER_MINUTE && oldestInMinuteAtMs != null)
            (oldestInMinuteAtMs + 60_000L - nowMs).coerceAtLeast(1L) else 0L
        return maxOf(gap, minute)
    }
}
