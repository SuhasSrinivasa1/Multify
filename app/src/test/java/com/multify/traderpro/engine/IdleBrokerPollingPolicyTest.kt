package com.multify.traderpro.engine

import org.junit.Assert.*
import org.junit.Test

class IdleBrokerPollingPolicyTest {
    private fun skip(positions: Int = 0, pending: Boolean = false, inFlight: Boolean = false,
        inventoryMs: Long? = 1000, gttMs: Long? = 1000, nowMs: Long = 2000,
        intervalMs: Long = 180_000
    ) = IdleBrokerPollingPolicy.maySkip(true, positions, pending, inFlight,
        inventoryMs, gttMs, nowMs, intervalMs)

    @Test fun confirmedRecentSnapshotsSuppressOnlyIdlePolling() {
        assertTrue(skip()) // external/manual holdings are not app-owned
        assertFalse(skip(positions = 1))
        assertFalse(skip(pending = true))
        assertFalse(skip(inFlight = true))
        assertFalse(skip(inventoryMs = null))
        assertFalse(skip(gttMs = null))
    }

    @Test fun verifiedEmptyPortfolioCanUseThreeMinuteHeartbeat() {
        assertTrue(skip(inventoryMs = 1000, gttMs = 1000, nowMs = 180_999))
        assertFalse(skip(inventoryMs = 1000, nowMs = 181_000))
        assertFalse(skip(gttMs = 1000, nowMs = 181_000))
    }

    @Test fun nonemptyExternalInventoryKeepsOriginalFortyFiveSecondHeartbeat() {
        assertTrue(skip(nowMs = 45_999, intervalMs = 45_000))
        assertFalse(skip(nowMs = 46_000, intervalMs = 45_000))
    }

    @Test fun tenMinuteAfterHoursHeartbeatAppliesOnlyToVerifiedEmptyIdlePortfolio() {
        val slow = IdleBrokerPollingPolicy.EMPTY_AFTER_HOURS_RECHECK_MS
        assertEquals(600_000L, IdleBrokerPollingPolicy.afterHoursRecheckIntervalMs(false, false, true, false, false))
        assertEquals(60_000L, IdleBrokerPollingPolicy.afterHoursRecheckIntervalMs(false, true, true, false, false))
        assertEquals(60_000L, IdleBrokerPollingPolicy.afterHoursRecheckIntervalMs(false, false, false, false, false))
        assertEquals(60_000L, IdleBrokerPollingPolicy.afterHoursRecheckIntervalMs(false, false, true, true, false))
        assertEquals(60_000L, IdleBrokerPollingPolicy.afterHoursRecheckIntervalMs(false, false, true, false, true))
        assertEquals(60_000L, IdleBrokerPollingPolicy.afterHoursRecheckIntervalMs(true, false, true, false, false))
    }

    @Test fun staleOrFutureSnapshotsRequireAnotherBrokerPoll() {
        assertFalse(skip(inventoryMs = 5000, nowMs = 2000))
        assertFalse(IdleBrokerPollingPolicy.maySkip(false, 0, false, false, 1000, 1000, 2000, 180_000))
    }
}
