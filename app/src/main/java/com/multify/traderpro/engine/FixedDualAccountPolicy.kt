package com.multify.traderpro.engine

/**
 * Deterministic transport-only work allocation between Alpha and a separately
 * authenticated, READ-ONLY Beta. Never used to select, score, rank, publish or
 * execute a trade. Both accounts observe the same NSE cash instruments.
 */
object FixedDualAccountPolicy {
    // No time-varying hash seed. Same symbol always uses the same history account.
    fun betaDiscoveryBatch(batchIndex: Int, betaReady: Boolean): Boolean =
        betaReady && batchIndex >= 0 && batchIndex % 2 == 1

    // Keep the Beta 15m history budget below its independently throttled limit.
    fun betaDetailedEvaluation(symbol: String, betaReady: Boolean): Boolean =
        betaReady && symbol.isNotBlank() && Math.floorMod(symbol.hashCode(), 6) == 0

    fun acceptableCoverage(valid: Int, master: Int, aborted: Boolean): Boolean =
        !aborted && master > 0 && valid.toDouble() / master >= .85

    const val FULL_NSE_LTP_BATCH_LIMIT = 50
    const val BETA_LTP_MIN_GAP_MS = LtpAdaptivePacingPolicy.MIN_GAP_MS
    // Beta is read-only Fixed research. Hard 60% of Groww's published
    // LIVE_DATA per-second and rolling-minute ceilings; no Alpha quota borrowing.
    const val BETA_LIVE_PER_SECOND = 6
    const val BETA_LIVE_PER_MINUTE = FixedResearchCapacityPolicy.BETA_SAFE_LIVE_60S
    const val BETA_HISTORICAL_PER_MINUTE = 8
}
