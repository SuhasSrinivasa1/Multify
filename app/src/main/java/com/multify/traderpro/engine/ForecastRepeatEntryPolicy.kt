package com.multify.traderpro.engine

/** Fresh independent same-day opportunity only after the older target was observed hit. */
object ForecastRepeatEntryPolicy {
    const val MIN_GAP_MS = 5L * 60L * 1000L
    const val REBASE_UNDER_PREVIOUS_TARGET_PCT = 0.20
    fun mayPublish(previousStatus: String?, previousCreatedAtMs: Long?, previousTarget: Double?,
                   latestPrice: Double, nowMs: Long): Boolean {
        if (previousStatus == null) return true
        if (previousStatus != "TARGET_HIT" || previousCreatedAtMs == null || previousTarget == null) return false
        return nowMs >= previousCreatedAtMs + MIN_GAP_MS && previousTarget > 0.0 &&
            latestPrice > 0.0 && latestPrice < previousTarget * (1.0 - REBASE_UNDER_PREVIOUS_TARGET_PCT / 100.0)
    }
}
