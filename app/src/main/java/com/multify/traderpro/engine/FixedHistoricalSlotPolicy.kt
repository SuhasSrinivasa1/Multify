package com.multify.traderpro.engine

/** A request slot is a timestamp reservation, never a lock held across HTTP I/O. */
object FixedHistoricalSlotPolicy {
    fun waitMs(nowMs: Long, nextSlotMs: Long, cooldownUntilMs: Long): Long =
        maxOf((nextSlotMs - nowMs).coerceAtLeast(0L), (cooldownUntilMs - nowMs).coerceAtLeast(0L))

    fun reservedNextSlotMs(nowMs: Long, minimumGapMs: Long): Long =
        nowMs + minimumGapMs.coerceAtLeast(0L)
}
