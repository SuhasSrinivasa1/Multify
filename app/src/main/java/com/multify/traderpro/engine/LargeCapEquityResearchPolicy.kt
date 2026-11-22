package com.multify.traderpro.engine

import java.time.LocalDate
import kotlin.math.abs

/** Read-only NSE equity research. No BUY/SELL/GTT code may call these decisions as authorisation. */
data class EquityEodBar(
    val date: LocalDate,
    val symbol: String,
    val low: Double,
    val close: Double,
    val volume: Long
)
data class SixMonthLowObservation(
    val symbol: String,
    val close: Double,
    val sixMonthLow: Double,
    val distancePct: Double,
    val tradingDays: Int,
    val latestDate: LocalDate
)
data class LargeCapEquityObservation(
    val symbol: String,
    val sector: String,
    val close: Double,
    val fiveDayPct: Double,
    val twentyDayPct: Double,
    val sectorFiveDayPct: Double?,
    val sectorBreadthPct: Double?,
    val relativeFiveDayPct: Double?,
    val ema20: Double,
    val ema50: Double,
    val volumeAcceleration: Double?,
    val intradayPct: Double?,
    val intradayRelativePct: Double?,
    val intradayResearch: Boolean,
    val swingResearch: Boolean
)

object LargeCapEquityResearchPolicy {
    /** Maximum six calendar months, including the most recent completed EOD session. */
    fun nearSixMonthLow(
        bars: List<EquityEodBar>,
        asOf: LocalDate,
        latestCompletedEodDate: LocalDate,
        completedEodDays: Int
    ): SixMonthLowObservation? {
        if (completedEodDays < 90) return null
        val relevant = bars.filter { !it.date.isBefore(asOf.minusMonths(6)) &&
            !it.date.isAfter(asOf) && it.low.isFinite() && it.close.isFinite() &&
            it.low > 0.0 && it.close > 0.0 }.sortedBy { it.date }
        if (relevant.isEmpty() || relevant.last().date != latestCompletedEodDate) return null
        // A short, recently recorded window cannot masquerade as six calendar months.
        if (relevant.first().date.isAfter(asOf.minusMonths(5).plusDays(14))) return null
        if (relevant.size < (completedEodDays * 0.75).toInt().coerceAtLeast(70)) return null
        val low = relevant.minOf { it.low }
        val close = relevant.last().close
        val distance = (close / low - 1.0) * 100.0
        if (!distance.isFinite() || distance < -0.000001 || distance > 10.0 + 0.000001)
            return null
        return SixMonthLowObservation(relevant.last().symbol, close, low,
            distance.coerceAtLeast(0.0), relevant.size, latestCompletedEodDate)
    }

    /** Technical research on VERIFIED Nifty-100 constituents, not an automated trading strategy. */
    fun scoreLargeCap(
        symbol: String,
        sector: String,
        history: List<EquityEodBar>,
        peerFiveDayReturns: List<Double>,
        peerIntradayReturns: List<Double>,
        marketLivePrice: Double?,
        marketOpenPrice: Double?
    ): LargeCapEquityObservation? {
        val bars = history.filter { it.close.isFinite() && it.close > 0.0 &&
            it.low.isFinite() && it.low > 0.0 }.sortedBy { it.date }
        if (bars.size < 55) return null
        val closes = bars.map { it.close }
        val last = closes.last()
        fun returnPct(days: Int): Double = (last / closes[closes.lastIndex - days] - 1.0) * 100.0
        fun ema(period: Int): Double {
            val k = 2.0 / (period + 1.0)
            return closes.drop(1).fold(closes.first()) { prior, price -> k * price + (1.0-k) * prior }
        }
        val five = returnPct(5)
        val twenty = returnPct(20)
        val goodPeers = peerFiveDayReturns.filter { it.isFinite() }
        val sectorReturn = goodPeers.takeIf { it.size >= 3 }?.average()
        val breadth = goodPeers.takeIf { it.size >= 3 }
            ?.let { p -> p.count { it > 0.0 }.toDouble() / p.size * 100.0 }
        val relative = sectorReturn?.let { five - it }
        val baseVol = bars.dropLast(5).takeLast(20).map { it.volume.toDouble() }
            .filter { it > 0.0 }
        val latestVol = bars.takeLast(5).map { it.volume.toDouble() }.filter { it > 0.0 }
        val vol = if (baseVol.size >= 15 && latestVol.size >= 4) {
            (latestVol.average() / baseVol.average()).takeIf { it.isFinite() }
        } else null
        val live = if (marketLivePrice != null && marketOpenPrice != null &&
            marketLivePrice.isFinite() && marketLivePrice > 0.0 &&
            marketOpenPrice.isFinite() && marketOpenPrice > 0.0) {
            (marketLivePrice / marketOpenPrice - 1.0) * 100.0
        } else null
        val peers = peerIntradayReturns.filter { it.isFinite() }
        val liveRelative = if (live != null && peers.size >= 3) live - peers.average() else null
        val e20 = ema(20)
        val e50 = ema(50)
        val swing = last > e20 && e20 > e50 && five >= 1.0 && twenty >= 1.5 &&
            relative != null && relative >= 0.25 && (breadth ?: 0.0) >= 50.0
        val intraday = live != null && live >= 0.35 && liveRelative != null &&
            liveRelative >= 0.15 && (breadth ?: 0.0) >= 50.0
        return LargeCapEquityObservation(
            symbol, sector, last, five, twenty, sectorReturn, breadth,
            relative, e20, e50, vol, live, liveRelative, intraday, swing
        )
    }
}
