package com.multify.traderpro.engine

/** Deliberately conservative handling of broker throttling; it never retries a 429 immediately. */
object GrowwRecoveryPolicy {
    const val AUTH_REQUEST_BUDGET_24H = 120 // reserve 30 below Groww's published 150.
    private const val MINUTE = 60_000L
    private const val HOUR = 60 * MINUTE
    const val ROLLING_DAY_MS = 24 * HOUR
    const val ROLLING_429_WINDOW_MS = HOUR

    fun cooldownMs(occurrencesInPastHourIncludingCurrent: Int, retryAfterMs: Long): Long {
        // A single isolated 429 with no Retry-After must not freeze Fixed for
        // ten minutes. One guarded retry is permitted after 90 seconds; repeated
        // 429s back off aggressively and the server's own Retry-After wins.
        // This never retries within an active window or increases the request rate.
        val floor = when {
            occurrencesInPastHourIncludingCurrent >= 4 -> HOUR
            occurrencesInPastHourIncludingCurrent == 3 -> 15 * MINUTE
            occurrencesInPastHourIncludingCurrent == 2 -> 5 * MINUTE
            else -> 90_000L
        }
        return maxOf(floor, retryAfterMs.coerceIn(0L, 6 * HOUR))
    }

    fun mayRequestAuth(attemptsInLast24h: Int): Boolean = attemptsInLast24h < AUTH_REQUEST_BUDGET_24H
}
