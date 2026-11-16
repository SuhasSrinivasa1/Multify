package com.multify.traderpro.engine

/**
 * Transport scheduling only: a valid official discovery snapshot can trigger the
 * NEXT normal full-universe sweep before detailed evaluation finishes. The same
 * 53 batches are moved in time, not duplicated, and candidate selection is frozen.
 */
object FixedEarlyPrefetchPolicy {
    const val MIN_COVERAGE = FixedSweepPrefetchPolicy.MIN_COVERAGE

    fun mayNotifyOfficialDiscovery(
        preparingNextSweep: Boolean, aborted: Boolean,
        validSymbols: Int, masterSymbols: Int
    ): Boolean =
        !preparingNextSweep && !aborted && masterSymbols > 0 &&
            validSymbols.toDouble() / masterSymbols >= MIN_COVERAGE

    /** A completed pending sweep still owns this scan's due date. No second sweep. */
    fun alreadyScheduledForDue(
        pendingDate: String?, pendingDueAtMs: Long?,
        currentDate: String, requestedDueAtMs: Long
    ): Boolean =
        pendingDate == currentDate && pendingDueAtMs == requestedDueAtMs
}
