package com.multify.traderpro.engine

import com.multify.traderpro.data.network.QuotePayload
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.max

/**
 * Opening momentum is derived from quote/volume observations already obtained by the
 * existing market scan. It deliberately does not request 5m candles or wait for a
 * completed same-day 15m candle. Quote receipt timestamps are client-observation times,
 * not verified exchange tick timestamps.
 */
internal object WorkingRangeOpeningMomentumPolicy {
    private val INDIA = ZoneId.of("Asia/Kolkata")
    val OPENING_WINDOW_END: LocalTime = LocalTime.of(9, 45)
    const val MIN_OPENING_SAMPLES = 3
    const val MIN_OPENING_MOMENTUM_PCT = 0.20

    data class QuoteSample(
        val observedAtMs: Long,
        val price: Double,
        val cumulativeVolume: Long?
    )

    data class Metrics(
        val openingMode: Boolean,
        val sampleCount: Int,
        val momentumPct: Double?,
        val volumePaceRvol: Double?,
        val volumeAcceleration: Double?,
        val greenVolumeShare: Double?,
        val signalBasis: String
    )

    fun assess(
        samples: List<QuoteSample>,
        quoteVolume: Long?,
        candles: List<WorkingRangeCandle>,
        observedAtMs: Long
    ): Metrics {
        val at = Instant.ofEpochMilli(observedAtMs).atZone(INDIA)
        val openingMode = at.toLocalTime().isBefore(OPENING_WINDOW_END)
        val daySamples = samples.filter {
            it.observedAtMs > 0L && it.price.isFinite() && it.price > 0.0 &&
                Instant.ofEpochMilli(it.observedAtMs).atZone(INDIA).toLocalDate() == at.toLocalDate()
        }.sortedBy { it.observedAtMs }
        val momentum = if (daySamples.size >= 2 && daySamples.first().price > 0.0)
            (daySamples.last().price / daySamples.first().price - 1.0) * 100.0 else null
        val pace = estimateVolumePaceRvol(quoteVolume, candles, observedAtMs)
        val acceleration = sampleVolumeAcceleration(daySamples)
        val greenShare = sampleGreenVolumeShare(daySamples)
        return Metrics(
            openingMode = openingMode,
            sampleCount = daySamples.size,
            momentumPct = momentum,
            volumePaceRvol = pace,
            volumeAcceleration = acceleration,
            greenVolumeShare = greenShare,
            signalBasis = if (openingMode) "LIVE_QUOTE_AND_CUMULATIVE_VOLUME_SAMPLES_PLUS_PRIOR_15M_TREND"
                else "COMPLETED_15M_SESSION_CANDLES_PLUS_LIVE_QUOTE"
        )
    }

    fun buildInputs(
        snapshot: WorkingRangeMarketSnapshot,
        quote: QuotePayload,
        observedAtMs: Long,
        samples: List<QuoteSample>,
        openingModeOverride: Boolean? = null
    ): WorkingRangeCandidatePolicy.Inputs {
        val metrics = assess(samples, quote.volume, snapshot.candles, observedAtMs)
        val openingMode = openingModeOverride ?: metrics.openingMode
        val at = Instant.ofEpochMilli(observedAtMs).atZone(INDIA)
        val today = at.toLocalDate()
        val session = snapshot.candles.filter { candle ->
            val local = Instant.ofEpochMilli(candle.atMs).atZone(INDIA)
            local.toLocalDate() == today &&
                !local.toLocalTime().isBefore(LocalTime.of(9, 15)) &&
                local.toLocalTime().isBefore(LocalTime.of(15, 30)) &&
                candle.atMs + 15L * 60_000L <= observedAtMs
        }.sortedBy { it.atMs }

        val sessionGreenShare = if (session.isNotEmpty()) {
            val green = session.filter { it.close >= it.open }.sumOf { it.volume }
            val total = session.sumOf { it.volume }
            if (total > 0.0) green / total else null
        } else null
        val sessionVolumeAcceleration = if (session.size >= 2) {
            val previous = session.dropLast(1).takeLast(3).map { it.volume }.average()
            if (previous > 0.0) session.last().volume / previous - 1.0 else null
        } else null

        val rvol = if (openingMode) metrics.volumePaceRvol
            else metrics.volumePaceRvol ?: snapshot.rvol
        val volumeAcceleration = if (openingMode) metrics.volumeAcceleration
            else sessionVolumeAcceleration ?: metrics.volumeAcceleration ?: snapshot.volumeAcceleration
        val greenShare = if (openingMode) metrics.greenVolumeShare
            else sessionGreenShare ?: metrics.greenVolumeShare ?: snapshot.greenVolumeShare
        val price = quote.lastPrice?.takeIf { it.isFinite() && it > 0.0 } ?: snapshot.price
        val bid = quote.bidPrice ?: quote.depth?.buy?.mapNotNull { it.price }?.maxOrNull()
        val ask = quote.offerPrice ?: quote.depth?.sell?.mapNotNull { it.price }?.minOrNull()
        val spread = snapshot.spreadBps ?: if (bid != null && ask != null && price > 0.0)
            max(0.0, ask - bid) / price * 10_000.0 else null
        val buyQty = quote.totalBuyQuantity ?: quote.bidQuantity
        val sellQty = quote.totalSellQuantity ?: quote.offerQuantity
        val imbalance = snapshot.orderBookImbalance ?: if (buyQty != null && sellQty != null &&
            buyQty + sellQty > 0.0) (buyQty - sellQty) / (buyQty + sellQty) else null
        val minutesToClose = Duration.between(at.toLocalTime(), LocalTime.of(15, 30))
            .toMillis() / 60_000.0
        return WorkingRangeCandidatePolicy.Inputs(
            price = price,
            atr = snapshot.atr,
            rsi = snapshot.rsi,
            rvol = rvol,
            volumeAcceleration = volumeAcceleration,
            greenVolumeShare = greenShare,
            ema9 = snapshot.ema9,
            ema20 = snapshot.ema20,
            ema50 = snapshot.ema50,
            ema9Slope = snapshot.ema9Slope,
            ema20Slope = snapshot.ema20Slope,
            macdHistogramSlope = snapshot.macdHistogramSlope,
            vwap = snapshot.vwap ?: quote.averagePrice?.takeIf { it.isFinite() && it > 0.0 },
            range52Position = snapshot.range52Position,
            spreadBps = spread,
            orderBookImbalance = imbalance,
            minutesToClose = minutesToClose,
            rsiSlope = snapshot.rsiSlope,
            openingMode = openingMode,
            openingSampleCount = metrics.sampleCount,
            openingMomentumPct = metrics.momentumPct,
            openingVolumePaceRvol = metrics.volumePaceRvol,
            openingSignalBasis = metrics.signalBasis
        )
    }

