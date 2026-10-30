package com.multify.traderpro.engine
data class FixedEntrySafetyAssessment(val eligible:Boolean,val reasons:List<String>,val vwapExtensionAtr:Double?,val ema20ExtensionAtr:Double?,val recentImpulseAtr:Double?)
/** Publication-only guard. Frozen Fixed scoring, targets and SELL rules are not modified. */
object FixedEntrySafetyOverlay {
    const val VERSION="FIXED_ENTRY_SAFETY_V1"
    const val MAX_VWAP_EXTENSION_ATR=2.25
    const val MAX_EMA20_EXTENSION_ATR=2.0
    const val MAX_RECENT_IMPULSE_ATR=1.2
    const val MAX_SPREAD_BPS=40.0
    const val RSI_EXHAUSTION=75.0
    fun assess(f:FixedFeatureSnapshot):FixedEntrySafetyAssessment {
        val r=mutableListOf<String>()
        if(f.sessionCandleCount<1)r+="current-session candles unavailable"
        val atr=f.atr14?.takeIf{it.isFinite()&&it>0.0}
        if(atr==null)r+="ATR unavailable"
        val vw=f.vwap?.takeIf{it.isFinite()&&it>0.0}
        if(vw==null)r+="current-session VWAP unavailable"
        val ve=if(atr!=null&&vw!=null)(f.ltp-vw)/atr else null
        if(ve!=null&&ve>MAX_VWAP_EXTENSION_ATR)r+="late entry: "+fmt(ve)+" ATR above session VWAP"
        val e=f.ema20?.takeIf{it.isFinite()&&it>0.0}
        val ee=if(atr!=null&&e!=null)(f.ltp-e)/atr else null
        if(ee!=null&&ee>MAX_EMA20_EXTENSION_ATR)r+="late entry: "+fmt(ee)+" ATR above EMA20"
        if((f.rsi14?:0.0)>=RSI_EXHAUSTION&&((ve?:0.0)>=1.0||(ee?:0.0)>=1.0))r+="overbought momentum exhaustion (RSI "+fmt(f.rsi14?:0.0)+")"
        val spread=f.spreadBps
        if(spread==null||!spread.isFinite()||spread<0.0)r+="bid/ask spread unavailable or invalid"
        else if(spread>MAX_SPREAD_BPS)r+="bid/ask spread too wide ("+fmt(spread)+" bps)"
        if(f.prevHigh!=null&&f.lastHigh!=null&&f.lastClose!=null&&f.lastHigh>f.prevHigh&&f.lastClose<f.prevHigh)r+="confirmed failed breakout / bull-trap rejection"
        val impulse=if(atr!=null&&f.lastClose!=null&&f.prevClose!=null)(f.lastClose-f.prevClose)/atr else null
        if(impulse!=null&&impulse>MAX_RECENT_IMPULSE_ATR)r+="single-bar upward impulse too extended ("+fmt(impulse)+" ATR)"
        if((f.sessionHighDistancePct?:Double.POSITIVE_INFINITY)<=0.10&&(ve?:0.0)>1.5)r+="at session high after excessive VWAP extension"
        return FixedEntrySafetyAssessment(r.isEmpty(),r.distinct(),ve,ee,impulse)
    }
    private fun fmt(v:Double)=String.format(java.util.Locale.US,"%.2f",v)
}
