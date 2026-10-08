package com.multify.traderpro.engine

import com.multify.traderpro.data.network.HistoricalPayload
import com.multify.traderpro.data.network.QuotePayload
import com.multify.traderpro.domain.ParsedSignal
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

data class Candle(
    val ts: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Double
)

data class FeatureSnapshot(
    val ltp: Double,
    val bid: Double?,
    val ask: Double?,
    val spreadBps: Double?,
    val orderBookImbalance: Double?,
    val dayChangePct: Double?,
    val marketCap: Double?,
    val range52Position: Double?,
    val vwap: Double?,
    val ema9: Double?,
    val ema20: Double?,
    val ema50: Double? = null,
    val ema9Slope: Double? = null,
    val ema20Slope: Double? = null,
    val vwapSlope: Double? = null,
    val macdHistogramSlope: Double? = null,
    val rsiSlope: Double? = null,
    val volumeAcceleration: Double? = null,
    val greenVolumeShare: Double? = null,
    val structureScore: Double? = null,
    val sessionHighDistancePct: Double? = null,
    val sessionLowDistancePct: Double? = null,
    val atr14: Double?,
    val rsi14: Double?,
    val bollWidth: Double?,
    val macdHistogram: Double?,
    val trendSlopeAtr: Double?,
    val rvol: Double?,
    val donchianHigh: Double?,
    val donchianLow: Double?,
    val orb5High: Double?,
    val orb5Low: Double?,
    val orb15High: Double?,
    val orb15Low: Double?,
    val lastOpen: Double?,
    val lastHigh: Double?,
    val lastLow: Double?,
    val lastClose: Double?,
    val prevOpen: Double?,
    val prevHigh: Double?,
    val prevLow: Double?,
    val prevClose: Double?,
    val candles: List<Candle>
)

data class StrategyVote(val name: String, val score: Double, val note: String)

data class StrategyEvaluation(
    val side: String,
    val regime: String,
    val directionalScore: Double,
    val confidence: Double,
    val strategy: String,
    val reason: String,
    val votes: List<StrategyVote>,
    val features: FeatureSnapshot
)

