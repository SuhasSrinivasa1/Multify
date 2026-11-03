package com.multify.traderpro.engine

/** Forecast only. A stale alert remains research but is not a live BUY opportunity. */
object ForecastNotificationFreshnessPolicy {
    const val MAX_ACTIONABLE_AGE_MS = 120_000L
    fun fresh(generatedAtMs: Long, nowMs: Long): Boolean =
        generatedAtMs > 0L && nowMs >= generatedAtMs &&
            nowMs - generatedAtMs <= MAX_ACTIONABLE_AGE_MS
}
