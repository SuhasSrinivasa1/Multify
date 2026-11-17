package com.multify.traderpro.engine

import java.time.Instant
import java.time.ZoneId

/** Research/shadow-only: NOT a Fixed score, publication or auto-BUY gate. */
object FixedEntryTimingStudyPolicy {
    private val IST = ZoneId.of("Asia/Kolkata")
    data class Metrics(val firstPrimaryToPublicationMs: Long?, val sourceQuoteToPublicationMs: Long?,
        val firstPrimaryRunupPct: Double?, val requiredAtrUnits: Double?,
        val remainingSessionMinutes: Long, val caution30Minutes: Boolean,
        val caution45Minutes: Boolean, val vwapExtensionAtr: Double?,
        val wouldWarnTargetCapacity: Boolean, val wouldWarnExtended: Boolean)
    fun assess(firstPrimaryAt: Long?, firstPrimaryPrice: Double?, sourceQuoteAt: Long?,
               publishedAt: Long, entryPrice: Double, targetPct: Double,
               atr15m: Double?, vwap: Double?): Metrics {
        val firstLag = firstPrimaryAt?.takeIf { it in 1..publishedAt }?.let { publishedAt - it }
        val quoteLag = sourceQuoteAt?.takeIf { it in 1..publishedAt }?.let { publishedAt - it }
        val runup = firstPrimaryPrice?.takeIf { it.isFinite() && it > 0.0 && entryPrice > 0.0 }
            ?.let { (entryPrice / it - 1.0) * 100.0 }
        val atr = atr15m?.takeIf { it.isFinite() && it > 0.0 }
        val required = atr?.let { entryPrice * targetPct / 100.0 / it }
        val ext = if (atr != null && vwap != null && vwap.isFinite() && vwap > 0.0)
            (entryPrice - vwap) / atr else null
        val minutes = FixedPublicationTimingPolicy.remainingMinutes(publishedAt)
        return Metrics(firstLag, quoteLag, runup, required, minutes, minutes < 30,
            minutes < 45, ext, required != null && required > 2.0,
            (ext != null && ext > 5.0) || (runup != null && runup >= targetPct))
    }
}
