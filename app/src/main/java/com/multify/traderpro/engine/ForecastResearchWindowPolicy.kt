package com.multify.traderpro.engine

import java.time.LocalTime

/** Research-only broker-budget allocation. Never owns Fixed, BUY, or SELL requests. */
object ForecastResearchWindowPolicy {
    private const val BOOST_CYCLE_MINUTES = 30
    private const val BOOST_START_OFFSET_MINUTES = 30
    private const val BOOST_DURATION_MINUTES = 5
    private const val START_MINUTES = 9 * 60 + 15
    private const val MARKET_CLOSE_MINUTES = 15 * 60 + 30
    private const val MIN_FIXED_HEADROOM_MS = 35_000L
    private const val RECENT_429_COOLDOWN_MS = 30L * 60_000L

    /** First boost 09:45–09:50 IST, then every 30 minutes until market close. */
    fun isBoostWindow(time: LocalTime): Boolean {
        val minutes = time.hour * 60 + time.minute
        if (minutes < START_MINUTES + BOOST_START_OFFSET_MINUTES || minutes >= MARKET_CLOSE_MINUTES) return false
        return (minutes - START_MINUTES - BOOST_START_OFFSET_MINUTES) % BOOST_CYCLE_MINUTES < BOOST_DURATION_MINUTES
    }

    fun noRecent429(nowMs: Long, last429Times: List<Long>): Boolean =
        last429Times.none { it > 0L && nowMs >= it && nowMs - it < RECENT_429_COOLDOWN_MS }

    fun canUseSpareCapacity(
        fixedDueInMs: Long,
        liveHeadroom: Int,
        historicalHeadroom: Int,
        nonTradingHeadroom: Int,
        isBoost: Boolean
    ): Boolean {
        // Reserve room for Fixed and trading execution in every mode.
        // Even in a boost, no extra Fixed scheduling time or API quota is taken.
        if (fixedDueInMs <= MIN_FIXED_HEADROOM_MS) return false
        return liveHeadroom >= (if (isBoost) 10 else 8) &&
            historicalHeadroom >= (if (isBoost) 6 else 4) &&
            nonTradingHeadroom >= 5
    }

    fun trancheSize(boost: Boolean, postFixed: Boolean, ordinary: Int): Int = when {
        boost -> 4  // Bounded between protected Fixed scans; stop when guard approaches.
        postFixed -> 3
        else -> ordinary
    }
}
