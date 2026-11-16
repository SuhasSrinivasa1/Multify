package com.multify.traderpro.engine

/**
 * UI/telemetry interpretation only. Coverage is a RESULT of the last sweep,
 * NOT a download progress bar. Never feeds the frozen Fixed selection model.
 */
object FixedDiscoveryDisplayPolicy {
    fun label(
        hasScan: Boolean,
        lastSweepAborted: Boolean,
        lastSweepCoverage: Double,
        baselineReady: Boolean,
        lastScanAgeMs: Long,
        staleThresholdMs: Long,
        minCoverage: Double,
        alphaReadCooldown: Boolean,
        betaReadOnlyReady: Boolean
    ): String {
        if (alphaReadCooldown)
            return if (betaReadOnlyReady) "BETA_READ_ONLY_RECOVERY" else "PAUSED_ALPHA_COOLDOWN"
        return when {
            !hasScan -> "WAITING"
            lastSweepAborted || lastSweepCoverage < minCoverage -> "DEGRADED"
            lastScanAgeMs > staleThresholdMs -> "STALE"
            !baselineReady -> "WARMING"
            else -> "HEALTHY"
        }
    }
}
