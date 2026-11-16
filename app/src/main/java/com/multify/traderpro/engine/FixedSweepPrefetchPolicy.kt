package com.multify.traderpro.engine

/** Transport-only guard; does not authorize an order or alter a Fixed signal. */
object FixedSweepPrefetchPolicy {
    const val EXPECTED_SWEEP_MS = 82_000L
    // Real v9.0.1 Alpha+Beta sweeps took ~40 seconds vs 79s Alpha-only.
    // Schedule dual-account samples just-in-time: an 82s lead left prepared
    // snapshots almost expired at the next 90s Fixed scan.
    const val DUAL_ACCOUNT_EXPECTED_SWEEP_MS = 50_000L
    // When Alpha is cooling but Beta can read, a full 53-batch Beta-only
    // sweep needs longer than the normal split. This is a planning lead,
    // not permission to return a stale or incomplete market snapshot.
    const val BETA_ONLY_EXPECTED_SWEEP_MS = 76_000L

    fun expectedSweepLeadMs(betaLiveReady: Boolean, alphaLiveReady: Boolean = true): Long =
        when {
            betaLiveReady && !alphaLiveReady -> BETA_ONLY_EXPECTED_SWEEP_MS
            betaLiveReady -> DUAL_ACCOUNT_EXPECTED_SWEEP_MS
            else -> EXPECTED_SWEEP_MS
        }
    const val MIN_START_HEADROOM_MS = 20_000L
    const val MAX_START_HEADROOM_MS = 150_000L
    const val MAX_ACCEPTED_AGE_MS = 45_000L
    const val MAX_WAIT_MS = 95_000L
    const val MAX_OVERRUN_MS = 95_000L
    const val MIN_COVERAGE = 0.85

    /**
     * An official scan may take its ORIGINAL full-market sweep only when
     * the scheduled prefetch never started any broker request. Once the
     * prefetch has started, a duplicate sweep is forbidden even on timeout.
     * This is transport arbitration, never permission to publish a partial scan.
     */
    fun officialSweepAllowedWithoutDuplication(prefetchRequestsStarted: Boolean): Boolean =
        !prefetchRequestsStarted

    fun canStart(
        nowMs: Long, dueAtMs: Long, criticalTradeInFlight: Boolean, brokerReady: Boolean
    ): Boolean = nowMs > 0L && dueAtMs > nowMs &&
        dueAtMs - nowMs in MIN_START_HEADROOM_MS..MAX_START_HEADROOM_MS &&
        !criticalTradeInFlight && brokerReady

    fun canConsume(
        completedAtMs: Long, nowMs: Long, sampledSymbols: Int,
        totalSymbols: Int, aborted: Boolean
    ): Boolean {
        if (completedAtMs <= 0L || nowMs < completedAtMs ||
            nowMs - completedAtMs > MAX_ACCEPTED_AGE_MS || aborted ||
            sampledSymbols <= 0 || totalSymbols <= 0) return false
        return sampledSymbols.toDouble() / totalSymbols.toDouble() >= MIN_COVERAGE
    }
}
