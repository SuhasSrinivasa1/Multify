package com.multify.traderpro.engine

/**
 * Read-only transport guard for the frozen 48-candidate Fixed detail cycle.
 *
 * Beta may take over the full NSE discovery sweep during an Alpha LIVE_DATA
 * cooldown, but its 40/minute live budget cannot also absorb all 48 individual
 * detail quotes in that same cycle without waiting into a later scan window.
 * This policy prevents that quota wait from turning one degraded scan into a
 * multi-minute partial cycle. It never changes candidate selection or scoring.
 */
object FixedBetaDetailCycleCapacityPolicy {
    const val REQUIRED_DETAIL_QUOTES = 48
    const val RESERVED_HEADROOM = 3

    /**
     * True only when every potential Fixed detail quote plus the existing
     * small reserve fits in BOTH current rolling and calendar-minute headroom.
     * Negative or insufficient capacity fails closed.
     */
    fun hasImmediateHeadroomForFullDetailCycle(
        rollingMinuteRemaining: Int,
        calendarMinuteRemaining: Int
    ): Boolean {
        val available = minOf(rollingMinuteRemaining, calendarMinuteRemaining).coerceAtLeast(0)
        return available >= REQUIRED_DETAIL_QUOTES + RESERVED_HEADROOM
    }
}
