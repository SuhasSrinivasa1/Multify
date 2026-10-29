package com.multify.traderpro.engine

/** Execution-side freshness only. Never modifies Fixed research, score, target or scan cadence. */
object AutoBuyEntrySafetyPolicy {
    // Two Fixed scan intervals allow a completed evaluation and prompt order dispatch,
    // but prevent automatic buys from hours-old recommendations on toggle enable.
    const val FIXED_AUTO_BUY_MAX_AGE_MS = 300_000L

    fun isFreshFixedEntry(generatedAtMs: Long, nowMs: Long): Boolean =
        generatedAtMs > 0L && nowMs >= generatedAtMs &&
            nowMs - generatedAtMs <= FIXED_AUTO_BUY_MAX_AGE_MS

    fun forecastAutoBuyAllowed(completedCalibrationSessions: Int): Boolean =
        MarketPriorityPolicy.liveForecastUnlocked(completedCalibrationSessions)
}
