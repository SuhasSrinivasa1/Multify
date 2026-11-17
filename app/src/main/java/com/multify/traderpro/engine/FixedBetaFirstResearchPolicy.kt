package com.multify.traderpro.engine

/**
 * Broker transport only: Beta is the preferred read-only LIVE research account.
 * Never determines stock selection, scoring, publication, PRIMARY, BUY or GTT.
 * Alpha keeps independently healthy HISTORICAL capacity and exclusive orders.
 * Do not flood an already-slow Beta lane with work that cannot complete promptly.
 */
object FixedBetaFirstResearchPolicy {
    const val PRESSURE_WARN_GAP_MS = 2_400L
    const val PRESSURE_CRITICAL_GAP_MS = 4_000L

    fun preferBetaBatch(index: Int, betaReady: Boolean, betaGapMs: Long): Boolean {
        if (!betaReady || index < 0) return false
        // 4/5 in healthy markets, 3/5 when throttling begins. Account governors
        // remain the final authority; this never increases total NSE batches.
        return if (betaGapMs < PRESSURE_WARN_GAP_MS) index % 5 != 0
        else index % 5 < 3
    }

    fun preferBetaDetail(symbol: String, betaReady: Boolean, betaGapMs: Long): Boolean {
        if (!betaReady || symbol.isBlank()) return false
        val slot = Math.floorMod(symbol.uppercase(java.util.Locale.US).hashCode(), 5)
        return if (betaGapMs < PRESSURE_WARN_GAP_MS) slot < 4 else slot < 3
    }

    fun shouldDeferBetaOnlySweep(betaReady: Boolean, alphaReady: Boolean, betaGapMs: Long): Boolean =
        betaReady && !alphaReady && betaGapMs >= PRESSURE_CRITICAL_GAP_MS
}
