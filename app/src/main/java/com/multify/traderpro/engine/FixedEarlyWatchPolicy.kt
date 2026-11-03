package com.multify.traderpro.engine

/** Read-only first PRIMARY time/price correlation; cannot publish or execute a trade. */
object FixedEarlyWatchPolicy {
    const val MAX_EVALUATION_LAG_MS = 180_000L

    fun quoteMatchesScan(scanStartedAtMs: Long, evaluationCompletedAtMs: Long): Boolean =
        scanStartedAtMs > 0L && evaluationCompletedAtMs >= scanStartedAtMs &&
            evaluationCompletedAtMs - scanStartedAtMs <= MAX_EVALUATION_LAG_MS

    fun advanceFromFirstPrimary(firstPrice: Double, currentPrice: Double): Double? =
        if (firstPrice.isFinite() && currentPrice.isFinite() &&
            firstPrice > 0.0 && currentPrice > 0.0)
            (currentPrice / firstPrice - 1.0) * 100.0
        else null

    fun independentObservation(priorScanAtMs: Long?, currentScanAtMs: Long): Boolean =
        currentScanAtMs > 0L && (priorScanAtMs == null || priorScanAtMs != currentScanAtMs)
}
