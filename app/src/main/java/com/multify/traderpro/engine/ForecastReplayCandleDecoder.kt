package com.multify.traderpro.engine

import com.google.gson.JsonElement

/**
 * Forecast-only historical replay parser. Use the same Groww timestamp normalization
 * as live Forecast 1m/5m aggregation. Never fabricate time or prices for malformed bars.
 */
object ForecastReplayCandleDecoder {
    data class Bar(
        val atMs: Long, val open: Double, val high: Double,
        val low: Double, val close: Double, val volume: Double
    )
    data class Result(
        val bars: List<Bar>,
        val sourceRows: Int,
        val badTimestampRows: Int,
        val badOhlcRows: Int,
        val shortRows: Int,
        val firstTimestampShape: String
    )

    fun decode(raw: List<List<JsonElement>>): Result {
        var badTimestamp = 0
        var badOhlc = 0
        var short = 0
        val bars = ArrayList<Bar>(raw.size)
        for (row in raw) {
            if (row.size < 5) { short++; continue }
            val timestamp = MarketCandleTimestampParser.toEpochMillis(row[0])
            if (timestamp == null) { badTimestamp++; continue }
            fun finitePositive(n: JsonElement): Double? =
                runCatching { n.asDouble }.getOrNull()?.takeIf { it.isFinite() && it > 0.0 }
            val open = finitePositive(row[1])
            val high = finitePositive(row[2])
            val low = finitePositive(row[3])
            val close = finitePositive(row[4])
            if (open == null || high == null || low == null || close == null || high < low) {
                badOhlc++
                continue
            }
            val volume = row.getOrNull(5)?.let { runCatching { it.asDouble }.getOrNull() }
                ?.takeIf { it.isFinite() && it >= 0.0 } ?: 0.0
            bars += Bar(timestamp, open, high, low, close, volume)
        }
        val shape = when (val first = raw.firstOrNull()?.firstOrNull()) {
            null -> "MISSING"
            else -> {
                if (!first.isJsonPrimitive) "NON_PRIMITIVE"
                else {
                    val p = first.asJsonPrimitive
                    if (p.isNumber) "EPOCH_NUMBER"
                    else if (p.asString.matches(Regex("[0-9]+"))) "EPOCH_STRING"
                    else if (p.asString.contains("T")) "ISO_TIMESTAMP"
                    else "OTHER_TIMESTAMP_STRING"
                }
            }
        }
        return Result(bars.sortedBy { it.atMs }, raw.size, badTimestamp, badOhlc, short, shape)
    }
}
