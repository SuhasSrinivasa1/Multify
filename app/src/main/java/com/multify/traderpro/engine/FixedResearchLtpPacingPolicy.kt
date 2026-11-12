package com.multify.traderpro.engine

/** Additional margin against unpredictable Alpha LTP endpoint 429s. This only
 * spaces NON-PROTECTIVE research/bulk LTP starts. Order API, SELL/GTT and
 * holding-protection LTP admission retain their existing priority and policy.
 */
object FixedResearchLtpPacingPolicy {
    const val MIN_RESEARCH_GAP_MS = 1_500L
    fun effectiveGapMs(currentAdaptiveGapMs: Long, isProtectionLtp: Boolean): Long =
        if (isProtectionLtp) currentAdaptiveGapMs
        else maxOf(currentAdaptiveGapMs, MIN_RESEARCH_GAP_MS)
}
