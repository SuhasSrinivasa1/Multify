package com.multify.traderpro.engine

/**
 * Execution-only, independent late/peak risk overlay for the frozen Fixed signal.
 *
 * Do not change Fixed ranking, 40 factors, 15m inputs, PRIMARY confirmation,
 * reference target, trailing, hard stop, or broker order reconciliation.
 * Replays from 20-Nov-2026 are one DAY, so a strong extension is treated as
 * a conservative entry-safety hold, never as a proven predictive model.
 *
 * The high proxy is a broker-reported high OR a verified same-day COMPLETED
 * 15m-candle high. A negative distance means price exceeds the latest
 * completed-bar high, not that an exchange day high has been verified.
 */
object FixedLateEntrySafetyPolicy {
    const val MAX_AGE_AFTER_ACTUAL_PUBLICATION_MS = 90_000L
    const val EXTREME_VWAP_EXTENSION_ATR = 5.0
    const val TARGET_CAPACITY_WARNING_ATR = 2.0
    const val MAX_DISTANCE_TO_VERIFIED_HIGH_PCT = 0.50

    data class Decision(
        val executable: Boolean,
        val reason: String,
        val peakRisk: Boolean,
        val publicationAgeMs: Long?,
        val sourceQuoteAgeMs: Long?
    )

    fun assess(
        nowMs: Long,
        publishedAtMs: Long?,
        sourceQuoteReceivedAtMs: Long?,
        vwapExtensionAtr: Double?,
        targetRequiredAtr: Double?,
        verifiedHighDistancePct: Double?,
        highSource: String
    ): Decision {
        if (nowMs <= 0L || publishedAtMs == null || publishedAtMs <= 0L ||
            sourceQuoteReceivedAtMs == null || sourceQuoteReceivedAtMs <= 0L ||
            sourceQuoteReceivedAtMs > publishedAtMs || nowMs < publishedAtMs
        ) return Decision(false, "FIXED_PUBLICATION_PROVENANCE_UNAVAILABLE", false, null, null)
        val pubAge = nowMs - publishedAtMs
        val quoteAge = nowMs - sourceQuoteReceivedAtMs
        if (pubAge > MAX_AGE_AFTER_ACTUAL_PUBLICATION_MS)
            return Decision(false, "FIXED_POST_PUBLICATION_ENTRY_EXPIRED", false, pubAge, quoteAge)

        val highObserved = highSource == "BROKER_REPORTED_DAY_HIGH" ||
            highSource == "TIMESTAMP_VERIFIED_TODAY_15M_CANDLES"
        val peak = highObserved &&
            verifiedHighDistancePct?.isFinite() == true &&
            verifiedHighDistancePct <= MAX_DISTANCE_TO_VERIFIED_HIGH_PCT &&
            vwapExtensionAtr?.isFinite() == true &&
            vwapExtensionAtr >= EXTREME_VWAP_EXTENSION_ATR &&
            targetRequiredAtr?.isFinite() == true &&
            targetRequiredAtr >= TARGET_CAPACITY_WARNING_ATR
        return if (peak)
            Decision(false, "FIXED_EXTENDED_PEAK_TARGET_CAPACITY_RISK", true, pubAge, quoteAge)
        else Decision(true, "FIXED_ENTRY_RISK_OVERLAY_CLEAR", false, pubAge, quoteAge)
    }
}
