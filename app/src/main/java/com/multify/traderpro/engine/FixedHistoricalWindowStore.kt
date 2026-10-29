package com.multify.traderpro.engine

import com.google.gson.JsonElement
import com.multify.traderpro.data.network.HistoricalPayload
import java.time.Instant
import java.time.OffsetDateTime
import java.time.LocalDateTime
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.LinkedHashMap

/**
 * Bounded in-memory transport optimization for Fixed 5-minute history.
 *
 * Requests ALWAYS go to the broker for the most recent candles: neither the active candle
 * nor the current price is replayed from cache. If the new response cannot be stitched
 * unambiguously into the preceding full window, callers must use a complete broker fetch.
 */
class FixedHistoricalWindowStore {
    data class IncrementalRequest(val startTime: String)

    private data class Snapshot(
        val storedAtMs: Long,
        val originalStartTime: String,
        val history: HistoricalPayload
    )
    private val cache = object : LinkedHashMap<String, Snapshot>(64, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Snapshot>?): Boolean =
            size > MAX_STOCKS
    }
    private val format = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    private val rejectionReasons = LinkedHashMap<String, String>(MAX_STOCKS, .75f, true)

    @Synchronized
    fun ineligibilityReason(symbol: String, originalStartTime: String, nowMs: Long): String {
        val snapshot = cache[symbol] ?: return rejectionReasons[symbol] ?: "SNAPSHOT_NOT_SEEN"
        if (snapshot.originalStartTime != originalStartTime) return "START_TIME_CHANGED"
        if (nowMs - snapshot.storedAtMs !in 0L..MAX_REUSE_AGE_MS) return "SNAPSHOT_EXPIRED"
        if (snapshot.history.candles.size < MIN_BASE_ROWS) return "INSUFFICIENT_BASE_ROWS"
        if (!strictlyIncreasing(snapshot.history.candles)) return "INVALID_CANDLE_FORMAT_OR_ORDER"
        if (epochSeconds(snapshot.history.candles[snapshot.history.candles.size - 3]) == null) return "TIMESTAMP_UNSUPPORTED"
        return "UNKNOWN"
    }

    @Synchronized
    fun request(symbol: String, originalStartTime: String, nowMs: Long): IncrementalRequest? {
        val snapshot = cache[symbol] ?: return null
        if (snapshot.originalStartTime != originalStartTime || nowMs - snapshot.storedAtMs !in 0L..MAX_REUSE_AGE_MS) return null
        val rows = snapshot.history.candles
        if (rows.size < MIN_BASE_ROWS || !strictlyIncreasing(rows)) return null
        // Repeat the last three bars; last bar can be unfinished and must be replaced.
        val anchor = epochSeconds(rows[rows.size - 3]) ?: return null
        val ts = Instant.ofEpochSecond(anchor).atZone(INDIA).toLocalDateTime()
        return IncrementalRequest(ts.format(format))
    }

    @Synchronized
    fun stitch(symbol: String, originalStartTime: String, incremental: HistoricalPayload, nowMs: Long): HistoricalPayload? {
        val prior = cache[symbol] ?: return null
        if (prior.originalStartTime != originalStartTime || nowMs - prior.storedAtMs !in 0L..MAX_REUSE_AGE_MS) return null
        val oldRows = prior.history.candles
        val rows = incremental.candles
        if (rows.size < 3 || !strictlyIncreasing(rows) || !strictlyIncreasing(oldRows)) return null
        val firstNew = epochSeconds(rows.first()) ?: return null
        // Fresh data must overlap the old window by at least two candle timestamps.
        val oldTimes = oldRows.map { epochSeconds(it) ?: return null }
        val newTimes = rows.map { epochSeconds(it) ?: return null }
        if (oldTimes.count { it in newTimes } < 2) return null
        if (newTimes.last() < oldTimes.last()) return null
        val prefix = oldRows.filterIndexed { index, _ -> oldTimes[index] < firstNew }
        val merged = prefix + rows
        if (!strictlyIncreasing(merged) || merged.size < MIN_BASE_ROWS) return null
        return incremental.copy(candles = merged, startTime = prior.history.startTime)
    }

    @Synchronized
    fun remember(symbol: String, originalStartTime: String, response: HistoricalPayload, nowMs: Long) {
        val reason = when {
            response.candles.size < MIN_BASE_ROWS -> "REJECTED_TOO_FEW_ROWS_" + response.candles.size
            !strictlyIncreasing(response.candles) -> "REJECTED_TIMESTAMP_OR_ORDER"
            else -> null
        }
        if (reason == null) {
            rejectionReasons.remove(symbol)
            cache[symbol] = Snapshot(nowMs, originalStartTime, response)
        } else {
            cache.remove(symbol)
            rejectionReasons[symbol] = reason
            // A small bounded diagnostic, never an unbounded memory store.
            while (rejectionReasons.size > MAX_STOCKS) rejectionReasons.remove(rejectionReasons.keys.first())
        }
    }

    private fun strictlyIncreasing(candles: List<List<JsonElement>>): Boolean {
        var previous = Long.MIN_VALUE
        for (candle in candles) {
            if (candle.size < 6) return false
            val timestamp = epochSeconds(candle) ?: return false
            if (timestamp <= previous) return false
            previous = timestamp
        }
        return true
    }

    private fun epochSeconds(row: List<JsonElement>): Long? {
        val raw = row.firstOrNull() ?: return null
        val value = runCatching { raw.asString }.getOrNull() ?: return null
        val parsed = value.toLongOrNull()?.let { if (it > 10_000_000_000L) it / 1000L else it }
            ?: value.toDoubleOrNull()?.takeIf { it.isFinite() && it > 1_500_000_000.0 }
                ?.toLong()?.let { if (it > 10_000_000_000L) it / 1000L else it }
            ?: runCatching { Instant.parse(value).epochSecond }.getOrNull()
            ?: runCatching { OffsetDateTime.parse(value).toEpochSecond() }.getOrNull()
            ?: runCatching { LocalDateTime.parse(value, format).atZone(INDIA).toEpochSecond() }.getOrNull()
            ?: runCatching { LocalDateTime.parse(value, DateTimeFormatter.ISO_LOCAL_DATE_TIME).atZone(INDIA).toEpochSecond() }.getOrNull()
            ?: runCatching { LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE).atStartOfDay(INDIA).toEpochSecond() }.getOrNull()
        return parsed?.takeIf { it in 1_500_000_000L..4_100_000_000L }
    }

    companion object {
        private val INDIA = ZoneId.of("Asia/Kolkata")
        private const val MAX_STOCKS = 128
        private const val MAX_REUSE_AGE_MS = 6L * 60_000L
        private const val MIN_BASE_ROWS = 10
    }
}
