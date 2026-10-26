package com.multify.traderpro.engine

/**
 * Network-readiness policy outside the immutable Fixed v7.4.1 forecasting region.
 * It never changes candidate construction, indicator inputs, model scores or targets.
 * An unavailable historical-candle API pauses a scan before it produces 48 predictable
 * local request errors; scanning resumes as soon as the broker cooldown expires.
 */
object FixedDataPipelinePolicy {
    enum class Readiness { READY, AUTH_RECONNECT_REQUIRED, HISTORICAL_COOLDOWN }

    const val SUPPRESSION_AUDIT_INTERVAL_MS = 60_000L

    fun readiness(
        reconnectRequired: Boolean,
        historicalCooldownUntilMs: Long,
        nowMs: Long
    ): Readiness = when {
        reconnectRequired -> Readiness.AUTH_RECONNECT_REQUIRED
        historicalCooldownUntilMs > nowMs -> Readiness.HISTORICAL_COOLDOWN
        else -> Readiness.READY
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
