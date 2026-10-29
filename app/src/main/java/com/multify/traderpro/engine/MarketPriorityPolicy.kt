package com.multify.traderpro.engine

/**
 * Market-hours scheduler policy. It never changes Fixed or trailing-stop calculations;
 * it only decides whether adaptive Forecast work may consume spare API capacity.
 */
object MarketPriorityPolicy {
    const val FIXED_GUARD_WINDOW_MS = 15_000L
    const val FORECAST_MAX_LIVE_UTILIZATION_PCT = 80
    const val FORECAST_MAX_HISTORICAL_UTILIZATION_PCT = 85
    const val FORECAST_MAX_NON_TRADING_UTILIZATION_PCT = 70
    const val LIVE_FORECAST_UNLOCK_SESSIONS = 5

    fun canRunForecast(
        fixedPriorityRequested: Boolean,
        criticalTradeInFlight: Boolean,
        millisUntilNextFixed: Long,
        liveUsedPerMinute: Int,
        liveAppLimitPerMinute: Int,
        historicalUsedPerMinute: Int,
        historicalAppLimitPerMinute: Int,
        nonTradingUsedPerMinute: Int,
        nonTradingAppLimitPerMinute: Int
    ): Boolean {
        if (fixedPriorityRequested || criticalTradeInFlight) return false
        if (millisUntilNextFixed in 0..FIXED_GUARD_WINDOW_MS) return false
        fun utilization(used: Int, limit: Int): Int =
            if (limit <= 0) 100 else ((used * 100.0) / limit).toInt()
        if (utilization(liveUsedPerMinute, liveAppLimitPerMinute) >= FORECAST_MAX_LIVE_UTILIZATION_PCT) return false
        if (utilization(historicalUsedPerMinute, historicalAppLimitPerMinute) >= FORECAST_MAX_HISTORICAL_UTILIZATION_PCT) return false
        if (utilization(nonTradingUsedPerMinute, nonTradingAppLimitPerMinute) >= FORECAST_MAX_NON_TRADING_UTILIZATION_PCT) return false
        return true
    }

    fun liveForecastUnlocked(completedTradingSessions: Int): Boolean =
        completedTradingSessions >= LIVE_FORECAST_UNLOCK_SESSIONS
}
