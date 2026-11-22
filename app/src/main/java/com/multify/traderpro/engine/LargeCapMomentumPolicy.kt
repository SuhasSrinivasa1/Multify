package com.multify.traderpro.engine

import kotlin.math.abs

data class LargeCapIntradayInput(
    val symbol: String,
    val companyName: String,
    val sector: String,
    val currentPrice: Double,
    val firstObservedPrice: Double,
    val previousSweepPrice: Double
)

data class LargeCapIntradaySignal(
    val symbol: String,
    val companyName: String,
    val sector: String,
    val price: Double,
    val dayMovePct: Double,
    val lastSweepMovePct: Double,
    val relativeStrengthPct: Double,
    val sectorBreadthPct: Double,
    val asOfMs: Long
)

data class LargeCapSwingInput(
    val symbol: String,
    val companyName: String,
    val sector: String,
    val closes: List<Double>,
    val highs: List<Double>,
    val lows: List<Double>,
    val volumes: List<Long>,
    val lastDate: String
)

data class LargeCapSwingSignal(
    val symbol: String,
    val companyName: String,
    val sector: String,
    val lastClose: Double,
    val fiveDayReturnPct: Double,
    val twentyDayReturnPct: Double,
    val rsi14: Double?,
    val volumeRatio20: Double?,
    val sma20: Double?,
    val sma50: Double?,
    val sixMonthLowDistancePct: Double?,
    val stage: String,
    val asOfDate: String
)

object LargeCapMomentumPolicy {
    fun intraday(inputs: List<LargeCapIntradayInput>, asOfMs: Long): List<LargeCapIntradaySignal> {
        val valid = inputs.filter {
            it.currentPrice.isFinite() && it.currentPrice > 0.0 &&
                it.firstObservedPrice.isFinite() && it.firstObservedPrice > 0.0 &&
                it.previousSweepPrice.isFinite() && it.previousSweepPrice > 0.0
        }
        if (valid.isEmpty()) return emptyList()
        val dayMoves = valid.associate { it.symbol to pct(it.currentPrice, it.firstObservedPrice) }
        val marketAverage = dayMoves.values.average()
        val sectorBreadth = valid.groupBy { it.sector }.mapValues { (_, members) ->
            members.count { (dayMoves[it.symbol] ?: 0.0) > 0.0 }.toDouble() /
                members.size.coerceAtLeast(1) * 100.0
        }
        return valid.map { row ->
            val day = dayMoves[row.symbol] ?: 0.0
            LargeCapIntradaySignal(
                symbol = row.symbol,
                companyName = row.companyName,
                sector = row.sector,
                price = row.currentPrice,
                dayMovePct = day,
                lastSweepMovePct = pct(row.currentPrice, row.previousSweepPrice),
                relativeStrengthPct = day - marketAverage,
                sectorBreadthPct = sectorBreadth[row.sector] ?: 0.0,
                asOfMs = asOfMs
            )
        }.sortedWith(compareByDescending<LargeCapIntradaySignal> { it.relativeStrengthPct }
            .thenByDescending { it.dayMovePct })
    }

    fun swing(input: LargeCapSwingInput): LargeCapSwingSignal? {
        val n = minOf(input.closes.size, input.highs.size, input.lows.size, input.volumes.size)
        if (n < 55) return null
        val closes = input.closes.takeLast(n)
        val highs = input.highs.takeLast(n)
        val lows = input.lows.takeLast(n)
        val volumes = input.volumes.takeLast(n)
        val last = closes.lastOrNull()?.takeIf { it.isFinite() && it > 0.0 } ?: return null
        val fiveBase = closes.getOrNull(n - 6)?.takeIf { it > 0.0 } ?: return null
        val twentyBase = closes.getOrNull(n - 21)?.takeIf { it > 0.0 } ?: return null
        val fiveReturn = pct(last, fiveBase)
        val twentyReturn = pct(last, twentyBase)
        val sma20 = closes.takeLast(20).average()
        val sma50 = closes.takeLast(50).average()
        val rsi = rsi14(closes)
        val averageVolume = volumes.dropLast(1).takeLast(20).average().takeIf { it > 0.0 }
        val volumeRatio = averageVolume?.let { volumes.last().toDouble() / it }
        val prior20High = highs.dropLast(1).takeLast(20).maxOrNull()
        val sixMonthLow = lows.minOrNull()?.takeIf { it > 0.0 }
        val lowDistance = sixMonthLow?.let { pct(last, it) }
        val stage = when {
            prior20High != null && last > prior20High && (volumeRatio ?: 0.0) >= 1.20 -> "BREAKOUT"
            fiveReturn >= 1.0 && last > sma20 && (rsi ?: 0.0) in 50.0..72.0 &&
                (volumeRatio ?: 0.0) >= 1.05 -> "EARLY MOMENTUM"
            last > sma20 && sma20 > sma50 && twentyReturn >= 2.0 -> "CONTINUATION"
            last > sma50 && fiveReturn < 0.0 && twentyReturn > 0.0 -> "PULLBACK"
            (rsi ?: 0.0) >= 75.0 && fiveReturn < twentyReturn / 4.0 -> "EXHAUSTION"
            else -> "WATCH"
        }
        return LargeCapSwingSignal(
            symbol = input.symbol, companyName = input.companyName, sector = input.sector,
            lastClose = last, fiveDayReturnPct = fiveReturn, twentyDayReturnPct = twentyReturn,
            rsi14 = rsi, volumeRatio20 = volumeRatio, sma20 = sma20, sma50 = sma50,
            sixMonthLowDistancePct = lowDistance, stage = stage, asOfDate = input.lastDate
        )
    }

    fun pct(price: Double, reference: Double): Double =
        if (price.isFinite() && reference.isFinite() && price > 0.0 && reference > 0.0)
            (price / reference - 1.0) * 100.0 else 0.0

    private fun rsi14(closes: List<Double>): Double? {
        if (closes.size < 15) return null
        val changes = closes.takeLast(15).zipWithNext { a, b -> b - a }
        val gain = changes.filter { it > 0.0 }.sum() / 14.0
        val loss = -changes.filter { it < 0.0 }.sum() / 14.0
        if (loss == 0.0) return if (gain > 0.0) 100.0 else 50.0
        val rs = gain / loss
        return 100.0 - 100.0 / (1.0 + rs)
    }
}