object LocalStrategyEngine {
    fun buildFeatures(quote: QuotePayload, historical: HistoricalPayload, intervalMinutes: Int = historical.intervalInMinutes ?: 5): FeatureSnapshot {
        val candles = historical.candles.mapIndexedNotNull { index, x ->
            if (x.size < 6) null else runCatching {
                Candle(
                    // Groww has returned both epoch-like and textual timestamps across historical APIs.
                    // Timestamp is not used for signal math, so keep a stable fallback when it is textual.
                    ts = x[0].takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asLong ?: index.toLong(),
                    open = x[1].asDouble,
                    high = x[2].asDouble,
                    low = x[3].asDouble,
                    close = x[4].asDouble,
                    volume = x[5].asDouble
                )
            }.getOrNull()
        }
        val ltp = quote.lastPrice ?: candles.lastOrNull()?.close ?: error("Groww quote does not contain LTP")
        val closes = candles.map { it.close }
        val vols = candles.map { it.volume }
        val prevWindow = if (candles.size > 21) candles.dropLast(1).takeLast(20) else candles.dropLast(1)
        val last = candles.lastOrNull()
        val prev = candles.dropLast(1).lastOrNull()
        val rv = if (vols.size >= 6) {
            val base = vols.dropLast(1).takeLast(20).average().takeIf { it > 0.0 }
            base?.let { vols.last() / it }
        } else null
        val (o5h, o5l) = openingRange(candles, 5, intervalMinutes)
        val (o15h, o15l) = openingRange(candles, 15, intervalMinutes)
        val bid = quote.bidPrice
        val ask = quote.offerPrice
        val atrNow = atr(candles, 14)
        val ema9Now = ema(closes, 9)
        val ema20Now = ema(closes, 20)
        val ema50Now = ema(closes, 50)
        val ema9Prev = ema(closes.dropLast(1), 9)
        val ema20Prev = ema(closes.dropLast(1), 20)
        val vwapNow = vwap(candles)
        val vwapPrev = vwap(candles.dropLast(1))
        val macdNow = macdHistogram(closes)
        val macdPrev = macdHistogram(closes.dropLast(1))
        val rsiNow = rsi(closes, 14)
        val rsiPrev = rsi(closes.dropLast(1), 14)
        val recentVols = vols.dropLast(1).takeLast(3)
        val volAccel = recentVols.takeIf { it.isNotEmpty() && it.average() > 0.0 }?.let { (vols.last() / it.average()) - 1.0 }
        val recentStructure = candles.takeLast(4)
        val structure = if (recentStructure.size >= 3) {
            var up = 0; var down = 0
            for (i in 1 until recentStructure.size) {
                if (recentStructure[i].high > recentStructure[i-1].high) up++ else if (recentStructure[i].high < recentStructure[i-1].high) down++
                if (recentStructure[i].low > recentStructure[i-1].low) up++ else if (recentStructure[i].low < recentStructure[i-1].low) down++
            }
            (up - down).toDouble() / (2.0 * (recentStructure.size - 1))
        } else null
        val recentTen = candles.takeLast(10)
        val greenVol = recentTen.filter { it.close >= it.open }.sumOf { it.volume }
        val redVol = recentTen.filter { it.close < it.open }.sumOf { it.volume }
        val greenShare = if (greenVol + redVol > 0.0) greenVol / (greenVol + redVol) else null
        val sessionHigh = candles.maxOfOrNull { it.high }
        val sessionLow = candles.minOfOrNull { it.low }
        return FeatureSnapshot(
            ltp = ltp,
            bid = bid,
            ask = ask,
            spreadBps = if (bid != null && ask != null && ltp > 0) max(0.0, ask - bid) / ltp * 10_000.0 else null,
            orderBookImbalance = run {
                val b = quote.totalBuyQuantity ?: quote.bidQuantity
                val a = quote.totalSellQuantity ?: quote.offerQuantity
                if (b != null && a != null && b + a > 0.0) (b - a) / (b + a) else null
            },
            dayChangePct = quote.dayChangePct,
            marketCap = quote.marketCap,
            range52Position = run {
                val lo = quote.week52Low; val hi = quote.week52High
                if (lo != null && hi != null && hi > lo) ((ltp - lo) / (hi - lo)).coerceIn(0.0, 1.0) else null
            },
            vwap = vwapNow,
            ema9 = ema9Now,
            ema20 = ema20Now,
            ema50 = ema50Now,
            ema9Slope = if (ema9Now != null && ema9Prev != null && atrNow != null && atrNow > 0.0) (ema9Now - ema9Prev) / atrNow else null,
            ema20Slope = if (ema20Now != null && ema20Prev != null && atrNow != null && atrNow > 0.0) (ema20Now - ema20Prev) / atrNow else null,
            vwapSlope = if (vwapNow != null && vwapPrev != null && atrNow != null && atrNow > 0.0) (vwapNow - vwapPrev) / atrNow else null,
            macdHistogramSlope = if (macdNow != null && macdPrev != null && atrNow != null && atrNow > 0.0) (macdNow - macdPrev) / atrNow else null,
            rsiSlope = if (rsiNow != null && rsiPrev != null) rsiNow - rsiPrev else null,
            volumeAcceleration = volAccel,
            greenVolumeShare = greenShare,
            structureScore = structure,
            sessionHighDistancePct = sessionHigh?.takeIf { it > 0.0 }?.let { (it - ltp) / it * 100.0 },
            sessionLowDistancePct = sessionLow?.takeIf { it > 0.0 }?.let { (ltp - it) / it * 100.0 },
            atr14 = atrNow,
            rsi14 = rsiNow,
            bollWidth = bollingerWidth(closes, 20),
            macdHistogram = macdNow,
            trendSlopeAtr = trendSlopeAtr(closes, atrNow),
            rvol = rv,
            donchianHigh = prevWindow.maxOfOrNull { it.high },
            donchianLow = prevWindow.minOfOrNull { it.low },
            orb5High = o5h, orb5Low = o5l,
            orb15High = o15h, orb15Low = o15l,
            lastOpen = last?.open, lastHigh = last?.high, lastLow = last?.low, lastClose = last?.close,
            prevOpen = prev?.open, prevHigh = prev?.high, prevLow = prev?.low, prevClose = prev?.close,
            candles = candles
        )
    }

