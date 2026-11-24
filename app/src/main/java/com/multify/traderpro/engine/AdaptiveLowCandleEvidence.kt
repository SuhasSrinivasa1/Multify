package com.multify.traderpro.engine

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Research-only candle shapes; never uses a candle after the observation. */
object AdaptiveLowCandleEvidence {
    private fun valid(b: EquityEodBar): Boolean {
        val o=b.open ?: return false
        val h=b.high ?: return false
        return listOf(o,h,b.low,b.close).all { it.isFinite() && it>0 } &&
            h>=max(o,b.close) && b.low<=min(o,b.close) && h>b.low
    }
    fun patternAt(bars: List<EquityEodBar>, i:Int):String? {
        val b=bars.getOrNull(i) ?: return null
        if(!valid(b))return null
        val o=b.open!!; val h=b.high!!; val range=h-b.low
        val body=abs(b.close-o)
        val lower=min(o,b.close)-b.low
        val upper=h-max(o,b.close)
        val prev=bars.getOrNull(i-1)?.takeIf{valid(it)}
        if(prev!=null && b.date>prev.date && prev.close<prev.open!! &&
            b.close>o && o<=prev.close && b.close>=prev.open!!)
            return "BULLISH_ENGULFING"
        if(lower>=max(2*body,range*0.45) && upper<=range*0.25 &&
            b.close>=b.low+range*0.55) return "HAMMER_SHAPE"
        if(prev!=null && b.date>prev.date && prev.close<prev.open!! &&
            b.close>o && o<prev.close && b.close>(prev.open!!+prev.close)/2 &&
            b.close<prev.open!!) return "PIERCING_SHAPE"
        if(b.close>o && body>=range*0.60 && b.close>=b.low+range*0.80)
            return "STRONG_BULLISH_CLOSE"
        if(body<=range*0.10)return "DOJI_SHAPE"
        return if(prev==null) "NO_SINGLE_CANDLE_PATTERN_PAIR_UNAVAILABLE" else "NO_TESTED_REVERSAL_SHAPE"
    }
    fun bullish(pattern:String?):Boolean = pattern in setOf(
        "BULLISH_ENGULFING","HAMMER_SHAPE","PIERCING_SHAPE","STRONG_BULLISH_CLOSE")
}
