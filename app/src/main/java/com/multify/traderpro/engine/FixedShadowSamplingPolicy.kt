package com.multify.traderpro.engine

/**
 * Fixed production is always 15-minute. Optional five-minute research uses Alpha only
 * when there is generous LIVE_DATA and HISTORICAL headroom and no upcoming Fixed scan.
 * This policy never changes production scan cadence, trading, or broker rate limits.
 */
object FixedShadowSamplingPolicy {
    const val MIN_INTERVAL_MS = 12L * 60L * 1000L
    const val MIN_FIXED_HEADROOM_MS = 5_000L
    const val MAX_REFERENCE_AGE_MS = 5L * 60L * 1000L
    const val MAX_PAIRED_SYMBOLS_PER_SAMPLE = 1
    const val MAX_UTILIZATION_PERCENT = 45

    fun canCollect(
        nowMs: Long,
        lastSuccessfulAtMs: Long,
        fixedPriority: Boolean,
        criticalTrade: Boolean,
        millisUntilNextFixed: Long,
        liveUsedLastMinute: Int,
        livePerMinuteLimit: Int,
        historicalUsedLastMinute: Int,
        historicalPerMinuteLimit: Int,
        brokerReady: Boolean
    ): Boolean {
        if (!brokerReady || fixedPriority || criticalTrade) return false
        if (lastSuccessfulAtMs > 0 && nowMs - lastSuccessfulAtMs < MIN_INTERVAL_MS) return false
        if (millisUntilNextFixed < MIN_FIXED_HEADROOM_MS) return false
        if (livePerMinuteLimit <= 0 || historicalPerMinuteLimit <= 0) return false
        if (liveUsedLastMinute.toLong() * 100L >= livePerMinuteLimit.toLong() * MAX_UTILIZATION_PERCENT) return false
        if (historicalUsedLastMinute.toLong() * 100L >= historicalPerMinuteLimit.toLong() * MAX_UTILIZATION_PERCENT) return false
        return true
    }
}
