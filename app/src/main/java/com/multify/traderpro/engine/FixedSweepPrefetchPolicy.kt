package com.multify.traderpro.engine

/** Transport-only guard; does not authorize an order or alter a Fixed signal. */
object FixedSweepPrefetchPolicy {
    const val EXPECTED_SWEEP_MS = 82_000L
    const val MIN_START_HEADROOM_MS = 20_000L
    const val MAX_START_HEADROOM_MS = 150_000L
    const val MAX_ACCEPTED_AGE_MS = 45_000L
    const val MAX_WAIT_MS = 95_000L
    const val MAX_OVERRUN_MS = 95_000L
    const val MIN_COVERAGE = 0.85

    fun canStart(
        nowMs: Long, dueAtMs: Long, criticalTradeInFlight: Boolean, brokerReady: Boolean
    ): Boolean = nowMs > 0L && dueAtMs > nowMs &&
        dueAtMs - nowMs in MIN_START_HEADROOM_MS..MAX_START_HEADROOM_MS &&
        !criticalTradeInFlight && brokerReady

    fun canConsume(
        completedAtMs: Long, nowMs: Long, sampledSymbols: Int,
        totalSymbols: Int, aborted: Boolean
    ): Boolean {
        if (completedAtMs <= 0L || nowMs < completedAtMs ||
            nowMs - completedAtMs > MAX_ACCEPTED_AGE_MS || aborted ||
            sampledSymbols <= 0 || totalSymbols <= 0) return false
        return sampledSymbols.toDouble() / totalSymbols.toDouble() >= MIN_COVERAGE
    }
}