    private fun estimateVolumePaceRvol(
        currentVolume: Long?,
        candles: List<WorkingRangeCandle>,
        observedAtMs: Long
    ): Double? {
        val volume = currentVolume?.takeIf { it >= 0L }?.toDouble() ?: return null
        val at = Instant.ofEpochMilli(observedAtMs).atZone(INDIA)
        val today = at.toLocalDate()
        val clock = at.toLocalTime()
        val historicalDays = candles.groupBy {
            Instant.ofEpochMilli(it.atMs).atZone(INDIA).toLocalDate()
        }.filterKeys { it < today }
        val expected = historicalDays.values.mapNotNull { dayBars ->
            val value = dayBars.sumOf { bar ->
                val local = Instant.ofEpochMilli(bar.atMs).atZone(INDIA)
                val start = local.toLocalTime()
                if (start < LocalTime.of(9, 15) || start >= LocalTime.of(15, 30)) return@sumOf 0.0
                val end = start.plusMinutes(15)
                val elapsed = when {
                    clock >= end -> 15.0
                    clock <= start -> 0.0
                    else -> Duration.between(start, clock).toMillis() / 60_000.0
                }.coerceIn(0.0, 15.0)
                bar.volume.coerceAtLeast(0.0) * elapsed / 15.0
            }
            value.takeIf { it > 0.0 && it.isFinite() }
        }
        if (expected.size < 2) return null
        val baseline = expected.average().takeIf { it > 0.0 } ?: return null
        return (volume / baseline).takeIf { it.isFinite() && it >= 0.0 }
    }

    private fun sampleVolumeAcceleration(samples: List<QuoteSample>): Double? {
        val lastThree = samples.takeLast(3)
        if (lastThree.size < 3) return null
        val (a, b, c) = lastThree
        val va = a.cumulativeVolume?.toDouble() ?: return null
        val vb = b.cumulativeVolume?.toDouble() ?: return null
        val vc = c.cumulativeVolume?.toDouble() ?: return null
        val dt1 = (b.observedAtMs - a.observedAtMs).takeIf { it > 0L }?.toDouble() ?: return null
        val dt2 = (c.observedAtMs - b.observedAtMs).takeIf { it > 0L }?.toDouble() ?: return null
        val rate1 = (vb - va).coerceAtLeast(0.0) / dt1
        val rate2 = (vc - vb).coerceAtLeast(0.0) / dt2
        if (rate1 <= 0.0) return null
        return (rate2 / rate1 - 1.0).takeIf { it.isFinite() }
    }

    private fun sampleGreenVolumeShare(samples: List<QuoteSample>): Double? {
        val recent = samples.takeLast(5)
        if (recent.size < 2) return null
        var green = 0.0
        var red = 0.0
        recent.zipWithNext().forEach { (previous, current) ->
            val before = previous.cumulativeVolume?.toDouble() ?: return@forEach
            val after = current.cumulativeVolume?.toDouble() ?: return@forEach
            val delta = (after - before).coerceAtLeast(0.0)
            if (delta <= 0.0) return@forEach
            if (current.price > previous.price) green += delta
            else if (current.price < previous.price) red += delta
        }
        val total = green + red
        return if (total > 0.0) green / total else null
    }
}
