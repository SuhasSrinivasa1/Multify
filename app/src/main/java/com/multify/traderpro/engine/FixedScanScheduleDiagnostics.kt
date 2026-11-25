package com.multify.traderpro.engine

/** Read-only scan cadence telemetry. It never changes scheduling, scoring or publication. */
object FixedScanScheduleDiagnostics {
    data class Cadence(
        val status: String,
        val targetSpacingMs: Long,
        val actualSpacingMs: Long?,
        val estimatedSkippedIntervals: Int
    )

    fun classify(
        actualSpacingMs: Long?,
        targetSpacingMs: Long = 90_000L,
        graceMs: Long = 15_000L
    ): Cadence {
        require(targetSpacingMs > 0L && graceMs >= 0L)
        if (actualSpacingMs == null || actualSpacingMs < 0L) {
            return Cadence("UNKNOWN_AFTER_RESTART_OR_NO_PRIOR_SCAN", targetSpacingMs, actualSpacingMs, 0)
        }
        val status = when {
            actualSpacingMs <= targetSpacingMs + graceMs -> "ON_TARGET"
            actualSpacingMs <= 2L * targetSpacingMs + graceMs -> "LATE"
            else -> "INTERVAL_GAP"
        }
        val skipped = if (status == "INTERVAL_GAP") {
            ((actualSpacingMs.toDouble() / targetSpacingMs).let { kotlin.math.round(it).toInt() } - 1)
                .coerceAtLeast(1)
        } else 0
        return Cadence(status, targetSpacingMs, actualSpacingMs, skipped)
    }

    fun durationBucket(elapsedMs: Long, targetSpacingMs: Long = 90_000L): String = when {
        elapsedMs < 0L -> "UNKNOWN"
        elapsedMs < 30_000L -> "UNDER_30S"
        elapsedMs < 60_000L -> "30_TO_60S"
        elapsedMs < targetSpacingMs -> "60S_TO_TARGET_INTERVAL"
        else -> "AT_OR_ABOVE_TARGET_INTERVAL"
    }
}
