package com.multify.traderpro.engine

import com.multify.traderpro.data.repository.FixedRecommendationRecord
import com.multify.traderpro.data.repository.FixedScanObservation

/**
 * Narrowly scoped publication-identity change; Fixed's 40 factors, 15m candles,
 * market-wide discovery, rank, target and PRIMARY confirmation are unchanged.
 *
 * A continuing PRIMARY is the SAME setup. A new event requires an explicit
 * intervening non-qualifying observation, then a fresh confirmed PRIMARY on
 * a different scan and at least 30 minutes since the previous publication.
 * Missing observations NEVER imply a reset.
 */
internal object FixedRepeatQualificationPolicy {
    const val MIN_EVENT_SEPARATION_MS = 30L * 60_000L
    const val MIN_TARGET_REENTRY_PULLBACK_PCT = 0.50

    fun resetAt(symbol: String, lastEventAtMs: Long,
                observations: List<FixedScanObservation>): Long? =
        observations.asSequence().filter {
            it.symbol.equals(symbol, ignoreCase = true) &&
                it.scanAtMs > lastEventAtMs &&
                (it.qualityTier != "PRIMARY" || !it.publishEligible || !it.executionEligible)
        }.maxOfOrNull { it.scanAtMs }

    fun qualifiedForNewEvent(symbol: String, nowMs: Long,
                             existing: List<FixedRecommendationRecord>,
                             observations: List<FixedScanObservation>,
                             importedLastEventAtMs: Long? = null): Boolean {
        val previousRecord = existing.filter { it.symbol.equals(symbol, ignoreCase = true) }
            .maxByOrNull { it.generatedAtMs }
        val latestLocal = previousRecord?.generatedAtMs
        val previousAtMs = listOfNotNull(latestLocal, importedLastEventAtMs)
            .maxOrNull() ?: return true
        if (nowMs <= previousAtMs ||
            nowMs - previousAtMs < MIN_EVENT_SEPARATION_MS) return false
        val reset = resetAt(symbol, previousAtMs, observations) ?: return false
        // After a known target hit, require an observed pullback of at least 0.50%
        // from that event's target before a new PRIMARY can qualify. No guessed
        // pullback is inferred from missing observations.
        val pullbackAt = if (previousRecord?.status == "TARGET_HIT") {
            val pullbackFloor = previousRecord.targetPrice *
                (1.0 - MIN_TARGET_REENTRY_PULLBACK_PCT / 100.0)
            observations.asSequence().filter {
                it.symbol.equals(symbol, ignoreCase = true) &&
                    it.scanAtMs > previousAtMs && it.scanAtMs < nowMs &&
                    it.observedPrice?.isFinite() == true && it.observedPrice > 0.0 &&
                    it.observedPrice <= pullbackFloor
            }.maxOfOrNull { it.scanAtMs } ?: return false
        } else Long.MIN_VALUE
        // At least one *prior* new PRIMARY observation after both reset and pullback;
        // current PRIMARY is checked by the unchanged FixedForecastPublicationPolicy.isReady.
        val freshSetupAfter = maxOf(reset, pullbackAt)
        return observations.any {
            it.symbol.equals(symbol, ignoreCase = true) &&
                it.scanAtMs > freshSetupAfter && it.scanAtMs < nowMs &&
                it.qualityTier == "PRIMARY" && it.publishEligible && it.executionEligible
        }
    }

    fun primaryHitsSinceReset(symbol: String, lastEventAtMs: Long?,
                              observations: List<FixedScanObservation>): Int {
        val boundary = lastEventAtMs?.let {
            resetAt(symbol, it, observations) ?: return 0
        } ?: Long.MIN_VALUE
        return observations.asSequence()
            .filter {
                it.symbol.equals(symbol, ignoreCase = true) &&
                    it.scanAtMs > boundary &&
                    it.qualityTier == "PRIMARY" && it.publishEligible && it.executionEligible
            }
            .map { it.scanAtMs }.distinct().count()
    }
}
