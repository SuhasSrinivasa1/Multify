package com.multify.traderpro.engine

import java.util.ArrayDeque

/**
 * Planning-only rolling sweep estimator. Never changes Fixed's market universe,
 * signal calculation, snapshot acceptance, broker limits or trading permissions.
 * Completed valid sweeps are tracked separately for dual, Alpha-only and Beta-only
 * routing, so an Alpha cooldown cannot poison the healthy dual-account estimate.
 */
class FixedSweepAdaptiveLeadPolicy {
    private enum class Lane { DUAL, ALPHA_ONLY, BETA_ONLY }
    private val observations = Lane.values().associateWith { ArrayDeque<Long>() }

    @Synchronized
    fun record(alphaUsed: Boolean, betaUsed: Boolean, elapsedMs: Long, valid: Boolean) {
        if (!valid || elapsedMs !in 1L..300_000L) return
        val lane = lane(alphaUsed, betaUsed) ?: return
        val sample = observations.getValue(lane)
        sample.addLast(elapsedMs)
        while (sample.size > MAX_SAMPLES) sample.removeFirst()
    }

    @Synchronized
    fun expectedLeadMs(betaLiveReady: Boolean, alphaLiveReady: Boolean): Long {
        val fallback = FixedSweepPrefetchPolicy.expectedSweepLeadMs(
            betaLiveReady = betaLiveReady, alphaLiveReady = alphaLiveReady
        )
        val lane = lane(alphaLiveReady, betaLiveReady) ?: return fallback
        val samples = observations.getValue(lane)
        if (samples.size < MIN_SAMPLES) return fallback
        val sorted = samples.sorted()
        // Nearest-rank p90 (conservative for small, recent samples).
        val index = ((sorted.size * 9 + 9) / 10 - 1).coerceIn(0, sorted.lastIndex)
        return (sorted[index] + SAFETY_MARGIN_MS)
            .coerceIn(MIN_LEAD_MS, MAX_LEAD_MS)
    }

    @Synchronized
    fun sampleCount(alphaUsed: Boolean, betaUsed: Boolean): Int =
        lane(alphaUsed, betaUsed)?.let { observations.getValue(it).size } ?: 0

    private fun lane(alpha: Boolean, beta: Boolean): Lane? = when {
        alpha && beta -> Lane.DUAL
        alpha -> Lane.ALPHA_ONLY
        beta -> Lane.BETA_ONLY
        else -> null
    }

    companion object {
        private const val MIN_SAMPLES = 3
        private const val MAX_SAMPLES = 12
        private const val SAFETY_MARGIN_MS = 8_000L
        private const val MIN_LEAD_MS = 35_000L
        // Preserve the existing prefetch policy's 150-second maximum headroom.
        private const val MAX_LEAD_MS = 150_000L
    }
}
