package com.multify.traderpro.engine

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/**
 * Diagnostic comparison of the existing 15m Fixed signal and an optional 5m
 * shadow evaluation at the SAME prior quote. No order, recommendation, veto,
 * target, GTT, network request, model training or production clock changes.
 */
internal object FixedTimeframeShadowDecisionPolicy {
    private val india = ZoneId.of("Asia/Kolkata")
    private val switchAt = LocalTime.of(9, 45)
    data class Verdict(
        val phase: String,
        val researchLabel: String,
        val scheduledResearchReference: String,
        val dynamicResearchReference: String,
        val completeFiveMinuteEvidence: Boolean,
        val spreadKnownAndAcceptable: Boolean,
        val exhaustionHypothesis: Boolean?,
        val productionAuthority: String = "FROZEN_FIXED_15M",
        val canAuthorizeTrading: Boolean = false
    )

    fun assess(
        capturedAtMs: Long,
        fifteenMinuteTier: String,
        fiveMinuteTier: String,
        completedFiveMinuteSessionBars: Int,
        spreadBps: Double?,
        vwapExtensionAtr: Double?,
        rsi14: Double?,
        rsiSlope: Double?
    ): Verdict {
        val clock = Instant.ofEpochMilli(capturedAtMs)
            .atZone(india).toLocalTime()
        val inSession = clock >= LocalTime.of(9, 15) &&
            clock < LocalTime.of(15, 30)
        val afterSwitch = clock >= switchAt
        val phase = when {
            !inSession -> "OUTSIDE_REGULAR_SESSION"
            afterSwitch -> "AFTER_0945_IST"
            else -> "BEFORE_0945_IST"
        }
        val scheduled = if (afterSwitch && inSession) "5M_RESEARCH_HYPOTHESIS"
            else "15M_REFERENCE"
        val fiveReady = completedFiveMinuteSessionBars >= 1
        val validSpread = spreadBps?.takeIf { it.isFinite() && it >= 0.0 }
        val acceptableSpread = validSpread != null && validSpread <= 40.0
        val extension = vwapExtensionAtr?.takeIf { it.isFinite() }
        val rsi = rsi14?.takeIf { it.isFinite() }
        val slope = rsiSlope?.takeIf { it.isFinite() }
        val exhaustion = if (extension == null || rsi == null || slope == null) null
            else extension >= 3.0 && rsi >= 75.0 && slope <= 0.0
        val tier15 = fifteenMinuteTier.uppercase() == "PRIMARY"
        val tier5 = fiveMinuteTier.uppercase() == "PRIMARY"
        val label = when {
            !inSession -> "OUT_OF_SESSION"
            !fiveReady -> "MISSING_COMPLETED_5M"
            validSpread == null -> "UNKNOWN_SPREAD"
            !acceptableSpread -> "SPREAD_RISK"
            exhaustion == true -> "EXHAUSTION_WATCH"
            tier15 && tier5 -> "CONTINUATION"
            !tier15 && tier5 -> "EARLY_WATCH"
            tier15 -> "DIVERGENCE_WATCH"
            else -> "NO_QUALIFIED_SHADOW_SETUP"
        }
        // A research preference is not a production timeframe switch.
        val dynamic = when (label) {
            "CONTINUATION", "EARLY_WATCH" -> "5M_TIMING_RESEARCH"
            "EXHAUSTION_WATCH" -> "NO_NEW_SHADOW_ENTRY_HYPOTHESIS"
            else -> "15M_REFERENCE"
        }
        return Verdict(
            phase, label, scheduled, dynamic, fiveReady,
            acceptableSpread, exhaustion
        )
    }
}