    fun evaluateLong(signal: ParsedSignal, f: FeatureSnapshot, includeEventPrior: Boolean = true): StrategyEvaluation {
        val votes = mutableListOf<StrategyVote>()
        val regime = regime(f)
        val px = f.ltp
        val atr = max(f.atr14 ?: max(px * 0.004, 0.05), 1e-9)
        val rv = f.rvol ?: 1.0
        f.vwap?.let { vw ->
            val dv = (px - vw) / atr
            if (px > vw && (f.ema9 ?: px) >= (f.ema20 ?: px))
                votes += StrategyVote("VWAP trend continuation", min(1.0, .35 + .25 * rv), "${fmt(dv)} ATR above VWAP")
            else if (px < vw && regime == "MEAN_REVERSION" && dv > -1.1)
                votes += StrategyVote("VWAP mean reversion", .25, "balanced regime below VWAP")
        }
        if (f.ema9 != null && f.ema20 != null) {
            val sep = (f.ema9 - f.ema20) / atr
            votes += StrategyVote("EMA 9/20 trend", (sep * .9).coerceIn(-.8, .8), "EMA separation ${fmt(sep)} ATR")
        }
        f.ema9Slope?.let { votes += StrategyVote("EMA9 slope", (it * 1.1).coerceIn(-.40,.40), "${fmt(it)} ATR/bar") }
        f.ema20Slope?.let { votes += StrategyVote("EMA20 slope", (it * .9).coerceIn(-.35,.35), "${fmt(it)} ATR/bar") }
        f.vwapSlope?.let { votes += StrategyVote("VWAP slope", (it * 1.0).coerceIn(-.35,.35), "${fmt(it)} ATR/bar") }
        f.macdHistogramSlope?.let { votes += StrategyVote("MACD histogram acceleration", (it * .8).coerceIn(-.30,.30), "${fmt(it)} ATR/bar") }
        f.rsiSlope?.let { votes += StrategyVote("RSI slope", (it / 20.0).coerceIn(-.25,.25), "delta RSI ${fmt(it)}") }
        f.structureScore?.let { votes += StrategyVote("Higher-high / higher-low structure", (it * .45).coerceIn(-.45,.45), "structure ${fmt(it)}") }
        f.volumeAcceleration?.let { votes += StrategyVote("Volume acceleration", (it * .20).coerceIn(-.25,.30), "volume change ${fmt(it)}") }
        f.greenVolumeShare?.let { votes += StrategyVote("Green/red volume balance", ((it - .5) * .70).coerceIn(-.35,.35), "green share ${fmt(it * 100)}%") }
        if (f.orb5High != null && px > f.orb5High && rv > 1.15) votes += StrategyVote("5-min opening range breakout", .55, "ORB5 + RVOL")
        if (f.orb15High != null && px > f.orb15High && rv > 1.10) votes += StrategyVote("15-min opening range breakout", .45, "ORB15 + RVOL")
        if (f.donchianHigh != null && px > f.donchianHigh && rv > 1.10) votes += StrategyVote("Donchian breakout", .50, "short-window high")
        if (f.prevHigh != null && f.lastHigh != null && f.lastClose != null && f.lastHigh > f.prevHigh && f.lastClose < f.prevHigh)
            votes += StrategyVote("Failed breakout / bull trap", -.65, "prior high swept then rejected")
        if (f.prevLow != null && f.lastLow != null && f.lastClose != null && f.lastLow < f.prevLow && f.lastClose > f.prevLow)
            votes += StrategyVote("Failed breakdown / bear trap", .65, "prior low swept then reclaimed")
        f.rsi14?.let { rsi ->
            if (regime == "TREND" && rsi > 55) votes += StrategyVote("RSI trend regime", .28, "RSI ${fmt(rsi)}")
            else if (rsi > 75) votes += StrategyVote("RSI exhaustion filter", -.18, "RSI ${fmt(rsi)}")
            else if (rsi < 28) votes += StrategyVote("RSI exhaustion reversal", .22, "RSI ${fmt(rsi)}")
        }
        f.macdHistogram?.let { h ->
            votes += StrategyVote("MACD momentum", (h / atr * .55).coerceIn(-.45, .45), "hist ${fmt(h)}")
        }
        f.trendSlopeAtr?.let { slope ->
            votes += StrategyVote("Price structure slope", (slope * .8).coerceIn(-.45, .45), "${fmt(slope)} ATR/bar")
        }
        if (f.ema20 != null && px > f.ema20 + atr * 1.45 && rv > 1.15)
            votes += StrategyVote("Keltner-style expansion", .34, "price expanded above EMA20 with RVOL")
        if ((f.bollWidth ?: 1.0) < .010 && rv > 1.25 && (f.donchianHigh?.let { px > it } == true))
            votes += StrategyVote("Volatility squeeze breakout", .48, "compression released on volume")
        if (rv > 1.60 && f.lastClose != null && f.lastOpen != null && f.lastClose > f.lastOpen)
            votes += StrategyVote("Volume-price impulse", .32, "RVOL ${fmt(rv)} with green impulse")
        votes += candleVotes(f)
        f.orderBookImbalance?.let { im ->
            votes += StrategyVote("Order-book imbalance", (im * .55).coerceIn(-.45, .45), "imbalance ${fmt(im)}")
        }
        f.dayChangePct?.let { dc ->
            votes += StrategyVote("Day momentum context", (dc / 4.0).coerceIn(-.25, .25), "day ${fmt(dc)}%")
        }
        f.range52Position?.let { pos ->
            if (pos > .85) votes += StrategyVote("52-week strength context", .08, "near upper yearly range")
        }
        if (includeEventPrior) votes += StrategyVote("Multify event prior", .30, "fresh equity intraday release")
        when (regime) {
            "TREND" -> votes += StrategyVote("Regime switch", .20, "trend families enabled")
            "MEAN_REVERSION" -> votes += StrategyVote("Regime switch", -.05, "chase penalty")
            "COMPRESSION" -> votes += StrategyVote("Squeeze filter", if (rv > 1.1) .10 else -.05, "compression regime")
        }
        return finish("LONG", regime, votes, f)
    }

