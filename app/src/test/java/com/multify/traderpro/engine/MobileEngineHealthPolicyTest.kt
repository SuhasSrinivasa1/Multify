package com.multify.traderpro.engine

import org.junit.Assert.*
import org.junit.Test

class MobileEngineHealthPolicyTest {
    @Test fun neverMarksStalledScanAsLicenceToResetIt() {
        val p = MobileEngineHealthPolicy
        val x = p.assess(true, true, true, true, 1_000_000L,
            400_000L, 999_000L, 0)
        assertEquals("FIXED_SCAN_PROGRESS_STALE", x.status)
        assertTrue(x.fixedScanStalled)
        assertTrue(x.fixedWorkerRunning)
    }
    @Test fun brokerCooldownIsNotAnEngineStall() {
        val snapshot = MobileEngineHealthPolicy.assess(true, true, true, true,
            1_000_000L, 400_000L, 999_000L, 0, brokerCooldownActive = true)
        assertEquals("BROKER_API_COOLDOWN", snapshot.status)
        assertFalse(snapshot.fixedScanStalled)
        assertTrue(snapshot.fixedWorkerRunning)
    }

    @Test fun portfolioProtectionIsMoreImportantThanResearch() {
        val p = MobileEngineHealthPolicy
        val x = p.assess(true, true, true, false, 1_000_000L,
            999_000L, 900_000L, 1)
        assertEquals("PROTECTION_MONITOR_STALE", x.status)
        assertTrue(x.protectionMonitorStalled)
        assertFalse(x.networkValidated)
    }
    @Test fun idleMarketCloseIsNotAStalledScan() {
        val x = MobileEngineHealthPolicy.assess(false, true, true, true,
            1_000_000L, 100_000L, 0L, 0)
        assertEquals("RUNNING", x.status)
    }
}
