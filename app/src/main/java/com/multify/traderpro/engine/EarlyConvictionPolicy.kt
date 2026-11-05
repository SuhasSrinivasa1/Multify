package com.multify.traderpro.engine

import com.multify.traderpro.data.network.EarlyOpportunityDto
import com.multify.traderpro.data.network.FixedEarlyWatchDto
import com.multify.traderpro.data.network.FixedFastCandidateDto
import com.multify.traderpro.data.network.FixedPulseResearchDto
import java.util.Locale
import kotlin.math.abs

/**
 * Independent early-conviction research lane. NEVER enters the frozen Fixed
 * scorer or order execution path. A quality score is NOT a win probability.
 * Receipt timestamps are not authenticated NSE exchange timestamps.
 */
object EarlyConvictionPolicy {
    const val MAX_VISIBLE = 3
    const val MAX_PRIMARY_AGE_MS = 8L * 60_000L
    const val MAX_PULSE_AGE_MS = 12L * 60_000L
    const val MAX_FAST_AGE_MS = 3L * 60_000L
    const val MAX_SPREAD_BPS = 40.0
    const val MIN_DAY_SHARES = 20_000L
    const val MAX_FIRST_PRIMARY_RUNUP_PCT = 1.25
    const val BUY_IDEA_MIN_QUALITY = 85

    private fun fresh(at: Long, now: Long, horizon: Long) =
        at > 0L && now >= at && now - at <= horizon

    fun rank(
        early: List<FixedEarlyWatchDto>,
        pulses: List<FixedPulseResearchDto>,
        fast: List<FixedFastCandidateDto>,
        officialSymbols: Collection<String>,
        nowMs: Long,
        limit: Int = MAX_VISIBLE
    ): List<EarlyOpportunityDto> {
        if (limit <= 0 || nowMs <= 0L) return emptyList()
        val official = officialSymbols.map { it.uppercase(Locale.US) }.toSet()
        val byPulse = pulses.filter { fresh(it.latestObservedAtMs, nowMs, MAX_PULSE_AGE_MS) }
            .associateBy { it.symbol.uppercase(Locale.US) }
        val byFast = fast.filter {
            it.observationCount > 0 && fresh(it.latestObservedAtMs, nowMs, MAX_FAST_AGE_MS)
        }.associateBy { it.symbol.uppercase(Locale.US) }

        return early.asSequence().mapNotNull { watch ->
            val symbol = watch.symbol.uppercase(Locale.US)
            if (symbol in official || watch.officialPublished ||
                !symbol.matches(Regex("[A-Z0-9&._-]{1,32}")) ||
                !fresh(watch.latestEvaluatedAtMs, nowMs, MAX_PRIMARY_AGE_MS) ||
                !watch.firstPrimaryPrice.isFinite() || watch.firstPrimaryPrice <= 0.0 ||
                !watch.latestPrice.isFinite() || watch.latestPrice <= 0.0 ||
                !RecommendationPriceBandPolicy.eligible(watch.latestPrice)) return@mapNotNull null

            val pulse = byPulse[symbol]?.takeIf {
                it.firstPulsePrice.isFinite() && it.firstPulsePrice > 0.0 &&
                    it.pulsePct in 0.30..4.00 &&
                    it.firstPulseObservedAtMs <= watch.latestEvaluatedAtMs + 120_000L &&
                    it.latestPrice.isFinite() &&
                    abs(it.latestPrice / watch.latestPrice - 1.0) <= 0.025
            }
            val sample = byFast[symbol]?.takeIf {
                it.latestObservedAtMs >= watch.firstEvaluatedAtMs &&
                    it.latestPrice.isFinite() && it.latestPrice > 0.0 &&
                    abs(it.latestPrice / watch.latestPrice - 1.0) <= 0.025
            }
            val latestPrice = if (sample != null && sample.latestObservedAtMs > watch.latestEvaluatedAtMs)
                sample.latestPrice else watch.latestPrice
            val latestAt = maxOf(watch.latestEvaluatedAtMs, sample?.latestObservedAtMs ?: 0L)
            val movedPct = (latestPrice / watch.firstPrimaryPrice - 1.0) * 100.0
            if (!movedPct.isFinite() || movedPct < -2.5 || movedPct > 3.0) return@mapNotNull null
            val validSpread = watch.quoteBookStatus == "VALID_BOOK" &&
                watch.observedSpreadBps?.isFinite() == true &&
                watch.observedSpreadBps in 0.0..MAX_SPREAD_BPS
            val liquid = (watch.dayTradedShares ?: 0L) >= MIN_DAY_SHARES
            val risky = watch.warnings.any {
                it.contains("CHASING", true) ||
                    it.contains("WIDE_SPREAD", true) ||
                    it.contains("LOW_ABSOLUTE_VOLUME", true)
            } || (watch.dayChangePct ?: 0.0) >= 12.0 ||
                movedPct > MAX_FIRST_PRIMARY_RUNUP_PCT
            // A pulse has already compared two full-market snapshots, so it is an
            // independent corroborating observation without waiting for an
            // optional Fast Watch API request. Never infer exchange tick timing.
            val corroborated = watch.confirmationObservations >= 2 ||
                pulse != null || (sample != null && sample.observationCount >= 2)
            var score = 55
            if (validSpread) score += if (watch.observedSpreadBps!! <= 15.0) 12 else 6
            if (liquid) score += if ((watch.dayTradedShares ?: 0L) >= 100_000L) 10 else 6
            if (watch.confirmationObservations >= 2) score += 12
            if (pulse != null) score += 12
            if (sample != null) score += 10
            if (watch.dayChangePct != null && watch.dayChangePct in 0.0..8.0) score += 6
            if (movedPct in -0.20..1.0) score += 5
            if (risky) score -= 20
            score = score.coerceIn(0, 100)
            val buyIdea = corroborated && validSpread && liquid && !risky &&
                score >= BUY_IDEA_MIN_QUALITY
            EarlyOpportunityDto(
                symbol = symbol,
                status = if (buyIdea) "EARLY_BUY_IDEA" else "WATCH",
                qualityScore = score,
                observedPrice = latestPrice,
                firstObservedAtMs = watch.firstEvaluatedAtMs,
                lastObservedAtMs = latestAt,
                primaryObservations = watch.confirmationObservations,
                corroboratingPulse = pulse != null,
                corroboratingFastPrice = sample != null,
                spreadBps = watch.observedSpreadBps,
                dayTradedShares = watch.dayTradedShares,
                note = if (buyIdea) "First PRIMARY observed; full Fixed confirmation pending"
                    else "Watch only: waiting for liquidity and independent corroboration",
                tradeEligible = false,
                evidenceSource = "ANDROID_RECEIPT_NOT_EXCHANGE_TICK"
            )
        }.filter { it.qualityScore >= 55 }
            .distinctBy { it.symbol }
            .sortedWith(compareByDescending<EarlyOpportunityDto> { it.status == "EARLY_BUY_IDEA" }
                .thenByDescending { it.qualityScore }
                .thenByDescending { it.lastObservedAtMs }
                .thenBy { it.symbol })
            .take(limit.coerceIn(0, MAX_VISIBLE)).toList()
    }
}
