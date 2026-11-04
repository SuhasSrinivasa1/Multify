package com.multify.traderpro.engine

import com.multify.traderpro.data.network.QuotePayload
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/** FIXED BUY/notification eligibility overlay. Never changes score, candle formula,
 * historical target, or SELL/GTT logic. These are conservative execution-risk checks;
 * they are NOT proven to predict the next price movement.
 *
 * A +5% day or a new high ALONE is never reason to block a momentum winner.
 * Blocking requires a combination of observed extension and near-peak conditions.
 */
object FixedEntryActionabilityPolicy {
    const val LATE_ENTRY_CUTOFF_HOUR = 15
    const val LATE_ENTRY_CUTOFF_MINUTE = 15
    const val MIN_PRIOR_PRIMARY_EXTENSION_PCT = 1.50
    const val MAX_DISTANCE_FROM_HIGH_PCT = 0.50
    const val STRONG_DAY_MOVE_PCT = 5.0
    const val EXTREME_DAY_MOVE_PCT = 12.0
    const val MICRO_DAY_VOLUME_SHARES = 10_000L
    const val VERY_CLOSE_TO_HIGH_PCT = 0.20
    private val IST = ZoneId.of("Asia/Kolkata")
    private val START = LocalTime.of(9, 15)
    private val END = LocalTime.of(15, 30)
    private val LATE = LocalTime.of(LATE_ENTRY_CUTOFF_HOUR, LATE_ENTRY_CUTOFF_MINUTE)

    data class Verdict(
        val actionable: Boolean,
        val status: String,
        val reasons: List<String>,
        val firstPrimaryRunupPct: Double?,
        val distanceFromQuoteHighPct: Double?,
        val highEvidenceSource: String,
        val vwapAtrExtension: Double?,
        val firstPrimaryRunupBeyondTargetPct: Double?
    )

    fun evaluate(
        nowMs: Long, quote: QuotePayload?,
        referencePrice: Double?, fixedTargetPct: Double,
        priorSessionDuplicate: Boolean = false,
        snapshot: FixedFeatureSnapshot? = null,
        verifiedSameDayCandleHigh: Double? = null
    ): Verdict {
        val local = Instant.ofEpochMilli(nowMs).atZone(IST)
        val clock = local.toLocalTime()
        val reasons = mutableListOf<String>()
        if (local.dayOfWeek.value > 5 || clock < START || clock >= END)
            reasons += "OUTSIDE_NSE_CASH_SESSION"
        else if (clock >= LATE)
            reasons += "LESS_THAN_15_MINUTES_TO_CLOSE"
        if (priorSessionDuplicate) reasons += "PREVIOUSLY_RECOMMENDED_THIS_SESSION"

        val price = quote?.lastPrice?.takeIf { it.isFinite() && it > 0.0 }
        val high = quote?.high?.takeIf { it.isFinite() && it > 0.0 }
        val dayMove = quote?.dayChangePct?.takeIf { it.isFinite() }
        // The frozen 15-minute sessionHighDistancePct covers a MULTI-DAY history
        // window, not necessarily today's session. Never use it as today's high.
        val fallbackHigh = verifiedSameDayCandleHigh
            ?.takeIf { it.isFinite() && it > 0.0 }
        val usedHigh = if (price != null && high != null && high >= price)
            high else fallbackHigh
        val highSource = when {
            usedHigh == null -> "UNAVAILABLE"
            usedHigh == high -> "BROKER_REPORTED_DAY_HIGH"
            else -> "TIMESTAMP_VERIFIED_TODAY_15M_CANDLES"
        }
        val distance = if (price != null && usedHigh != null && usedHigh > 0.0)
            (usedHigh - price) / usedHigh * 100.0 else null
        val vwapAtr = if (price != null && snapshot?.vwap != null &&
            snapshot.atr14 != null && snapshot.atr14.isFinite() && snapshot.atr14 > 0.0)
            (price - snapshot.vwap) / snapshot.atr14 else null
        val firstPrice = referencePrice?.takeIf { it.isFinite() && it > 0.0 }
        val runup = if (firstPrice != null && price != null)
            (price / firstPrice - 1.0) * 100.0 else null
        val spreadBps = FixedMarketQualityShadowPolicy.assess(quote, referencePrice).observedSpreadBps
        val volume = quote?.volume?.takeIf { it >= 0L }

        val extendedSinceFirst = runup != null && fixedTargetPct.isFinite() &&
            fixedTargetPct > 0.0 &&
            runup >= maxOf(MIN_PRIOR_PRIMARY_EXTENSION_PCT, fixedTargetPct * 1.25)
        val nearHigh = distance != null && distance <= MAX_DISTANCE_FROM_HIGH_PCT
        val dayStrong = dayMove != null && dayMove >= STRONG_DAY_MOVE_PCT
        val nearExtremeHigh = distance != null && distance <= VERY_CLOSE_TO_HIGH_PCT
        val volumeThin = volume != null && volume < MICRO_DAY_VOLUME_SHARES
        val adverseSpread = spreadBps != null &&
            spreadBps > FixedMarketQualityShadowPolicy.SHADOW_WIDE_SPREAD_BPS

        if (extendedSinceFirst && nearHigh && dayStrong)
            reasons += "EXTENDED_SINCE_FIRST_PRIMARY_AND_NEAR_DAY_HIGH"
        if (dayMove != null && dayMove >= EXTREME_DAY_MOVE_PCT &&
            nearExtremeHigh && (volumeThin || adverseSpread))
            reasons += "EXTREME_DAY_RISE_NEAR_HIGH_WITH_LIQUIDITY_WARNING"
        if (dayStrong && nearHigh && vwapAtr != null && vwapAtr >= 8.0 &&
            ((runup != null && runup >= 0.5) || volumeThin || adverseSpread))
            reasons += "EXTREME_VWAP_ATR_EXTENSION_NEAR_HIGH"
        // Groww omitted the reported day-high in every Fixed quote of the
        // 2026-11-04 13:25 diagnostic set. The frozen 15m 'session' high in
        // fact spans the multi-DAY historical window. Only use it as a distinctly
        // labeled, corroborating extension proxy, NEVER as today's NSE high.
        val historicalWindowHighDistance = snapshot?.sessionHighDistancePct
            ?.takeIf { it.isFinite() }
        if (dayStrong && extendedSinceFirst &&
            historicalWindowHighDistance != null &&
            historicalWindowHighDistance in -0.10..0.35 &&
            vwapAtr != null && vwapAtr >= 3.0)
            reasons += "MULTIDAY_15M_WINDOW_HIGH_WITH_EXTENDED_FIRST_PRIMARY_RUNUP"

        val cautions = buildList {
            if (dayStrong) add("ALREADY_UP_5PCT")
            if (nearHigh) add("NEAR_BROKER_DAY_HIGH")
            if (runup != null && runup > fixedTargetPct && fixedTargetPct > 0.0)
                add("FIRST_PRIMARY_PRICE_TO_CURRENT_EXCEEDS_TARGET")
        }
        return Verdict(
            actionable = reasons.isEmpty(),
            status = if (reasons.isNotEmpty()) "RESEARCH_ONLY" else if (cautions.isNotEmpty()) "CAUTION" else "ACTIONABLE",
            reasons = if (reasons.isNotEmpty()) reasons else cautions,
            firstPrimaryRunupPct = runup,
            distanceFromQuoteHighPct = distance,
            highEvidenceSource = highSource,
            vwapAtrExtension = vwapAtr,
            firstPrimaryRunupBeyondTargetPct =
                if (runup != null && fixedTargetPct.isFinite())
                    runup - fixedTargetPct else null
        )
    }
}
