package com.multify.traderpro.engine

import org.junit.Assert.*
import org.junit.Test

class DualMinuteAdmissionPolicyTest {
    private val p = DualMinuteAdmissionPolicy
    @Test fun calendarAndRollingAreIndependentAtMinuteBoundary() {
        val previous = p.reserve(p.CalendarState(), 59_999L)
        assertEquals(1, previous.used)
        assertEquals(0, p.normalize(previous, 60_000L).used)
        assertEquals(59_999L, p.rollingWaitMs(1, 1, 59_999L, 60_000L))
        assertEquals(0L,p.calendarWaitMs(previous, 60_000L, 1))
    }
    @Test fun aFullCalendarMinuteStopsAtBoundary() {
        val a = p.reserve(p.CalendarState(), 125_000L)
        assertEquals(55_000L, p.calendarWaitMs(a,125_000L,1))
        assertEquals(0L,p.calendarWaitMs(a,180_000L,1))
    }
    @Test fun rollingWindowPreventsBoundaryBurst() {
        assertEquals(25_000L, p.rollingWaitMs(40,40,100_000L,135_000L))
        assertEquals(0L,p.rollingWaitMs(39,40,100_000L,135_000L))
    }
    @Test fun categoriesCanMaintainDistinctState() {
        val live=p.reserve(p.CalendarState(),15_000L)
        val historical=p.CalendarState()
        assertEquals(1, live.used)
        assertEquals(0,p.normalize(historical,15_000L).used)
    }
}
