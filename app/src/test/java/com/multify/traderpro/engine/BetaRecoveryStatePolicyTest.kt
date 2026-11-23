package com.multify.traderpro.engine
import org.junit.Assert.*
import org.junit.Test
class BetaRecoveryStatePolicyTest {
    @Test fun activeCooldownSurvivesProcessRestartAsRemainingWallTime() {
        assertEquals(35_000L, BetaRecoveryStatePolicy.remainingCooldownMs(135_000L, 100_000L))
        assertEquals(0L, BetaRecoveryStatePolicy.remainingCooldownMs(99_999L, 100_000L))
        assertEquals(0L, BetaRecoveryStatePolicy.cooldownUntilEpochMs(100_000L, 0L))
    }
    @Test fun incidentHistoryRestoresOnlyCurrentHourAndRejectsFutureTimestamps() {
        val now = 10_000_000L
        val raw = "${now - 3_000_000},${now - 10_000},${now - 1},${now + 1}"
        assertEquals(listOf(now - 10_000, now - 1), BetaRecoveryStatePolicy.parseRecentIncidents(raw, now))
    }
    @Test fun rollingMinuteAdmissionsRestoreAndExpireAtSixtySeconds() {
        val now = 10_000_000L
        val raw = "${now - 60_001},${now - 59_999},${now - 1_000},${now}"
        assertEquals(listOf(now - 59_999, now - 1_000, now), BetaRecoveryStatePolicy.parseRecentAdmissions(raw, now))
        assertEquals("${now - 59_999},${now - 1_000},${now}",
            BetaRecoveryStatePolicy.serializeAdmissions(listOf(now - 60_001, now - 59_999, now - 1_000, now), now))
    }
}
