package com.multify.traderpro.engine

/** Deliberately conservative handling of broker throttling; it never retries a 429 immediately. */
object GrowwRecoveryPolicy {
    const val AUTH_REQUEST_BUDGET_24H = 120 // reserve 30 below Groww's published 150.
    private const val MINUTE = 60_000L
    private const val HOUR = 60 * MINUTE
    const val ROLLING_DAY_MS = 24 * HOUR
    const val ROLLING_429_WINDOW_MS = HOUR

    fun cooldownMs(occurrencesInPastHourIncludingCurrent: Int, retryAfterMs: Long): Long {
        val floor = when {
            occurrencesInPastHourIncludingCurrent >= 3 -> HOUR
            occurrencesInPastHourIncludingCurrent == 2 -> 30 * MINUTE
            else -> 10 * MINUTE
        }
        return maxOf(floor, retryAfterMs.coerceIn(0L, 6 * HOUR))
    }

    fun mayRequestAuth(attemptsInLast24h: Int): Boolean = attemptsInLast24h < AUTH_REQUEST_BUDGET_24H
}
