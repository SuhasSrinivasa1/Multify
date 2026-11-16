package com.multify.traderpro.engine

import org.junit.Assert.*
import org.junit.Test

class FixedEarlyPrefetchPolicyTest {
    @Test fun onlyValidOfficialSweepCanTriggerNextPrefetch() {
        val gate = FixedEarlyPrefetchPolicy
        assertTrue(gate.mayNotifyOfficialDiscovery(false, false, 2438, 2646))
        assertFalse(gate.mayNotifyOfficialDiscovery(true, false, 2438, 2646))
        assertFalse(gate.mayNotifyOfficialDiscovery(false, true, 2438, 2646))
        assertFalse(gate.mayNotifyOfficialDiscovery(false, false, 1368, 2646))
        assertFalse(gate.mayNotifyOfficialDiscovery(false, false, 0, 0))
    }

    @Test fun sameDueDateCannotScheduleDuplicateSweepEvenWhenCompleted() {
        val gate = FixedEarlyPrefetchPolicy
        val date = "2026-11-16"
        val due = 1794804600000L
        assertTrue(gate.alreadyScheduledForDue(date, due, date, due))
        assertFalse(gate.alreadyScheduledForDue(date, due, date, due + 90000L))
        assertFalse(gate.alreadyScheduledForDue(date, due, "2026-11-17", due))
        assertFalse(gate.alreadyScheduledForDue(null, null, date, due))
    }

    @Test fun twoAccountsUseMeasuredLeadInsteadOfNearlyExpiredSnapshot() {
        assertEquals(50_000L, FixedSweepPrefetchPolicy.expectedSweepLeadMs(true))
        assertEquals(82_000L, FixedSweepPrefetchPolicy.expectedSweepLeadMs(false))
        // With a 40-second Beta+Alpha sweep, a 50-second lead leaves ~10 sec of age.
        val due = 190_000L
        val completedAt = due - 50_000L + 40_000L
        assertTrue(FixedSweepPrefetchPolicy.canConsume(completedAt, due, 2438, 2646, false))
    }

    @Test fun oldPrefetchSafetyStillRejectsStaleAndIncompleteData() {
        val clock = 1_000_000L
        val total = 2646
        assertTrue(FixedSweepPrefetchPolicy.canConsume(clock - 40_000L, clock, 2438, total, false))
        assertFalse(FixedSweepPrefetchPolicy.canConsume(clock - 50_000L, clock, 2438, total, false))
        assertFalse(FixedSweepPrefetchPolicy.canConsume(clock - 4_000L, clock, 1368, total, false))
        assertFalse(FixedSweepPrefetchPolicy.canConsume(clock - 4_000L, clock, 2438, total, true))
    }
}
