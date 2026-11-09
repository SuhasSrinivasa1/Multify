package com.multify.traderpro.engine

/**
 * Transport-only account routing for the frozen Fixed pipeline.
 * Every NSE batch is still requested and every candidate is still evaluated by the same
 * frozen engine. Beta is reserved for wide LTP discovery; Alpha retains detail evaluation
 * and all order execution.
 */
object FixedDualAccountTransportPolicy {
    const val BETA_DISCOVERY_BATCH_CAP_PER_MINUTE = 35

    fun betaDiscoveryBatch(index: Int, totalBatches: Int, betaReady: Boolean): Boolean {
        if (!betaReady || index < 0 || totalBatches <= 0 || index >= totalBatches) return false
        val betaTarget = minOf(BETA_DISCOVERY_BATCH_CAP_PER_MINUTE, totalBatches)
        val before = (index.toLong() * betaTarget / totalBatches).toInt()
        val after = ((index + 1L) * betaTarget / totalBatches).toInt()
        return after > before
    }

    /** Beta's 35/min live lane is reserved for full-universe discovery, not detail quotes. */
    @Suppress("UNUSED_PARAMETER")
    fun betaDetailedEvaluation(symbol: String, betaReady: Boolean): Boolean = false
}
