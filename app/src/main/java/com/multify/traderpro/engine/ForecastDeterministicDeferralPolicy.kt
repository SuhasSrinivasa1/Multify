package com.multify.traderpro.engine

/** Research scheduling only. Use fresh observed NSE prices to avoid repeatedly fetching
 * historical candles for symbols far outside Forecast's fixed price band.
 * Borderline or stale prices MUST receive a new quote. */
object ForecastDeterministicDeferralPolicy {
    const val MAX_SNAPSHOT_AGE_MS = 90_000L
    fun defer(observedPrice: Double?, snapshotAtMs: Long, nowMs: Long): Boolean {
        if (observedPrice == null || !observedPrice.isFinite() || snapshotAtMs <= 0L ||
            nowMs < snapshotAtMs || nowMs - snapshotAtMs > MAX_SNAPSHOT_AGE_MS) return false
        // Wide buffer protects fast-moving names close to ₹200/₹5,000 boundaries.
        return observedPrice <= 180.0 || observedPrice >= 5_500.0
    }
}
