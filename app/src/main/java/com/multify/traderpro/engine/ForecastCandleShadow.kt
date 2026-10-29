package com.multify.traderpro.engine

import com.multify.traderpro.data.network.HistoricalPayload

/** Non-trading research measurement derived exclusively from received 1m bars. */
object ForecastCandleShadow {
    data class FiveMinuteSummary(val completeBars: Int, val lastClose: Double?)
    fun aggregateFiveMinute(history: HistoricalPayload): FiveMinuteSummary {
        val candles = history.candles.mapNotNull { c ->
            if (c.size < 6) return@mapNotNull null
            val epoch = runCatching { c[0].asString.toLong() }.getOrNull() ?: return@mapNotNull null
            val close = runCatching { c[4].asDouble }.getOrNull() ?: return@mapNotNull null
            if (!close.isFinite()) return@mapNotNull null
            (epoch / 300L) to ((epoch / 60L) % 5L to close)
        }
        val complete = candles.groupBy({ it.first }, { it.second })
            .filterValues { values -> values.map { it.first }.distinct().size == 5 }
        return FiveMinuteSummary(complete.size, complete.maxByOrNull { it.key }?.value?.lastOrNull()?.second)
    }
}