    fun evaluateShort(f: FeatureSnapshot, includeEventPrior: Boolean = true): StrategyEvaluation {
        val votes = mutableListOf<StrategyVote>()
        val regime = regime(f)
        val px = f.ltp
        val atr = max(f.atr14 ?: max(px * 0.004, 0.05), 1e-9)
        val rv = f.rvol ?: 1.0
        f.vwap?.let { vw ->
            val dv = (vw - px) / atr
            if (px < vw && (f.ema9 ?: px) <= (f.ema20 ?: px))
                votes += StrategyVote("VWAP bearish continuation", min(1.0, .35 + .25 * rv), "${fmt(dv)} ATR below VWAP")
            else if (px > vw) votes += StrategyVote("VWAP reclaim against short", -.40, "price above VWAP")
        }
        if (f.ema9 != null && f.ema20 != null) {
            val sep = (f.ema20 - f.ema9) / atr
            votes += StrategyVote("EMA 9/20 bear trend", (sep * .9).coerceIn(-.8, .8), "bear separation ${fmt(sep)} ATR")
        }
        f.ema9Slope?.let { votes += StrategyVote("EMA9 bear slope", (-it * 1.1).coerceIn(-.40,.40), "${fmt(it)} ATR/bar") }
        f.ema20Slope?.let { votes += StrategyVote("EMA20 bear slope", (-it * .9).coerceIn(-.35,.35), "${fmt(it)} ATR/bar") }
        f.vwapSlope?.let { votes += StrategyVote("VWAP bear slope", (-it * 1.0).coerceIn(-.35,.35), "${fmt(it)} ATR/bar") }
        f.macdHistogramSlope?.let { votes += StrategyVote("MACD histogram downside acceleration", (-it * .8).coerceIn(-.30,.30), "${fmt(it)} ATR/bar") }
        f.rsiSlope?.let { votes += StrategyVote("RSI bear slope", (-it / 20.0).coerceIn(-.25,.25), "delta RSI ${fmt(it)}") }
        f.structureScore?.let { votes += StrategyVote("Lower-high / lower-low structure", (-it * .45).coerceIn(-.45,.45), "structure ${fmt(it)}") }
        f.volumeAcceleration?.let { votes += StrategyVote("Volume acceleration", (it * .20).coerceIn(-.25,.30), "volume change ${fmt(it)}") }
        f.greenVolumeShare?.let { votes += StrategyVote("Red/green volume balance", ((.5 - it) * .70).coerceIn(-.35,.35), "green share ${fmt(it * 100)}%") }
        if (f.orb5Low != null && px < f.orb5Low && rv > 1.15) votes += StrategyVote("5-min opening range breakdown", .55, "ORB5 + RVOL")
        if (f.orb15Low != null && px < f.orb15Low && rv > 1.10) votes += StrategyVote("15-min opening range breakdown", .45, "ORB15 + RVOL")
        if (f.donchianLow != null && px < f.donchianLow && rv > 1.10) votes += StrategyVote("Donchian breakdown", .50, "short-window low")
        if (f.prevHigh != null && f.lastHigh != null && f.lastClose != null && f.lastHigh > f.prevHigh && f.lastClose < f.prevHigh)
            votes += StrategyVote("Failed breakout / bull trap", .70, "upside liquidity sweep failed")
        if (f.prevLow != null && f.lastLow != null && f.lastClose != null && f.lastLow < f.prevLow && f.lastClose > f.prevLow)
            votes += StrategyVote("Failed breakdown / bear trap", -.65, "downside sweep reclaimed")
        f.rsi14?.let { rsi ->
            if (regime == "TREND" && rsi < 45) votes += StrategyVote("RSI bear trend", .28, "RSI ${fmt(rsi)}")
            else if (rsi < 25) votes += StrategyVote("Oversold short-chase filter", -.30, "RSI ${fmt(rsi)}")
        }
        f.macdHistogram?.let { h ->
            votes += StrategyVote("MACD bear momentum", (-h / atr * .55).coerceIn(-.45, .45), "hist ${fmt(h)}")
        }
        f.trendSlopeAtr?.let { slope ->
            votes += StrategyVote("Price structure slope", (-slope * .8).coerceIn(-.45, .45), "${fmt(slope)} ATR/bar")
        }
        if (f.ema20 != null && px < f.ema20 - atr * 1.45 && rv > 1.15)
            votes += StrategyVote("Keltner-style breakdown", .34, "price expanded below EMA20 with RVOL")
        if ((f.bollWidth ?: 1.0) < .010 && rv > 1.25 && (f.donchianLow?.let { px < it } == true))
            votes += StrategyVote("Volatility squeeze breakdown", .48, "compression released on volume")
        if (rv > 1.60 && f.lastClose != null && f.lastOpen != null && f.lastClose < f.lastOpen)
            votes += StrategyVote("Volume-price downside impulse", .32, "RVOL ${fmt(rv)} with red impulse")
        candleVotes(f).forEach { votes += it.copy(score = -it.score, note = "short-side conversion") }
        f.orderBookImbalance?.let { im ->
            votes += StrategyVote("Order-book imbalance", (-im * .55).coerceIn(-.45, .45), "imbalance ${fmt(im)}")
        }
        f.dayChangePct?.let { dc ->
            votes += StrategyVote("Day momentum context", (-dc / 4.0).coerceIn(-.25, .25), "day ${fmt(dc)}%")
        }
        f.range52Position?.let { pos ->
            if (pos < .15) votes += StrategyVote("52-week weakness context", .08, "near lower yearly range")
        }
        if (includeEventPrior) votes += StrategyVote("Multify sell event prior", .28, "fresh book-profit reaction")
        if (regime == "TREND") votes += StrategyVote("Regime switch", .18, "bear-trend families enabled")
        else if (regime == "MEAN_REVERSION") votes += StrategyVote("Short chase penalty", -.08, "balanced market")
        return finish("SHORT", regime, votes, f)
    }

