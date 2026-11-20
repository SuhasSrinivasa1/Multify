package com.multify.traderpro.engine

/** Execution-only eligibility for newly confirmed Working Range events. */
object WorkingRangeAutoBuyPolicy {
    const val MAX_CONFIRMATION_AGE_MS = WorkingRangeEntryBandPolicy.MAX_CONFIRMATION_TO_BUY_MS
    const val MAX_FIRST_QUALIFIED_AGE_MS = WorkingRangeEntryBandPolicy.MAX_FIRST_QUALIFIED_TO_BUY_MS

    data class Decision(val eligible: Boolean, val reason: String)

    fun decide(
        enabled: Boolean,
        safetyHalt: Boolean,
        activatedAtMs: Long,
        confirmedAtMs: Long,
        state: String,
        autoBuyState: String,
        nowMs: Long,
        firstQualifiedAtMs: Long = confirmedAtMs
    ): Decision {
        if (!enabled) return Decision(false, "WORKING_RANGE_DISABLED")
        if (safetyHalt) return Decision(false, "SAFETY_HALT")
        if (activatedAtMs <= 0L) return Decision(false, "ACTIVATION_TIMESTAMP_MISSING")
        if (confirmedAtMs <= activatedAtMs) return Decision(false, "CONFIRMED_BEFORE_ACTIVATION")
        if (state != "IN_RANGE") return Decision(false, "NOT_CURRENTLY_IN_RANGE")
        if (autoBuyState != "NOT_ATTEMPTED") return Decision(false, "EVENT_ALREADY_RESERVED_OR_CONSUMED")
        if (firstQualifiedAtMs <= 0L || nowMs < firstQualifiedAtMs)
            return Decision(false, "FIRST_QUALIFIED_CLOCK_INVALID")
        if (nowMs - firstQualifiedAtMs > MAX_FIRST_QUALIFIED_AGE_MS)
            return Decision(false, "FIRST_QUALIFIED_SIGNAL_EXPIRED")
        if (nowMs < confirmedAtMs) return Decision(false, "CLOCK_ORDER_INVALID")
        if (nowMs - confirmedAtMs > MAX_CONFIRMATION_AGE_MS)
            return Decision(false, "CONFIRMATION_TO_BUY_EXPIRED_45S")
        return Decision(true, "NEW_CONFIRMED_WORKING_RANGE_EVENT")
    }
}

/** Enforces two DIFFERENT official scan generations. Duplicate evaluation calls
 * within one scan must never satisfy the live Auto-Buy confirmation. */
object WorkingRangeDistinctScanPolicy {
    fun isIndependentSecondScan(
        firstScanStartedAtMs: Long, currentScanStartedAtMs: Long,
        previousEvidenceAtMs: Long, currentEvidenceAtMs: Long
    ): Boolean =
        firstScanStartedAtMs > 0L && currentScanStartedAtMs > firstScanStartedAtMs &&
            previousEvidenceAtMs > 0L && currentEvidenceAtMs > previousEvidenceAtMs
}
