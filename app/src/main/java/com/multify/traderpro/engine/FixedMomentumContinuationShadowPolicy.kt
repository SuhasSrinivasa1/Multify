package com.multify.traderpro.engine

/** Evidence-only continuation fingerprint. Never participates in Fixed decisions,
 * BUY, SELL, GTT or target rules. Uses already evaluated 15m features only. */
internal object FixedMomentumContinuationShadowPolicy {
    internal data class Inputs(
        val rsi: Double? = null, val rsiSlope: Double? = null,
        val rvol: Double? = null, val volumeAcceleration: Double? = null,
        val greenVolumeShare: Double? = null, val macdHistogramSlope: Double? = null,
        val ema9: Double? = null, val ema20: Double? = null, val ema50: Double? = null,
        val ema9Slope: Double? = null, val ema20Slope: Double? = null,
        val vwapSlope: Double? = null, val range52Position: Double? = null,
        val candidateAgeMinutes: Double? = null,
        val lastCompleted15mVolume: Double? = null,
        val previousCompleted15mVolume: Double? = null
    )
    fun observe(e: FixedStrategyEvaluation): Map<String, Any?> {
        val f = e.features
        return evaluate(Inputs(
            rsi=f.rsi14, rsiSlope=f.rsiSlope, rvol=f.rvol,
            volumeAcceleration=f.volumeAcceleration, greenVolumeShare=f.greenVolumeShare,
            macdHistogramSlope=f.macdHistogramSlope,
            ema9=f.ema9, ema20=f.ema20, ema50=f.ema50,
            ema9Slope=f.ema9Slope, ema20Slope=f.ema20Slope, vwapSlope=f.vwapSlope,
            range52Position=f.range52Position,
            lastCompleted15mVolume=f.candles.lastOrNull()?.volume,
            previousCompleted15mVolume=f.candles.dropLast(1).lastOrNull()?.volume
        ))
    }
    internal fun evaluate(i: Inputs): Map<String, Any?> {
        fun n(x: Double?): Double? = x?.takeIf { it.isFinite() }
        val rvol=n(i.rvol); val accel=n(i.volumeAcceleration)
        val green=n(i.greenVolumeShare); val macd=n(i.macdHistogramSlope)
        val rsiSlope=n(i.rsiSlope); val range52=n(i.range52Position)
        val e9=n(i.ema9); val e20=n(i.ema20); val e50=n(i.ema50)
        val s9=n(i.ema9Slope); val s20=n(i.ema20Slope); val sv=n(i.vwapSlope)
        val emaAligned=if(e9!=null&&e20!=null&&e50!=null) e9>e20&&e20>e50 else null
        val slopeAligned=if(s9!=null&&s20!=null&&sv!=null) s9>0&&s20>0&&sv>0 else null
        val participation=if(rvol!=null&&accel!=null&&green!=null)
            rvol>=3&&accel>0&&green>=0.65 else null
        val continuation=if(participation!=null&&emaAligned!=null&&slopeAligned!=null&&macd!=null)
            participation&&emaAligned&&slopeAligned&&macd>0 else null
        val mature=if(range52!=null&&rvol!=null&&accel!=null&&macd!=null)
            range52>=0.90&&rvol<1.10&&accel<0&&macd<0 else null
        val slowing=if(rsiSlope!=null&&accel!=null&&macd!=null)
            rsiSlope<0&&accel<=0&&macd<=0 else null
        val last=n(i.lastCompleted15mVolume)?.takeIf { it>=0 }
        val previous=n(i.previousCompleted15mVolume)?.takeIf { it>0 }
        val volumeRatio=if(last!=null&&previous!=null) last/previous else null
        val age=n(i.candidateAgeMinutes)?.takeIf { it>=0 }
        val label=when {
            continuation==true -> "FRESH_PARTICIPATION_CONTINUATION_HYPOTHESIS"
            mature==true -> "MATURE_WEAK_PARTICIPATION_HYPOTHESIS"
            slowing==true -> "JOINT_MOMENTUM_DECELERATION_HYPOTHESIS"
            continuation==null||slowing==null -> "INSUFFICIENT_DATA"
            else -> "MIXED_RESEARCH_ONLY"
        }
        val missing=linkedMapOf("rvol" to rvol,"volume_acceleration" to accel,
            "green_volume_share" to green,"rsi_slope" to rsiSlope,
            "macd_histogram_slope" to macd,"ema_alignment" to emaAligned,
            "slope_alignment" to slopeAligned,"range52_position" to range52,
            "15m_volume_ratio" to volumeRatio,"candidate_age_minutes" to age)
            .filterValues { it==null }.keys.toList()
        return mapOf(
            "schema" to "fixed_momentum_continuation_shadow_v9019",
            "research_only" to true, "extra_broker_calls" to 0,
            "changes_fixed_selection_or_execution" to false,
            "working_range_certified" to false,
            "requires_independent_days_and_equal_horizons" to true,
            "label_hypothesis_not_verified_working_range" to label,
            "rsi" to n(i.rsi), "rsi_slope" to rsiSlope,
            "rvol" to rvol, "volume_acceleration" to accel,
            "green_volume_share" to green,"macd_histogram_slope" to macd,
            "ema_aligned" to emaAligned,"ema_vwap_slope_aligned" to slopeAligned,
            "range52_position" to range52,"candidate_age_minutes" to age,
            "last_completed_15m_volume_vs_previous" to volumeRatio,
            "continuation_combination_hypothesis" to continuation,
            "mature_weak_participation_hypothesis" to mature,
            "joint_deceleration_hypothesis" to slowing,
            "missing_input_names" to missing,"no_single_factor_veto" to true
        )
    }
}
