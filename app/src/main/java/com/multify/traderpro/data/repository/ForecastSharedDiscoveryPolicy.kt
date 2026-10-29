package com.multify.traderpro.data.repository

/** Transport integrity only. No Fixed or Forecast selection rule is altered. */
internal object ForecastSharedDiscoveryPolicy {
    const val MAX_FIXED_SNAPSHOT_AGE_MS = 6L * 60L * 1000L
    private const val MIN_COVERAGE = 0.85
    fun fresh(nowMs: Long, sourceAtMs: Long): Boolean =
        sourceAtMs > 0 && nowMs >= sourceAtMs && nowMs - sourceAtMs <= MAX_FIXED_SNAPSHOT_AGE_MS

    fun ready(currentCoverage: Double, priorCoverage: Double, currentMs: Long, priorMs: Long): Boolean =
        currentCoverage >= MIN_COVERAGE && priorCoverage >= MIN_COVERAGE &&
            priorMs > 0 && currentMs > priorMs
}
