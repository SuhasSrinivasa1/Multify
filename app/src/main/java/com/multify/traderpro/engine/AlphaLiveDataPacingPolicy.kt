package com.multify.traderpro.engine

/**
 * Shared Alpha quote/LTP/OHLC endpoint pacing. Groww returned a quote 429 below
 * its published aggregate category ceiling, so no live-data endpoint may burst
 * independently of the others. Pure transport policy; it does not filter symbols.
 */
object AlphaLiveDataPacingPolicy {
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
