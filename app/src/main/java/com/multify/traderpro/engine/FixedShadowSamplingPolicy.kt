package com.multify.traderpro.engine

/**
 * Fixed production is always 15-minute. Optional five-minute research prefers Beta and
 * uses Alpha only when Beta is not configured, with spare LIVE/HISTORICAL headroom and
 * no upcoming Fixed scan. One symbol is sampled per 12 minutes; never changes trading.
 */
object FixedShadowSamplingPolicy {
    const val MIN_INTERVAL_MS = 12L * 60L * 1000L
    const val MIN_FIXED_HEADROOM_MS = 5_000L
    const val MAX_REFERENCE_AGE_MS = 5L * 60L * 1000L
    const val MAX_PAIRED_SYMBOLS_PER_SAMPLE = 1
    const val MAX_UTILIZATION_PERCENT = 75
    const val POST_429_RESEARCH_QUIET_PERIOD_MS = 30L * 60L * 1000L

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
        brokerReady: Boolean,
        latestBrokerCooldownUntilMs: Long = 0L,
        lastObserved429AtMs: Long = 0L
    ): Boolean {
        if (!brokerReady || fixedPriority || criticalTrade) return false
        // No discretionary research while 429 protection is active or recently expired.
        if (latestBrokerCooldownUntilMs > 0L &&
            nowMs - latestBrokerCooldownUntilMs < POST_429_RESEARCH_QUIET_PERIOD_MS) return false
        if (lastObserved429AtMs > 0L &&
            nowMs - lastObserved429AtMs < POST_429_RESEARCH_QUIET_PERIOD_MS) return false
        if (lastSuccessfulAtMs > 0 && nowMs - lastSuccessfulAtMs < MIN_INTERVAL_MS) return false
        if (millisUntilNextFixed < MIN_FIXED_HEADROOM_MS) return false
        if (livePerMinuteLimit <= 0 || historicalPerMinuteLimit <= 0) return false
        if (liveUsedLastMinute.toLong() * 100L >= livePerMinuteLimit.toLong() * MAX_UTILIZATION_PERCENT) return false
        if (historicalUsedLastMinute.toLong() * 100L >= historicalPerMinuteLimit.toLong() * MAX_UTILIZATION_PERCENT) return false
        return true
    }
}
