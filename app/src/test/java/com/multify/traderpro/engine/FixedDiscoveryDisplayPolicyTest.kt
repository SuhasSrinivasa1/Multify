package com.multify.traderpro.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class FixedDiscoveryDisplayPolicyTest {
    private fun state(
        coverage: Double = .538, baseline: Boolean = false,
        alphaCooldown: Boolean = false, betaAvailable: Boolean = false,
        hasScan: Boolean = true, aborted: Boolean = false
    ) = FixedDiscoveryDisplayPolicy.label(
        hasScan, aborted, coverage, baseline,
        lastScanAgeMs = 30_000L, staleThresholdMs = 180_000L,
        minCoverage = .85, alphaReadCooldown = alphaCooldown,
        betaReadOnlyReady = betaAvailable
    )

    @Test fun lastIncomplete53PercentSweepIsNeverMistakenForWarmupProgress() {
        assertEquals("DEGRADED", state())
        assertEquals("DEGRADED", state(coverage = .92, baseline = true, aborted = true))
    }

    @Test fun alphaCooldownExplainsTheStatusAndBetaRecoveryIsReadOnly() {
        assertEquals("PAUSED_ALPHA_COOLDOWN", state(alphaCooldown = true))
        assertEquals("BETA_READ_ONLY_RECOVERY", state(alphaCooldown = true, betaAvailable = true))
    }

    @Test fun twoValidDistinctSweepsAreStillNeededForFrozenBaseline() {
        assertEquals("WARMING", state(coverage = .92))
        assertEquals("HEALTHY", state(coverage = .92, baseline = true))
        assertEquals("WAITING", state(hasScan = false, coverage = 0.0))
    }
}
