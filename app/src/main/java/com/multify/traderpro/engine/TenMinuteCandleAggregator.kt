package com.multify.traderpro.engine

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.lang.Math.floorDiv

/** Read-only 10m bars derived from two consecutive completed Groww 5m bars. */
object TenMinuteCandleAggregator {
    private const val FIVE_MINUTES_MS = 5L * 60_000L
    private const val TEN_MINUTES_MS = 10L * 60_000L
    private val nseZone = ZoneId.of("Asia/Kolkata")
    private val sessionOpen = LocalTime.of(9, 15)
    private val sessionClose = LocalTime.of(15, 30)

    fun aggregate(fiveMinuteBars: List<MarketCandleBar>, asOfMs: Long = Long.MAX_VALUE): List<MarketCandleBar> {
        val grouped = linkedMapOf<Pair<LocalDate, Long>, MutableList<MarketCandleBar>>()
        fiveMinuteBars.forEach { bar ->
            if (!valid(bar) || (asOfMs != Long.MAX_VALUE && bar.atMs + FIVE_MINUTES_MS > asOfMs)) return@forEach
            val local = Instant.ofEpochMilli(bar.atMs).atZone(nseZone)
            if (local.toLocalTime() < sessionOpen || local.toLocalTime() >= sessionClose) return@forEach
            val openMs = local.toLocalDate().atTime(sessionOpen).atZone(nseZone).toInstant().toEpochMilli()
            val offset = bar.atMs - openMs
            if (offset < 0L || offset % FIVE_MINUTES_MS != 0L) return@forEach
            val bucket = floorDiv(offset, TEN_MINUTES_MS)
            grouped.getOrPut(local.toLocalDate() to bucket) { mutableListOf() }.add(bar)
        }
        return grouped.mapNotNull { (key, rows) ->
            val openMs = key.first.atTime(sessionOpen).atZone(nseZone).toInstant().toEpochMilli()
            val startMs = openMs + key.second * TEN_MINUTES_MS
            val ordered = rows.sortedBy { it.atMs }
            if (ordered.map { it.atMs }.distinct().size != ordered.size) return@mapNotNull null
            val first = ordered.singleOrNull { it.atMs == startMs } ?: return@mapNotNull null
            val second = ordered.singleOrNull { it.atMs == startMs + FIVE_MINUTES_MS } ?: return@mapNotNull null
            MarketCandleBar(startMs, first.open, maxOf(first.high, second.high),
                minOf(first.low, second.low), second.close, first.volume + second.volume)
        }.sortedBy { it.atMs }
    }

    private fun valid(bar: MarketCandleBar): Boolean =
        bar.atMs > 0L && listOf(bar.open, bar.high, bar.low, bar.close, bar.volume).all { it.isFinite() } &&
            bar.open > 0.0 && bar.high >= maxOf(bar.open, bar.close) &&
            bar.low > 0.0 && bar.low <= minOf(bar.open, bar.close) && bar.close > 0.0 && bar.volume >= 0.0
}
