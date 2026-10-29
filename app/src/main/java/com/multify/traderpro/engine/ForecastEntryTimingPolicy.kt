package com.multify.traderpro.engine

import kotlin.math.max
import kotlin.math.min

/** Forecast-only historical counterfactuals. Never called by Fixed or broker order routing.
 * Candle timestamps identify candle OPEN; orders enter at the NEXT candle open after a fully
 * observed signal/confirmation. A same-bar stop and target conservatively resolves to stop.
 * Research results are NOT fills and never use a known future low to pick an entry.
 */
object ForecastEntryTimingPolicy {
    const val VERSION = "V790_ONE_MIN_ENTRY_STUDY"
    const val HORIZON_MINUTES = 30L
    const val BASE_SLIPPAGE_BPS_PER_LEG = 2.0
    enum class Method { NOW, CONFIRM_1M, CONFIRM_3M, PULLBACK_RETEST }
    data class Bar(val atMs: Long, val open: Double, val high: Double, val low: Double,
                   val close: Double, val volume: Double)
    data class Outcome(
        val method: Method, val filled: Boolean, val entryAtMs: Long? = null,
        val entryPrice: Double? = null, val exitAtMs: Long? = null,
        val exitReason: String = "MISSED", val grossPct: Double? = null,
        val netPct: Double? = null, val mfePct: Double? = null, val maePct: Double? = null,
        val targetHit: Boolean = false
    )
    data class Study(val version: String, val signalAtMs: Long, val outcomes: List<Outcome>,
                     val hindsightBest: String?, val nextBarObserved: Boolean,
                     val dataQuality: String)
    data class Example(val date: String, val symbol: String, val atMs: Long,
                       val market: String, val time: String, val volume: String,
                       val structure: String, val outcomes: List<Outcome>)
    data class Advice(val method: Method, val validated: Boolean, val sampleSize: Int,
                      val trainingDays: Int, val holdoutSamples: Int, val holdoutDays: Int,
                      val estimatedImprovementPct: Double, val context: String,
                      val reason: String)

    fun timeBucket(epochMs: Long): String {
        val time = java.time.Instant.ofEpochMilli(epochMs)
            .atZone(java.time.ZoneId.of("Asia/Kolkata")).toLocalTime()
        val minutes = time.hour * 60 + time.minute
        return when {
            minutes < 10 * 60 + 30 -> "OPEN"
            minutes < 13 * 60 -> "MIDDAY"
            else -> "LATE"
        }
    }
    fun volumeBucket(rvol: Double?): String = when {
        rvol == null || !rvol.isFinite() -> "UNKNOWN"
        rvol >= 1.5 -> "HIGH"
        rvol >= .85 -> "NORMAL"
        else -> "LOW"
    }
    fun structureBucket(compression: Double?, rising: Boolean): String = when {
        compression != null && compression in .10..0.85 && rising -> "COIL_RISING"
        compression != null && compression in .10..0.85 -> "COIL"
        rising -> "RISING"
        else -> "OTHER"
    }
    fun marketBucket(market: String): String = when {
        market.contains("BULL", true) -> "BULL"
        market.contains("WEAK", true) || market.contains("BEAR", true) -> "BEAR"
        else -> "FLAT"
    }
    fun contextKey(market: String, time: String, volume: String, structure: String): String =
        "${marketBucket(market)}|$time|$volume|$structure"

    fun reconstruct(signalAtMs: Long, referencePrice: Double, targetPct: Double,
                    bars: List<Bar>, capitalRupees: Int = 200_000): Study {
        val clean = bars.asSequence().filter {
            it.atMs > 0 && it.open > 0 && it.high >= max(it.open, it.close) &&
                it.low > 0 && it.low <= min(it.open, it.close) &&
                it.close > 0 && it.volume >= 0 && listOf(it.open,it.high,it.low,it.close).all(Double::isFinite)
        }.distinctBy { it.atMs }.sortedBy { it.atMs }.toList()
        if (referencePrice <= 0 || targetPct <= 0 || clean.size < 4) {
            return Study(VERSION,signalAtMs,emptyList(),null,false,"INSUFFICIENT_BARS")
        }
        // A bar whose open is before signal time can straddle the signal: never use it.
        val first = clean.indexOfFirst { it.atMs > signalAtMs }
        if (first < 0) return Study(VERSION,signalAtMs,emptyList(),null,false,"NO_FUTURE_BARS")
        val lastStart = signalAtMs + HORIZON_MINUTES * 60_000L
        fun chooseIndex(method: Method): Int? {
            if (method == Method.NOW) return first
            if (method == Method.CONFIRM_1M || method == Method.CONFIRM_3M) {
                val count = if (method == Method.CONFIRM_1M) 1 else 3
                val confirm = first + count - 1
                val next = confirm + 1
                // Confirmation is only available at the bar CLOSE. Do not buy on the same bar.
                if (next >= clean.size || clean[confirm].atMs >= lastStart) return null
                if (clean[confirm].close < referencePrice * 1.0001) return null
                return next
            }
            // A pullback is only known after a completed candle; wait for the recovery
            // candle to CLOSE, then enter the following bar's OPEN. Never buy the hindsight low.
            var pulled = false
            for (i in first until clean.size - 1) {
                val bar=clean[i]
                if (bar.atMs >= lastStart - 60_000L) break
                if (!pulled && bar.close <= referencePrice * .9985) pulled=true
                else if (pulled && bar.close >= referencePrice * .999) return i+1
            }
            return null
        }
        val outcomes = Method.entries.map { method ->
            val idx=chooseIndex(method)
            if (idx==null || idx >=clean.size || clean[idx].atMs > lastStart - 60_000L) {
                Outcome(method,false)
            } else {
                val entry=clean[idx].open
                val target=entry*(1.0+targetPct/100.0)
                // Shared live SELL/trailing is untouched. A conservative risk proxy is used
                // only for retrospective comparison, capped to the same 30-minute horizon.
                val stop=entry*(1.0-.65/100.0)
                val within=clean.drop(idx).takeWhile { it.atMs < lastStart }
                var exit=within.lastOrNull()?.close ?: entry
                var exitAt=within.lastOrNull()?.atMs ?: clean[idx].atMs
                var reason="HORIZON_CLOSE"
                var hit=false
                var high=entry; var low=entry
                for (bar in within) {
                    high=max(high,bar.high);low=min(low,bar.low)
                    if (bar.low <= stop) { exit=stop;exitAt=bar.atMs;reason="RISK_PROXY";break }
                    if (bar.high >= target) { exit=target;exitAt=bar.atMs;reason="TARGET";hit=true;break }
                }
                val entryFill=entry*(1+BASE_SLIPPAGE_BPS_PER_LEG/10_000.0)
                val exitFill=exit*(1-BASE_SLIPPAGE_BPS_PER_LEG/10_000.0)
                val qty=(capitalRupees/entryFill).toInt()
                if(qty<=0) Outcome(method,false)
                else {
                    val fees=GrowwDeliveryCostPolicy.estimateCharges(entryFill,exitFill,qty).total
                    val capital=entryFill*qty
                    Outcome(method,true,clean[idx].atMs,entry,exitAt,reason,
                        (exit/entry-1)*100,
                        ((exitFill-entryFill)*qty-fees)/capital*100,
                        (high/entry-1)*100,(entry-low)/entry*100,hit)
                }
            }
        }
        val valid=outcomes.filter { it.filled && it.netPct != null }
        return Study(VERSION,signalAtMs,outcomes,valid.maxByOrNull { it.netPct!! }?.method?.name,
            true,if(clean.zipWithNext().any{ (a,b)-> b.atMs-a.atMs>180_000L && a.atMs>=signalAtMs && a.atMs < lastStart }) "GAPPED_BARS" else "OK")
    }

