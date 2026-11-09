package com.multify.traderpro.engine

/**
 * Readiness diagnostics for the immutable Fixed scan pipeline.
 * Distinguishes the actual blocked API category without changing when scans run.
 */
object FixedDataPipelinePolicy {
    enum class Readiness {
        READY,
        AUTH_RECONNECT_REQUIRED,
        LIVE_DATA_COOLDOWN,
        HISTORICAL_COOLDOWN,
        MULTIPLE_API_COOLDOWNS
    }

    const val SUPPRESSION_AUDIT_INTERVAL_MS = 60_000L

    fun readiness(
        reconnectRequired: Boolean,
        historicalCooldownUntilMs: Long,
        liveDataCooldownUntilMs: Long,
        nowMs: Long
    ): Readiness {
        if (reconnectRequired) return Readiness.AUTH_RECONNECT_REQUIRED
        val historicalBlocked = historicalCooldownUntilMs > nowMs
        val liveBlocked = liveDataCooldownUntilMs > nowMs
        return when {
            historicalBlocked && liveBlocked -> Readiness.MULTIPLE_API_COOLDOWNS
            historicalBlocked -> Readiness.HISTORICAL_COOLDOWN
            liveBlocked -> Readiness.LIVE_DATA_COOLDOWN
            else -> Readiness.READY
        }
    }

    fun shouldAuditSuppression(
        previous: Readiness?,
        current: Readiness,
        previousLogAtMs: Long,
        nowMs: Long
    ): Boolean = current != Readiness.READY &&
        (current != previous || previousLogAtMs == 0L ||
            nowMs - previousLogAtMs >= SUPPRESSION_AUDIT_INTERVAL_MS)
}
