package com.multify.traderpro.engine

/** Read-only routing; zero effect on stock universe/scoring/BUY protection. */
object FixedResearchCapacityPolicy {
    const val BETA_SAFE_LIVE_60S = 40
    const val BETA_DISCOVERY_RESERVE = 6
    const val BETA_DETAIL_RESERVE = 3
    const val MIN_PREFETCH_SPARE_REQUESTS = 10
    const val BETA_RECOVERY_PROBE_BATCH_INDEX = 1

    fun betaDiscoveryPreferred(
        index: Int, betaReady: Boolean, betaRemaining60s: Int,
        betaRemainingCalendar: Int, betaGapMs: Long,
        recoveryProbeRequired: Boolean = false
    ): Boolean {
        if (!betaReady || index < 0) return false
        val available = minOf(betaRemaining60s, betaRemainingCalendar)
        if (available <= BETA_DISCOVERY_RESERVE) return false
        // After a broker cooldown, permit exactly one read-only half-open probe
        // before normal batch routing resumes. This also prevents multiple parallel
        // batches from all mistaking themselves for the recovery probe.
        if (recoveryProbeRequired)
            return index == BETA_RECOVERY_PROBE_BATCH_INDEX
        // A long adaptive gap without an outstanding probe remains ineligible.
        if (betaGapMs >= 4_000L) return false
        return index % 2 == 1
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
        betaRemaining60s: Int, betaRemainingCalendar: Int,
        betaLiveHealthy: Boolean = true
    ): Boolean {
        val betaHasSpare = betaLiveHealthy &&
            minOf(betaRemaining60s, betaRemainingCalendar) >= MIN_PREFETCH_SPARE_REQUESTS
        // During the foreground Fixed detail phase, prefetch may overlap ONLY when
        // both accounts have independent headroom. This preserves Alpha's protected
        // reserve and Beta's read-only quota while moving the next sweep off the
        // next scan's critical path. If either account is unavailable, wait until
        // the foreground cycle completes and retain the original safe fallback.
        return if (foregroundResearchActive) alphaHasSpareResearch && betaHasSpare
        else alphaHasSpareResearch || betaHasSpare
    }
}
