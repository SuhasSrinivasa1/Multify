package com.multify.traderpro.engine

import org.junit.Assert.*
import org.junit.Test

class IdleBrokerPollingPolicyTest {
    private fun skip(positions: Int = 0, pending: Boolean = false, inFlight: Boolean = false,
        inventoryMs: Long? = 1000, gttMs: Long? = 1000, nowMs: Long = 2000
    ) = IdleBrokerPollingPolicy.maySkip(true, positions, pending, inFlight,
        inventoryMs, gttMs, nowMs, 45_000)

    @Test fun confirmedRecentSnapshotsSuppressOnlyIdlePolling() {
        assertTrue(skip()) // external/manual holdings are not app-owned
        assertFalse(skip(positions = 1))
        assertFalse(skip(pending = true))
        assertFalse(skip(inFlight = true))
        assertFalse(skip(inventoryMs = null))
        assertFalse(skip(gttMs = null))
    }

    @Test fun staleOrFutureSnapshotsRequireAnotherBrokerPoll() {
        assertFalse(skip(inventoryMs = 1000, nowMs = 50_000))
        assertFalse(skip(gttMs = 1000, nowMs = 50_000))
        assertFalse(skip(inventoryMs = 5000, nowMs = 2000))
        assertFalse(IdleBrokerPollingPolicy.maySkip(false, 0, false, false, 1000, 1000, 2000, 45_000))
    }
}
