package com.multify.traderpro.engine

/** Pure, read-only watchdog classification. Never decides broker order execution. */
object MobileEngineHealthPolicy {
    data class Snapshot(
        val status: String,
        val fixedWorkerRunning: Boolean,
        val positionWorkerRunning: Boolean,
        val networkValidated: Boolean,
        val fixedProgressAgeMs: Long?,
        val holdingMonitorAgeMs: Long?,
        val appOwnedHoldings: Int,
        val fixedScanStalled: Boolean,
        val protectionMonitorStalled: Boolean
    )

    const val FIXED_PROGRESS_WARN_MS = 8L * 60_000L
    const val HOLDING_MONITOR_WARN_MS = 20_000L

    fun assess(
        marketOpen: Boolean, fixedWorkerRunning: Boolean,
        positionWorkerRunning: Boolean, networkValidated: Boolean,
        nowMs: Long, fixedProgressAtMs: Long, positionPassAtMs: Long,
        appOwnedHoldings: Int,
        brokerCooldownActive: Boolean = false
    ): Snapshot {
        val fixedAge = if (fixedProgressAtMs in 1..nowMs) nowMs - fixedProgressAtMs else null
        val monitorAge = if (positionPassAtMs in 1..nowMs) nowMs - positionPassAtMs else null
        val fixedStall = marketOpen && fixedWorkerRunning &&
            !brokerCooldownActive && fixedAge != null && fixedAge > FIXED_PROGRESS_WARN_MS
        val monitorStall = appOwnedHoldings > 0 && positionWorkerRunning &&
            (monitorAge == null || monitorAge > HOLDING_MONITOR_WARN_MS)
        val status = when {
            !positionWorkerRunning -> "PROTECTION_WORKER_DOWN"
            monitorStall -> "PROTECTION_MONITOR_STALE"
            !networkValidated -> "NETWORK_UNVALIDATED"
            marketOpen && !fixedWorkerRunning -> "FIXED_WORKER_DOWN"
            marketOpen && brokerCooldownActive -> "BROKER_API_COOLDOWN"
            fixedStall -> "FIXED_SCAN_PROGRESS_STALE"
            marketOpen && fixedAge == null -> "FIXED_WARMING"
            else -> "RUNNING"
        }
        return Snapshot(status, fixedWorkerRunning, positionWorkerRunning, networkValidated,
            fixedAge, monitorAge, appOwnedHoldings, fixedStall, monitorStall)
    }
}
