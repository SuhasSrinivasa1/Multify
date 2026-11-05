package com.multify.traderpro.engine

import com.multify.traderpro.data.network.FixedEarlyWatchDto
import com.multify.traderpro.data.network.FixedPulseResearchDto
import java.util.Locale

/** Research-only price refresh; NEVER counts as Fixed confirmation or BUY signal. */
object FixedFastCandidateSelectionPolicy {
    const val MAX_LTP_SYMBOLS = 50
    const val MAX_EXTRA_CANDIDATES = 12
    const val WATCH_MAX_AGE_MS = 20L * 60_000L
    const val PULSE_MAX_AGE_MS = 12L * 60_000L
    data class Candidate(val symbol: String, val baselinePrice: Double,
        val observedAtMs: Long, val origin: String)
    fun select(early: List<FixedEarlyWatchDto>, pulses: List<FixedPulseResearchDto>,
        publishedSymbols: List<String>, nowMs: Long, limit: Int = MAX_EXTRA_CANDIDATES
    ): List<Candidate> {
        val exclude = publishedSymbols.map { it.uppercase(Locale.US) }.toSet()
        fun valid(symbol: String, price: Double, atMs: Long, maxAgeMs: Long) =
            symbol.matches(Regex("[A-Z0-9&._-]{1,32}")) &&
            price.isFinite() && price > 0.0 && atMs > 0L &&
            nowMs >= atMs && nowMs - atMs <= maxAgeMs
        val fromPrimary = early.asSequence()
            .filter { valid(it.symbol, it.firstPrimaryPrice, it.firstEvaluatedAtMs, WATCH_MAX_AGE_MS) }
            .map { Candidate(it.symbol.uppercase(Locale.US), it.firstPrimaryPrice,
                it.firstEvaluatedAtMs, "FIRST_PRIMARY_RESEARCH") }
        val fromPulses = pulses.asSequence()
            .filter { valid(it.symbol, it.firstPulsePrice, it.firstPulseObservedAtMs, PULSE_MAX_AGE_MS) }
            .map { Candidate(it.symbol.uppercase(Locale.US), it.firstPulsePrice,
                it.firstPulseObservedAtMs, "MARKET_PULSE_RESEARCH") }
        return (fromPrimary + fromPulses)
            .filter { it.symbol !in exclude }.distinctBy { it.symbol }
            .take(limit.coerceIn(0, MAX_EXTRA_CANDIDATES)).toList()
    }
}

/** One discretionary candidate-only LTP request between Full Fixed scans at most.
 * Existing published outcome LTP batches can piggyback the selected candidates.
 */
object FixedFastCandidateSamplingPolicy {
    const val MIN_SAMPLE_INTERVAL_MS = 60_000L
    const val MIN_FIXED_SCAN_HEADROOM_MS = 25_000L
    const val MIN_LIVE_HEADROOM = 12

    fun candidateOnlyAllowed(nowMs: Long, lastRequestedAtMs: Long,
        millisUntilNextFixed: Long, fixedBusy: Boolean,
        tradingCritical: Boolean, hasManagedHoldings: Boolean,
        liveHeadroom: Int, brokerReady: Boolean
    ): Boolean = brokerReady && !fixedBusy && !tradingCritical && !hasManagedHoldings &&
        millisUntilNextFixed >= MIN_FIXED_SCAN_HEADROOM_MS &&
        liveHeadroom >= MIN_LIVE_HEADROOM &&
        (lastRequestedAtMs <= 0L || nowMs < lastRequestedAtMs ||
            nowMs - lastRequestedAtMs >= MIN_SAMPLE_INTERVAL_MS)
}
