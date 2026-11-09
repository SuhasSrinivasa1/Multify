package com.multify.traderpro.engine

/**
 * Shared Alpha quote/LTP/OHLC pacing. The latest log received a quote 429 below
 * the published aggregate category ceiling, so live-data endpoints share one lane.
 *
 * When app-owned holdings exist, research is capped at 28/min and 12/min remains
 * available to protective holding LTP requests. With no holdings, Fixed can use
 * the full 40/min. This is transport admission only; it never filters candidates.
 */
object AlphaLiveDataPacingPolicy {
    const val MAX_PER_MINUTE = 40
    const val MAX_RESEARCH_WITH_HOLDINGS_PER_MINUTE = 28
    const val MIN_GAP_MS = 1_200L

    fun waitMs(
        nowMs: Long,
        lastGrantedAtMs: Long,
        oldestTotalInMinuteAtMs: Long?,
        totalUsedInMinute: Int,
        oldestResearchInMinuteAtMs: Long?,
        researchUsedInMinute: Int,
        protectionRequest: Boolean,
        hasManagedHoldings: Boolean
    ): Long {
        val gap = if (lastGrantedAtMs > 0L)
            (lastGrantedAtMs + MIN_GAP_MS - nowMs).coerceAtLeast(0L) else 0L
        val totalWait = if (totalUsedInMinute >= MAX_PER_MINUTE && oldestTotalInMinuteAtMs != null)
            (oldestTotalInMinuteAtMs + 60_000L - nowMs).coerceAtLeast(1L) else 0L
        val researchLimit = if (hasManagedHoldings && !protectionRequest)
            MAX_RESEARCH_WITH_HOLDINGS_PER_MINUTE else MAX_PER_MINUTE
        val researchWait = if (!protectionRequest && researchUsedInMinute >= researchLimit &&
            oldestResearchInMinuteAtMs != null
        ) (oldestResearchInMinuteAtMs + 60_000L - nowMs).coerceAtLeast(1L) else 0L
        return maxOf(gap, totalWait, researchWait)
    }
}
