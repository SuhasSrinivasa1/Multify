package com.multify.traderpro.engine

/** Read-only routing; zero effect on stock universe/scoring/BUY protection. */
object FixedResearchCapacityPolicy {
    const val BETA_SAFE_LIVE_60S = 40
    const val BETA_DISCOVERY_RESERVE = 6
    const val BETA_DETAIL_RESERVE = 3
    const val MIN_PREFETCH_SPARE_REQUESTS = 10

    fun betaDiscoveryPreferred(
        index: Int, betaReady: Boolean, betaRemaining60s: Int,
        betaRemainingCalendar: Int, betaGapMs: Long
    ): Boolean {
        if (!betaReady || index < 0 || betaGapMs >= 4_000L) return false
        val available = minOf(betaRemaining60s, betaRemainingCalendar)
        return available > BETA_DISCOVERY_RESERVE && index % 2 == 1
    }
    fun betaDetailPreferred(
        symbol: String, betaReady: Boolean, betaRemaining60s: Int,
        betaRemainingCalendar: Int, betaGapMs: Long
    ): Boolean {
        if (!betaReady || symbol.isBlank() || betaGapMs >= 4_000L) return false
        val available = minOf(betaRemaining60s, betaRemainingCalendar)
        return available > BETA_DETAIL_RESERVE &&
            Math.floorMod(symbol.uppercase(java.util.Locale.US).hashCode(), 5) < 2
    }
    fun prefetchMayStart(
        foregroundResearchActive: Boolean, alphaHasSpareResearch: Boolean,
        betaRemaining60s: Int, betaRemainingCalendar: Int
    ): Boolean = !foregroundResearchActive &&
        (alphaHasSpareResearch ||
            minOf(betaRemaining60s,betaRemainingCalendar) >= MIN_PREFETCH_SPARE_REQUESTS)
}
