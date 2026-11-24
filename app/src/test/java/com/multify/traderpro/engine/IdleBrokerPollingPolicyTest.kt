package com.multify.traderpro.engine

import org.junit.Assert.*
import org.junit.Test

class IdleBrokerPollingPolicyTest {
    private fun skip(marketOpen: Boolean = true, positions: Int = 0, pending: Boolean = false, inFlight: Boolean = false,
        inventoryMs: Long? = 1000, gttMs: Long? = 1000, nowMs: Long = 2000,
        intervalMs: Long = 300_000
    ) = IdleBrokerPollingPolicy.maySkip(marketOpen, positions, pending, inFlight,
        inventoryMs, gttMs, nowMs, intervalMs)

    @Test fun confirmedRecentSnapshotsSuppressOnlyIdlePolling() {
        assertTrue(skip()) // external/manual holdings are not app-owned
        assertFalse(skip(positions = 1))
        assertFalse(skip(pending = true))
        assertFalse(skip(inFlight = true))
        assertFalse(skip(inventoryMs = null))
        assertFalse(skip(gttMs = null))
    }

    @Test fun verifiedEmptyPortfolioCanUseFiveMinuteHeartbeat() {
        assertTrue(skip(inventoryMs = 1000, gttMs = 1000, nowMs = 299_999))
        assertFalse(skip(inventoryMs = 1000, nowMs = 300_000))
        assertFalse(skip(gttMs = 1000, nowMs = 181_000))
    }

    @Test fun nonemptyExternalInventoryUsesFiveMinuteIdleHeartbeat() {
        assertTrue(skip(nowMs = 299_999, intervalMs = 300_000))
        assertFalse(skip(nowMs = 300_000, intervalMs = 300_000))
    }

    @Test fun staleOrFutureSnapshotsRequireAnotherBrokerPoll() {
        assertFalse(skip(inventoryMs = 5000, nowMs = 2000))
        assertTrue(IdleBrokerPollingPolicy.maySkip(false, 0, false, false, 1000, 1000, 2000, 300_000))
    }

    @Test fun afterHoursNeverSkipsWhenLocalExposureOrEntryIsPending() {
        assertFalse(skip(marketOpen=false,positions=1))
        assertFalse(skip(marketOpen=false,pending=true))
        assertFalse(skip(marketOpen=false,inFlight=true))
    }
}