    private fun trimmedMean(values: List<Double>): Double {
        if(values.isEmpty())return 0.0
        val sorted=values.sorted()
        val cut=if(sorted.size>=10) (sorted.size*.1).toInt() else 0
        return sorted.drop(cut).dropLast(cut).average()
    }
    /** Chronological train/holdout, with unique symbol-day/time windows (handled upstream).
     * If any evidence gate is unmet, returns unmodified NOW. No forced adaptation.
     */
    fun advise(market: String,time: String,volume: String,structure: String,
               history: List<Example>, predictionDate: String): Advice {
        val key=contextKey(market,time,volume,structure)
        val prior=history.filter { it.date < predictionDate }
        val dates=prior.map { it.date }.distinct().sorted()
        fun fallback(reason:String)=Advice(Method.NOW,false,prior.size,dates.size,0,0,0.0,key,reason)
        if(prior.size<30 || dates.size<5 || prior.map{it.symbol}.distinct().size<8)
            return fallback("LEARNING: insufficient independent days/stocks")
        val holdoutDays=max(2,(dates.size*.25).toInt())
        val trainDates=dates.dropLast(holdoutDays).toSet()
        val validationDates=dates.takeLast(holdoutDays).toSet()
        val train=prior.filter{it.date in trainDates}
        val validation=prior.filter{it.date in validationDates}
        if(train.size<20 || validation.size<8 || train.map{it.symbol}.distinct().size<6)
            return fallback("LEARNING: walk-forward sample not mature")
        // Hierarchical averaging: sparse market/time/volume/structure cohorts borrow from
        // the global prior, rather than producing brittle high-confidence decisions.
        val local=train.filter{contextKey(it.market,it.time,it.volume,it.structure)==key}
        val broad=train.filter{marketBucket(it.market)==marketBucket(market)}
        val parent=if(broad.size>=12)broad else train
        fun mean(rows:List<Example>,method:Method):Double = trimmedMean(rows.map{ e ->
            e.outcomes.firstOrNull{it.method==method}?.netPct ?: 0.0 // no-fill = zero, not a loss-free win
        })
        val w=local.size.toDouble()/(local.size+30.0)
        fun projected(m:Method)=(1-w)*mean(parent,m)+w*mean(local,m)
        val chosen=Method.entries.maxByOrNull(::projected) ?: Method.NOW
        if(chosen==Method.NOW)return fallback("NOW leads the smoothed training evidence")
        val trainGain=projected(chosen)-projected(Method.NOW)
        if(trainGain<.10)return fallback("Insufficient robust net advantage over NOW")
        val validateContext=validation.filter{marketBucket(it.market)==marketBucket(market)}
            .takeIf{it.size>=8} ?: validation
        val valGain=mean(validateContext,chosen)-mean(validateContext,Method.NOW)
        val valFilled=validateContext.count{e->e.outcomes.any{it.method==chosen && it.filled}}.toDouble()/validateContext.size
        if(valGain<.06 || valFilled<.70 || validation.map{it.date}.distinct().size<2)
            return fallback("Unvalidated challenger / missed-entry risk")
        return Advice(chosen,true,prior.size,trainDates.size,validateContext.size,
            validateContext.map{it.date}.distinct().size,valGain,key,
            "VALIDATED RESEARCH: net edge vs NOW ${"%.2f".format(java.util.Locale.US,valGain)}pp on walk-forward holdout; advisory only")
    }
}
