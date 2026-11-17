package com.multify.traderpro.engine

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/**
 * Read-only scan-health guardrail calibrated from the 17-Nov-2026 v9.0.6
 * diagnostic export. It classifies scan timing; it does NOT change Fixed
 * selection, publication, Auto Buy, order submission or protection.
 *
 * 45s elapsed budget sits above the 38.1s maximum observed after startup warm-up in the healthy
 * 09:18-09:45 pre-429 sample, leaving operating margin.
 * 105s start-spacing budget is the 90s cadence target plus 15s tolerance.
 */
object FixedScanTimingGuardrail {
    const val TARGET_START_SPACING_MS = 90_000L
    const val MAX_NORMAL_ELAPSED_MS = 45_000L
    const val MAX_NORMAL_START_SPACING_MS = 105_000L
    private val INDIA = ZoneId.of("Asia/Kolkata")

    data class Assessment(
        val status: String,
        val timeOfDayBucket: String,
        val scanElapsedMs: Long,
        val startSpacingMs: Long?,
        val scanElapsedWithinBudget: Boolean,
        val startSpacingWithinBudget: Boolean?,
        val brokerRateLimited: Boolean,
        val action: String = "OBSERVE_ONLY",
        val productionBuyVeto: Boolean = false,
        val fixedStrategyChanged: Boolean = false
    )

    fun assess(
        scanStartedAtMs: Long,
        elapsedMs: Long,
        startSpacingMs: Long?,
        live429s: Long,
        historical429s: Long
    ): Assessment {
        val localTime = Instant.ofEpochMilli(scanStartedAtMs).atZone(INDIA).toLocalTime()
        val bucket = when {
            localTime < LocalTime.of(9, 15) -> "PRE_MARKET"
            localTime < LocalTime.of(9, 30) -> "OPENING_0915_0930"
            localTime < LocalTime.of(9, 45) -> "EARLY_0930_0945"
            localTime < LocalTime.of(11, 0) -> "MIDMORNING_0945_1100"
            localTime < LocalTime.of(14, 0) -> "MIDDAY_1100_1400"
            localTime < LocalTime.of(15, 30) -> "LATE_1400_1530"
            else -> "AFTER_MARKET"
        }
        val elapsedOkay = elapsedMs in 0..MAX_NORMAL_ELAPSED_MS
        val spacingOkay = startSpacingMs?.let { it in 0..MAX_NORMAL_START_SPACING_MS }
        val rateLimited = live429s > 0L || historical429s > 0L
        val status = when {
            rateLimited -> "BROKER_RATE_LIMITED"
            !elapsedOkay -> "SLOW_SCAN"
            spacingOkay == false -> "CADENCE_DRIFT"
            spacingOkay == null -> "BASELINE_NO_SPACING"
            else -> "NORMAL"
        }
        return Assessment(
            status = status,
            timeOfDayBucket = bucket,
            scanElapsedMs = elapsedMs,
            startSpacingMs = startSpacingMs,
            scanElapsedWithinBudget = elapsedOkay,
            startSpacingWithinBudget = spacingOkay,
            brokerRateLimited = rateLimited
        )
    }
}
