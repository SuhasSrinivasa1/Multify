package com.multify.traderpro.engine

/** Adaptive handling of broker throttling; never retries a 429 immediately. */
object GrowwRecoveryPolicy {
    const val AUTH_REQUEST_BUDGET_24H = 120 // reserve 30 below Groww's published 150.
    private const val MINUTE = 60_000L
    private const val HOUR = 60 * MINUTE
    const val ROLLING_DAY_MS = 24 * HOUR
    const val ROLLING_429_WINDOW_MS = HOUR

    /**
     * A single 429 without Retry-After pauses the affected category for one rate-limit
     * window, not an arbitrary ten minutes. Repeated incidents escalate progressively.
     * A longer broker Retry-After always wins.
     */
    fun cooldownMs(occurrencesInPastHourIncludingCurrent: Int, retryAfterMs: Long): Long {
        val floor = when {
            occurrencesInPastHourIncludingCurrent >= 4 -> 30 * MINUTE
            occurrencesInPastHourIncludingCurrent == 3 -> 10 * MINUTE
            occurrencesInPastHourIncludingCurrent == 2 -> 3 * MINUTE
            else -> MINUTE
        }
        return maxOf(floor, retryAfterMs.coerceIn(0L, 6 * HOUR))
    }

    fun mayRequestAuth(attemptsInLast24h: Int): Boolean = attemptsInLast24h < AUTH_REQUEST_BUDGET_24H
}
