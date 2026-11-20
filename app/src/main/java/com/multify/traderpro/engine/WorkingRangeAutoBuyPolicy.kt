package com.multify.traderpro.engine

/** Execution-only eligibility for newly confirmed Working Range events. */
object WorkingRangeAutoBuyPolicy {
    const val MAX_CONFIRMATION_AGE_MS = 300_000L

    data class Decision(val eligible: Boolean, val reason: String)

    fun decide(
        enabled: Boolean,
        safetyHalt: Boolean,
        activatedAtMs: Long,
        confirmedAtMs: Long,
        state: String,
        autoBuyState: String,
        nowMs: Long
    ): Decision {
        if (!enabled) return Decision(false, "WORKING_RANGE_DISABLED")
        if (safetyHalt) return Decision(false, "SAFETY_HALT")
        if (activatedAtMs <= 0L) return Decision(false, "ACTIVATION_TIMESTAMP_MISSING")
        if (confirmedAtMs <= activatedAtMs) return Decision(false, "CONFIRMED_BEFORE_ACTIVATION")
        if (state != "IN_RANGE") return Decision(false, "NOT_CURRENTLY_IN_RANGE")
        if (autoBuyState != "NOT_ATTEMPTED") return Decision(false, "EVENT_ALREADY_RESERVED_OR_CONSUMED")
        if (nowMs < confirmedAtMs) return Decision(false, "CLOCK_ORDER_INVALID")
        if (nowMs - confirmedAtMs > MAX_CONFIRMATION_AGE_MS)
            return Decision(false, "CONFIRMATION_EXPIRED")
        return Decision(true, "NEW_CONFIRMED_WORKING_RANGE_EVENT")
    }
}