    private fun finish(side: String, regime: String, votes: List<StrategyVote>, f: FeatureSnapshot): StrategyEvaluation {
        val raw = votes.sumOf { it.score }
        val score = (raw / max(2.5, votes.size * .32)).coerceIn(-1.0, 1.0)
        val confidence = (0.50 + score * 0.42).coerceIn(0.05, 0.95)
        val best = votes.maxByOrNull { abs(it.score) }
        val top = votes.sortedByDescending { abs(it.score) }.take(3)
        val reason = top.joinToString(" · ") { "${it.name}: ${it.note}" }
        return StrategyEvaluation(side, regime, score, confidence, best?.name ?: "No validated setup", reason, votes, f)
    }

    private fun regime(f: FeatureSnapshot): String {
        val atr = f.atr14 ?: return "UNKNOWN"
        val e9 = f.ema9 ?: return "UNKNOWN"
        val e20 = f.ema20 ?: return "UNKNOWN"
        val sep = abs(e9 - e20) / max(atr, 1e-9)
        val rv = f.rvol ?: 1.0
        if (sep > .35 && rv >= 1.05) return "TREND"
        if (f.bollWidth != null && f.bollWidth < .008 && rv < 1.0) return "COMPRESSION"
        if (sep < .18 && rv < 1.2) return "MEAN_REVERSION"
        return "MIXED"
    }

