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

    fun resetAt(symbol: String, lastEventAtMs: Long,
                observations: List<FixedScanObservation>): Long? =
        observations.asSequence().filter {
            it.symbol.equals(symbol, ignoreCase = true) &&
                it.scanAtMs > lastEventAtMs &&
                (it.qualityTier != "PRIMARY" || !it.publishEligible || !it.executionEligible)
        }.maxOfOrNull { it.scanAtMs }

    fun qualifiedForNewEvent(symbol: String, nowMs: Long,
                             existing: List<FixedRecommendationRecord>,
                             observations: List<FixedScanObservation>): Boolean {
        val previous = existing.filter { it.symbol.equals(symbol, ignoreCase = true) }
            .maxByOrNull { it.generatedAtMs } ?: return true
        if (nowMs <= previous.generatedAtMs ||
            nowMs - previous.generatedAtMs < MIN_EVENT_SEPARATION_MS) return false
        val reset = resetAt(symbol, previous.generatedAtMs, observations) ?: return false
        // At least one *prior* new PRIMARY observation; current PRIMARY is checked
        // by the unchanged FixedForecastPublicationPolicy.isReady.
        return observations.any {
            it.symbol.equals(symbol, ignoreCase = true) &&
                it.scanAtMs > reset && it.scanAtMs < nowMs &&
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
