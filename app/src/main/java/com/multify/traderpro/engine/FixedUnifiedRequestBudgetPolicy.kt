package com.multify.traderpro.engine

/**
 * Shared Alpha/Beta upstream request envelope. These are transport budgets only:
 * they never alter Fixed symbols, ranking, factors, quality tiers or targets.
 *
 * Normal limits preserve the measured two-lane operating envelope. After a 429,
 * only the affected lane is temporarily reduced; the shared cap also backs off.
 */
object FixedUnifiedRequestBudgetPolicy {
    const val WINDOW_MS = 60_000L
    const val SECOND_WINDOW_MS = 1_000L
    const val ALPHA_LIVE_PER_SECOND = 7
    const val BETA_LIVE_PER_SECOND = 1
    const val SHARED_LIVE_PER_SECOND = 8
    const val ALPHA_HISTORICAL_PER_SECOND = 4
    const val BETA_HISTORICAL_PER_SECOND = 1
    const val SHARED_HISTORICAL_PER_SECOND = 5
    const val ALPHA_LIVE_PER_MINUTE = 55
    const val BETA_LIVE_PER_MINUTE = 35
    const val SHARED_LIVE_PER_MINUTE = 90
    const val ALPHA_LTP_PER_MINUTE = 18
    const val ALPHA_HISTORICAL_PER_MINUTE = 45
    const val BETA_HISTORICAL_PER_MINUTE = 8
    const val SHARED_HISTORICAL_PER_MINUTE = 53

    const val ALPHA_LIVE_AFTER_429 = 50
    const val BETA_LIVE_AFTER_429 = 30
    const val SHARED_LIVE_AFTER_429 = 75
    const val ALPHA_HISTORICAL_AFTER_429 = 40
    const val BETA_HISTORICAL_AFTER_429 = 7
    const val SHARED_HISTORICAL_AFTER_429 = 45
    const val ADAPTIVE_WINDOW_MS = 5L * 60_000L
    const val BACKGROUND_YIELD_MS = 125L

    fun effectiveAlphaLiveLimit(pressureActive: Boolean): Int =
        if (pressureActive) ALPHA_LIVE_AFTER_429 else ALPHA_LIVE_PER_MINUTE

    fun effectiveBetaLiveLimit(pressureActive: Boolean): Int =
        if (pressureActive) BETA_LIVE_AFTER_429 else BETA_LIVE_PER_MINUTE

    fun effectiveSharedLiveLimit(pressureActive: Boolean): Int =
        if (pressureActive) SHARED_LIVE_AFTER_429 else SHARED_LIVE_PER_MINUTE

    fun effectiveAlphaHistoricalLimit(pressureActive: Boolean): Int =
        if (pressureActive) ALPHA_HISTORICAL_AFTER_429 else ALPHA_HISTORICAL_PER_MINUTE

    fun effectiveBetaHistoricalLimit(pressureActive: Boolean): Int =
        if (pressureActive) BETA_HISTORICAL_AFTER_429 else BETA_HISTORICAL_PER_MINUTE

    fun effectiveSharedHistoricalLimit(pressureActive: Boolean): Int =
        if (pressureActive) SHARED_HISTORICAL_AFTER_429 else SHARED_HISTORICAL_PER_MINUTE

    fun windowWaitMs(
        nowMs: Long,
        used: Int,
        limit: Int,
        oldestAtMs: Long?
    ): Long = if (used >= limit && oldestAtMs != null)
        (oldestAtMs + WINDOW_MS - nowMs).coerceAtLeast(1L) else 0L

    fun secondWindowWaitMs(
        nowMs: Long,
        used: Int,
        limit: Int,
        oldestAtMs: Long?
    ): Long = if (used >= limit && oldestAtMs != null)
        (oldestAtMs + SECOND_WINDOW_MS - nowMs).coerceAtLeast(1L) else 0L
}
