package com.multify.traderpro.engine

import kotlin.math.ln

data class FactorPoint(
    val name: String,
    val family: String,
    val score: Double,
    val raw: Double?
)

data class FactorFamilyScore(
    val name: String,
    val score: Double,
    val availableFactors: Int,
    val totalFactors: Int
)

data class MultifyFactorModel(
    val factors: List<FactorPoint>,
    val families: List<FactorFamilyScore>,
    val dominantFamily: String,
    val consensusScore: Double,
    val positiveFamilies: Int
)

object MultifyFactorFamilies {
    const val MOMENTUM_TREND = "MOMENTUM_TREND"
    const val PARTICIPATION_LIQUIDITY = "PARTICIPATION_LIQUIDITY"
    const val STRUCTURE_CONTEXT = "STRUCTURE_CONTEXT"
    const val FACTOR_COUNT = 40

    fun evaluate(e: StrategyEvaluation, marketRegime: String): MultifyFactorModel {
        val f = e.features
        val atr = f.atr14?.takeIf { it.isFinite() && it > 0.0 }
        val atrPct = atr?.let { it / f.ltp * 100.0 }
        val lastRange = if (f.lastHigh != null && f.lastLow != null) (f.lastHigh - f.lastLow).coerceAtLeast(0.0) else null
        val lastBody = if (f.lastOpen != null && f.lastClose != null) f.lastClose - f.lastOpen else null
        val votePositive = e.votes.count { it.score > 0.05 }
        val voteNegative = e.votes.count { it.score < -0.05 }
        val voteConsensus = if (votePositive + voteNegative > 0)
            (votePositive - voteNegative).toDouble() / (votePositive + voteNegative)
        else 0.0

        fun signScaled(v: Double?, scale: Double): Double =
            v?.takeIf { it.isFinite() }?.let { (it / scale).coerceIn(-1.0, 1.0) } ?: 0.0
        fun boolScore(v: Boolean?): Double = when (v) { true -> 1.0; false -> -1.0; null -> 0.0 }
        fun centered01(v: Double?): Double = v?.takeIf { it.isFinite() }?.let { ((it - 0.5) * 2.0).coerceIn(-1.0, 1.0) } ?: 0.0
        fun raw(name: String, family: String, value: Double?, score: Double) =
            FactorPoint(name, family, score.coerceIn(-1.0, 1.0), value?.takeIf { it.isFinite() })

        val factors = buildList {
            add(raw("day_change_pct", MOMENTUM_TREND, f.dayChangePct, signScaled(f.dayChangePct, 2.5)))
            add(raw("price_above_vwap", MOMENTUM_TREND, f.vwap?.let { f.ltp - it }, boolScore(f.vwap?.let { f.ltp > it })))
            add(raw("price_vs_ema9", MOMENTUM_TREND, f.ema9?.let { (f.ltp / it - 1.0) * 100.0 }, f.ema9?.let { signScaled((f.ltp / it - 1.0) * 100.0, 1.0) } ?: 0.0))
            add(raw("ema9_vs_ema20", MOMENTUM_TREND, if (f.ema9 != null && f.ema20 != null) (f.ema9 / f.ema20 - 1.0) * 100.0 else null, if (f.ema9 != null && f.ema20 != null) signScaled((f.ema9 / f.ema20 - 1.0) * 100.0, 0.8) else 0.0))
            add(raw("ema20_vs_ema50", MOMENTUM_TREND, if (f.ema20 != null && f.ema50 != null) (f.ema20 / f.ema50 - 1.0) * 100.0 else null, if (f.ema20 != null && f.ema50 != null) signScaled((f.ema20 / f.ema50 - 1.0) * 100.0, 1.2) else 0.0))
            add(raw("ema9_slope_atr", MOMENTUM_TREND, f.ema9Slope, signScaled(f.ema9Slope, 0.20)))
            add(raw("ema20_slope_atr", MOMENTUM_TREND, f.ema20Slope, signScaled(f.ema20Slope, 0.15)))
            add(raw("vwap_slope_atr", MOMENTUM_TREND, f.vwapSlope, signScaled(f.vwapSlope, 0.15)))
            add(raw("macd_histogram", MOMENTUM_TREND, f.macdHistogram, if (atr != null) signScaled(f.macdHistogram?.div(atr), 0.25) else 0.0))
            add(raw("macd_histogram_slope", MOMENTUM_TREND, f.macdHistogramSlope, signScaled(f.macdHistogramSlope, 0.12)))
            add(raw("rsi14", MOMENTUM_TREND, f.rsi14, f.rsi14?.let { ((it - 50.0) / 20.0).coerceIn(-1.0, 1.0) } ?: 0.0))
            add(raw("rsi_slope", MOMENTUM_TREND, f.rsiSlope, signScaled(f.rsiSlope, 8.0)))
            add(raw("trend_slope_atr", MOMENTUM_TREND, f.trendSlopeAtr, signScaled(f.trendSlopeAtr, 0.25)))
            add(raw("structure_score", MOMENTUM_TREND, f.structureScore, signScaled(f.structureScore, 0.65)))

            add(raw("relative_volume", PARTICIPATION_LIQUIDITY, f.rvol, f.rvol?.let { ((it - 1.0) / 0.8).coerceIn(-1.0, 1.0) } ?: 0.0))
            add(raw("volume_acceleration", PARTICIPATION_LIQUIDITY, f.volumeAcceleration, signScaled(f.volumeAcceleration, 0.8)))
            add(raw("green_volume_share", PARTICIPATION_LIQUIDITY, f.greenVolumeShare, centered01(f.greenVolumeShare)))
            add(raw("order_book_imbalance", PARTICIPATION_LIQUIDITY, f.orderBookImbalance, signScaled(f.orderBookImbalance, 0.5)))
            add(raw("spread_quality_bps", PARTICIPATION_LIQUIDITY, f.spreadBps, f.spreadBps?.let { (1.0 - it / 45.0).coerceIn(-1.0, 1.0) } ?: 0.0))
            add(raw("atr_pct_capacity", PARTICIPATION_LIQUIDITY, atrPct, atrPct?.let { ((it - 0.25) / 0.75).coerceIn(-1.0, 1.0) } ?: 0.0))
            add(raw("bollinger_width", PARTICIPATION_LIQUIDITY, f.bollWidth, f.bollWidth?.let { ((it - 0.010) / 0.025).coerceIn(-1.0, 1.0) } ?: 0.0))
            add(raw("session_high_proximity", PARTICIPATION_LIQUIDITY, f.sessionHighDistancePct, f.sessionHighDistancePct?.let { (1.0 - it / 1.5).coerceIn(-1.0, 1.0) } ?: 0.0))
            add(raw("session_low_support", PARTICIPATION_LIQUIDITY, f.sessionLowDistancePct, f.sessionLowDistancePct?.let { (it / 2.0).coerceIn(-1.0, 1.0) } ?: 0.0))
            add(raw("donchian_breakout", PARTICIPATION_LIQUIDITY, f.donchianHigh?.let { f.ltp - it }, boolScore(f.donchianHigh?.let { f.ltp > it })))
            add(raw("orb5_breakout", PARTICIPATION_LIQUIDITY, f.orb5High?.let { f.ltp - it }, boolScore(f.orb5High?.let { f.ltp > it })))
            add(raw("orb15_breakout", PARTICIPATION_LIQUIDITY, f.orb15High?.let { f.ltp - it }, boolScore(f.orb15High?.let { f.ltp > it })))
            add(raw("candle_body_impulse", PARTICIPATION_LIQUIDITY, if (lastRange != null && lastRange > 0.0 && lastBody != null) lastBody / lastRange else null, if (lastRange != null && lastRange > 0.0 && lastBody != null) (lastBody / lastRange).coerceIn(-1.0, 1.0) else 0.0))

            add(raw("range52_position", STRUCTURE_CONTEXT, f.range52Position, centered01(f.range52Position)))
            add(raw("market_cap_liquidity_proxy", STRUCTURE_CONTEXT, f.marketCap, f.marketCap?.takeIf { it > 0.0 }?.let { ((ln(it) - 15.0) / 6.0).coerceIn(-1.0, 1.0) } ?: 0.0))
            add(raw("previous_high_break", STRUCTURE_CONTEXT, if (f.prevHigh != null) f.ltp - f.prevHigh else null, boolScore(f.prevHigh?.let { f.ltp > it })))
            add(raw("previous_low_hold", STRUCTURE_CONTEXT, if (f.prevLow != null) f.ltp - f.prevLow else null, boolScore(f.prevLow?.let { f.ltp > it })))
            add(raw("higher_high", STRUCTURE_CONTEXT, if (f.lastHigh != null && f.prevHigh != null) f.lastHigh - f.prevHigh else null, boolScore(if (f.lastHigh != null && f.prevHigh != null) f.lastHigh > f.prevHigh else null)))
            add(raw("higher_low", STRUCTURE_CONTEXT, if (f.lastLow != null && f.prevLow != null) f.lastLow - f.prevLow else null, boolScore(if (f.lastLow != null && f.prevLow != null) f.lastLow > f.prevLow else null)))
            add(raw("close_vs_previous_close", STRUCTURE_CONTEXT, if (f.lastClose != null && f.prevClose != null) (f.lastClose / f.prevClose - 1.0) * 100.0 else null, if (f.lastClose != null && f.prevClose != null) signScaled((f.lastClose / f.prevClose - 1.0) * 100.0, 1.0) else 0.0))
            add(raw("close_location_last_range", STRUCTURE_CONTEXT, if (f.lastClose != null && f.lastLow != null && f.lastHigh != null && f.lastHigh > f.lastLow) (f.lastClose - f.lastLow) / (f.lastHigh - f.lastLow) else null, if (f.lastClose != null && f.lastLow != null && f.lastHigh != null && f.lastHigh > f.lastLow) centered01((f.lastClose - f.lastLow) / (f.lastHigh - f.lastLow)) else 0.0))
            add(raw("gap_vs_previous_close", STRUCTURE_CONTEXT, if (f.lastOpen != null && f.prevClose != null) (f.lastOpen / f.prevClose - 1.0) * 100.0 else null, if (f.lastOpen != null && f.prevClose != null) signScaled((f.lastOpen / f.prevClose - 1.0) * 100.0, 1.0) else 0.0))
            add(raw("last_range_vs_atr", STRUCTURE_CONTEXT, if (lastRange != null && atr != null && atr > 0.0) lastRange / atr else null, if (lastRange != null && atr != null && atr > 0.0) ((lastRange / atr - 1.0) / 1.5).coerceIn(-1.0, 1.0) else 0.0))
            add(raw("pullback_from_session_high", STRUCTURE_CONTEXT, f.sessionHighDistancePct, f.sessionHighDistancePct?.let { (0.6 - it).coerceIn(-1.0, 1.0) } ?: 0.0))
            add(raw("distance_above_session_low", STRUCTURE_CONTEXT, f.sessionLowDistancePct, f.sessionLowDistancePct?.let { ((it - 0.5) / 2.0).coerceIn(-1.0, 1.0) } ?: 0.0))
            add(raw("strategy_vote_consensus", STRUCTURE_CONTEXT, voteConsensus, voteConsensus.coerceIn(-1.0, 1.0)))
        }

        check(factors.size == FACTOR_COUNT) { "Expected $FACTOR_COUNT factors, got ${factors.size}" }

        val familyScores = listOf(MOMENTUM_TREND, PARTICIPATION_LIQUIDITY, STRUCTURE_CONTEXT).map { family ->
            val xs = factors.filter { it.family == family }
            val available = xs.count { it.raw != null }
            val mean = if (xs.isEmpty()) 0.0 else xs.map { it.score }.average()
            FactorFamilyScore(family, mean.coerceIn(-1.0, 1.0), available, xs.size)
        }

        val weights = when (marketRegime) {
            "BREADTH_BULL" -> mapOf(MOMENTUM_TREND to 1.15, PARTICIPATION_LIQUIDITY to 1.05, STRUCTURE_CONTEXT to 0.90)
            "BREADTH_WEAK" -> mapOf(MOMENTUM_TREND to 0.80, PARTICIPATION_LIQUIDITY to 0.90, STRUCTURE_CONTEXT to 1.20)
            else -> mapOf(MOMENTUM_TREND to 1.00, PARTICIPATION_LIQUIDITY to 1.00, STRUCTURE_CONTEXT to 1.00)
        }
        val numerator = familyScores.sumOf { it.score * (weights[it.name] ?: 1.0) }
        val denominator = familyScores.sumOf { weights[it.name] ?: 1.0 }.coerceAtLeast(1e-9)
        val consensus = (numerator / denominator).coerceIn(-1.0, 1.0)
        val dominant = familyScores.maxByOrNull { it.score * (weights[it.name] ?: 1.0) }?.name ?: MOMENTUM_TREND
        val positive = familyScores.count { it.score >= 0.08 }

        return MultifyFactorModel(
            factors = factors,
            families = familyScores,
            dominantFamily = dominant,
            consensusScore = consensus,
            positiveFamilies = positive
        )
    }

    fun rankingBonus(model: MultifyFactorModel): Double =
        (model.consensusScore * 0.12 + model.positiveFamilies * 0.015).coerceIn(-0.06, 0.15)
}
