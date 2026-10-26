package com.multify.traderpro.engine

import org.junit.Assert.*
import org.junit.Test

class FixedHistoricalSlotPolicyTest {
    @Test fun reserveOnDispatchRatherThanAfterNetworkCompletes() {
        assertEquals(20L, FixedHistoricalSlotPolicy.reservedNextSlotMs(0, 20))
        assertEquals(0L, FixedHistoricalSlotPolicy.waitMs(1050, 1020, 1000))
        assertEquals(20L, FixedHistoricalSlotPolicy.waitMs(1000, 1020, 0))
    }

    @Test fun cooldownStillBlocksSlotReservation() {
        assertEquals(5000L, FixedHistoricalSlotPolicy.waitMs(1000, 1020, 6000))
    }
}