    private fun candleVotes(f: FeatureSnapshot): List<StrategyVote> {
        val o = f.lastOpen ?: return emptyList()
        val h = f.lastHigh ?: return emptyList()
        val l = f.lastLow ?: return emptyList()
        val c = f.lastClose ?: return emptyList()
        val body = abs(c - o)
        val range = max(h - l, 1e-9)
        val lower = min(o, c) - l
        val upper = h - max(o, c)
        val out = mutableListOf<StrategyVote>()
        if (lower >= 2 * max(body, range * .08) && upper <= body * .8 && c >= o)
            out += StrategyVote("Hammer / bullish pin bar", .75, "lower-wick rejection")
        if (upper >= 2 * max(body, range * .08) && lower <= body * .8 && c <= o)
            out += StrategyVote("Shooting star / bearish pin bar", -.75, "upper-wick rejection")
        if (f.prevOpen != null && f.prevClose != null) {
            val po = f.prevOpen; val pc = f.prevClose
            if (pc < po && c > o && o <= pc && c >= po) out += StrategyVote("Bullish engulfing", .85, "body engulfed prior red candle")
            if (pc > po && c < o && o >= pc && c <= po) out += StrategyVote("Bearish engulfing", -.85, "body engulfed prior green candle")
        }
        if (body / range > .80) out += StrategyVote(if (c > o) "Bullish marubozu" else "Bearish marubozu", if (c > o) .65 else -.65, "large directional body")
        return out
    }

    private fun ema(values: List<Double>, period: Int): Double? {
        if (values.isEmpty()) return null
        val alpha = 2.0 / (period + 1.0)
        var e = values.first()
        values.drop(1).forEach { e = alpha * it + (1 - alpha) * e }
        return e
    }

    private fun atr(c: List<Candle>, period: Int): Double? {
        if (c.size < 2) return null
        val tr = mutableListOf<Double>()
        var prev = c.first().close
        c.drop(1).forEach {
            tr += max(it.high - it.low, max(abs(it.high - prev), abs(it.low - prev)))
            prev = it.close
        }
        return tr.takeLast(period).takeIf { it.isNotEmpty() }?.average()
    }

    private fun rsi(values: List<Double>, period: Int): Double? {
        if (values.size < period + 1) return null
        val xs = values.takeLast(period + 1)
        var gains = 0.0; var losses = 0.0
        for (i in 1 until xs.size) {
            val d = xs[i] - xs[i - 1]
            if (d >= 0) gains += d else losses -= d
        }
        val ag = gains / period; val al = losses / period
        if (al == 0.0) return 100.0
        val rs = ag / al
        return 100.0 - 100.0 / (1.0 + rs)
    }

    private fun vwap(c: List<Candle>): Double? {
        var n = 0.0; var d = 0.0
        c.forEach {
            val tp = (it.high + it.low + it.close) / 3.0
            n += tp * it.volume; d += it.volume
        }
        return if (d > 0) n / d else null
    }

    private fun bollingerWidth(values: List<Double>, period: Int): Double? {
        if (values.size < period) return null
        val xs = values.takeLast(period)
        val mean = xs.average()
        if (mean == 0.0) return null
        val sd = sqrt(xs.sumOf { (it - mean) * (it - mean) } / xs.size)
        return 4 * sd / mean
    }

    private fun macdHistogram(values: List<Double>): Double? {
        if (values.size < 26) return null
        val macdSeries = mutableListOf<Double>()
        for (i in values.indices) {
            val prefix = values.take(i + 1)
            if (prefix.size >= 26) {
                val fast = ema(prefix, 12) ?: continue
                val slow = ema(prefix, 26) ?: continue
                macdSeries += fast - slow
            }
        }
        if (macdSeries.isEmpty()) return null
        val signal = ema(macdSeries, 9) ?: return null
        return macdSeries.last() - signal
    }

    private fun trendSlopeAtr(values: List<Double>, atr: Double?): Double? {
        if (values.size < 5 || atr == null || atr <= 0.0) return null
        val xs = values.takeLast(min(8, values.size))
        return (xs.last() - xs.first()) / max(1, xs.size - 1) / atr
    }

    private fun openingRange(c: List<Candle>, minutes: Int, intervalMinutes: Int): Pair<Double?, Double?> {
        if (c.isEmpty()) return null to null
        val count = max(1, kotlin.math.ceil(minutes.toDouble() / max(1, intervalMinutes)).toInt())
        val selected = c.take(count)
        return selected.maxOfOrNull { it.high } to selected.minOfOrNull { it.low }
    }

    private fun fmt(x: Double) = String.format(java.util.Locale.US, "%.2f", x)
}
