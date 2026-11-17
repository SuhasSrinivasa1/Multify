package com.multify.traderpro.engine

/**
 * Joint calendar-minute AND sliding-window admission. Groww's exact
 * minute-reset semantics are not documented: calendar boundaries are
 * observability/extra safety, never an alternative to rolling limits.
 *
 * Caller reserves atomically inside its account/category mutex.
 */
object DualMinuteAdmissionPolicy {
    const val MINUTE_MS = 60_000L
    data class CalendarState(val bucket: Long = Long.MIN_VALUE, val used: Int = 0)
    fun minuteId(epochMs: Long): Long = Math.floorDiv(epochMs, MINUTE_MS)
    fun normalize(state: CalendarState, epochMs: Long): CalendarState {
        val bucket = minuteId(epochMs)
        return if (state.bucket == bucket) state else CalendarState(bucket, 0)
    }
    fun calendarWaitMs(state: CalendarState, nowEpochMs: Long, maxPerMinute: Int): Long {
        require(maxPerMinute > 0)
        val normalized = normalize(state, nowEpochMs)
        return if (normalized.used >= maxPerMinute)
            ((normalized.bucket + 1L) * MINUTE_MS - nowEpochMs).coerceAtLeast(1L)
        else 0L
    }
    fun rollingWaitMs(used: Int, maxPerMinute: Int, oldestAtElapsedMs: Long?,
                      nowElapsedMs: Long): Long {
        require(maxPerMinute > 0)
        return if (used >= maxPerMinute && oldestAtElapsedMs != null)
            (oldestAtElapsedMs + MINUTE_MS - nowElapsedMs).coerceAtLeast(1L)
        else 0L
    }
    fun reserve(state: CalendarState, nowEpochMs: Long): CalendarState {
        val current = normalize(state, nowEpochMs)
        return current.copy(used = current.used + 1)
    }
    fun headroom(state: CalendarState, nowEpochMs: Long, limit: Int): Int =
        (limit - normalize(state, nowEpochMs).used).coerceAtLeast(0)
}
