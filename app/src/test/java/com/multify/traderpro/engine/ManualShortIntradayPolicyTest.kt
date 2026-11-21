package com.multify.traderpro.engine

import org.junit.Assert.*
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class ManualShortIntradayPolicyTest {
    @Test fun shortHasReversedOnePercentHardStopAndLongTrailMilestones() {
        val p = ManualShortIntradayPolicy
        assertEquals(101.0, p.stopForLowWater(100.0, 100.0), 1e-8)
        assertEquals(101.0, p.stopForLowWater(100.0, 99.46), 1e-8)
        assertEquals(99.50, p.stopForLowWater(100.0, 99.45), 1e-8)
        assertEquals(99.50, p.stopForLowWater(100.0, 99.30), 1e-8)
        assertEquals(99.40, p.stopForLowWater(100.0, 99.25), 1e-8)
        assertEquals(99.30, p.stopForLowWater(100.0, 99.15), 1e-8)
        assertEquals(99.20, p.stopForLowWater(100.0, 99.05), 1e-8)
    }

    @Test fun shortStopNeverWidensOnRebound() {
        val p = ManualShortIntradayPolicy
        val first = p.tighterStop(101.0, 100.0, 99.20)
        assertEquals(99.40, first, 1e-8)
        assertEquals(first, p.tighterStop(first, 100.0, 99.80), 1e-8)
        assertEquals(99.10, p.tighterStop(first, 100.0, 98.95), 1e-8)
    }

    @Test fun shortEntriesEndAt1430AndCoverStartsAt1455Ist() {
        val zone = ZoneId.of("Asia/Kolkata")
        fun ist(hour: Int, minute: Int): ZonedDateTime =
            ZonedDateTime.of(2026, 11, 23, hour, minute, 0, 0, zone)
        assertTrue(ManualShortIntradayPolicy.mayOpen(ist(10, 0)))
        assertTrue(ManualShortIntradayPolicy.mayOpen(ist(14, 29)))
        assertFalse(ManualShortIntradayPolicy.mayOpen(ist(14, 30)))
        assertFalse(ManualShortIntradayPolicy.mustSquareOff(ist(14, 54)))
        assertTrue(ManualShortIntradayPolicy.mustSquareOff(ist(14, 55)))
    }

    @Test fun manualShortRecordsBlockReentryUntilBrokerFlat() {
        val trade = ManualShortPosition(
            symbol = "TCS", reference = "MSS1234567890abcd",
            openedAtMs = 1L, quantity = 10, entryPrice = 100.0,
            lowestPrice = 100.0, stopTrigger = 101.0, state = "ENTRY_PENDING"
        )
        assertTrue(trade.unresolved())
        assertFalse(trade.brokerStopVerified())
        assertFalse(trade.copy(state = "PROTECTED", protectionOrderId = "").brokerStopVerified())
        assertTrue(trade.copy(state = "PROTECTED", protectionOrderId = "ALPHA-STOP").brokerStopVerified())
        assertFalse(trade.copy(state = "CLOSED").unresolved())
        assertFalse(trade.copy(state = "CANCELLED_NO_FILL").unresolved())
    }
}
