package com.multify.traderpro.engine

import com.multify.traderpro.data.network.HistoricalPayload
import com.google.gson.JsonElement
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Recover incomplete current-session 15m history using a separately fetched, verified
 * completed prior-session prefix. Never invent candles, reuse incomplete bars, or
 * change Fixed scoring/quality/publication. Fail closed if validation is insufficient.
 */
internal object Fixed15mHistoryRepair {
    private const val CANDLE_MS = 15L * 60_000L
    private const val WINDOW_MS = 90_000L
    private const val MAX_EXTRA_CALLS_PER_WINDOW = 6
    private val lock = Any()
    private var bucket = Long.MIN_VALUE
    private var used = 0
    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    /** Bounds additional broker calls across all concurrent Fixed symbol evaluations. */
    fun allowRecovery(elapsedMs: Long): Boolean = synchronized(lock) {
        val currentBucket = elapsedMs / WINDOW_MS
        if (bucket != currentBucket) {
            bucket = currentBucket
            used = 0
        }
        if (used >= MAX_EXTRA_CALLS_PER_WINDOW) false
        else { used++; true }
    }

    /** Fetch prior completed sessions only; current-day data comes from the fresh original request. */
    fun priorWindow(now: ZonedDateTime): Pair<String, String> {
        var prior = now.toLocalDate().minusDays(1)
        while (prior.dayOfWeek == DayOfWeek.SATURDAY || prior.dayOfWeek == DayOfWeek.SUNDAY)
            prior = prior.minusDays(1)
        return prior.minusDays(7).atTime(9, 15).format(formatter) to
            prior.atTime(15, 30).format(formatter)
    }

    fun mergeCompleted(
        current: HistoricalPayload, prior: HistoricalPayload, asOfMs: Long
    ): HistoricalPayload? {
        val india = MarketCandleTimestampParser.NSE_ZONE
        val date = java.time.Instant.ofEpochMilli(asOfMs).atZone(india).toLocalDate()
        val dayStart = date.atStartOfDay(india).toInstant().toEpochMilli()
        val sessionOpen = date.atTime(9, 15).atZone(india).toInstant().toEpochMilli()
        val rows = sortedMapOf<Long, List<JsonElement>>()
        fun add(source: HistoricalPayload, older: Boolean) {
            for (row in source.candles) {
                if (row.size < 6) continue
                val ts = MarketCandleTimestampParser.toEpochMillis(row[0]) ?: continue
                if (ts < 1_500_000_000_000L || ts > asOfMs - CANDLE_MS) continue
                if (older && ts >= dayStart) continue // discard any broker-ignored end_time
                if (!older && ts < dayStart) continue // older prefix comes solely from verified prior fetch
                val numerical = row.subList(1, 6).all {
                    runCatching { it.asDouble.isFinite() }.getOrDefault(false)
                }
                if (numerical) rows[ts] = row
            }
        }
        add(prior, true)
        add(current, false)
        // No production Fixed evaluation is authorized before a valid *completed*
        // current-session 15m candle exists.
        if (rows.size < 5 || rows.keys.none { it >= sessionOpen }) return null
        return current.copy(candles = rows.values.takeLast(160))
    }
}
