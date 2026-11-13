package com.multify.traderpro.engine

/**
 * Research-only scan-latency classification. A slow scan is not evidence of
 * a bad recommendation. No candidate selection, publication, BUY, SELL or GTT gate.
 */
internal object FixedScanLatencyShadowPolicy {
    const val FAST_UP_TO_MS = 90_000L
    const val SLOW_FROM_MS = 120_000L

    data class Verdict(
        val bucket: String,
        val wouldWarn: Boolean,
        val canBlockRecommendation: Boolean = false,
        val canChangeAutoBuy: Boolean = false
    )

    fun classify(scanElapsedMs: Long): Verdict = when {
        scanElapsedMs < 0L -> Verdict("UNKNOWN", false)
        scanElapsedMs <= FAST_UP_TO_MS -> Verdict("FAST_UP_TO_90S", false)
        scanElapsedMs < SLOW_FROM_MS -> Verdict("INTERMEDIATE_90_TO_120S", false)
        else -> Verdict("SLOW_120S_OR_MORE", true)
    }

    /** Measured on the client: never claim this is exchange quote age. */
    fun localQuoteToReadyMs(
        referenceCapturedAtMs: Long?, scanStartedAtMs: Long, scanReadyAtMs: Long
    ): Long? = referenceCapturedAtMs?.takeIf {
        it >= scanStartedAtMs && it <= scanReadyAtMs
    }?.let { scanReadyAtMs - it }
}
