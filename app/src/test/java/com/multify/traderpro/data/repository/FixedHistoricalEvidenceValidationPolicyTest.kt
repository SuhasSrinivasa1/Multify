package com.multify.traderpro.data.repository

import org.junit.Assert.*
import org.junit.Test

class FixedHistoricalEvidenceValidationPolicyTest {
    private fun record() = FixedHistoricalEvidenceRow(
        date = "2026-11-04", symbol = "SGRL", publishedAtMs = 1_000L,
        entryPrice = 669.0, targetPrice = 676.47, targetPct = 1.1161043,
        latestObservedPrice = 656.95, lastObservedAtMs = 2_000L,
        maximumFavourablePct = 0.3, maximumAdversePct = 2.84,
        observedTargetHit = false, snapshotOutcome = "UNRESOLVED_AT_SNAPSHOT",
        observedAfterPublication = true
    )

    @Test fun acceptsHistoricalResearchEvenWhenOutcomeUnresolved() {
        assertTrue(FixedHistoricalEvidenceValidationPolicy.valid(record()))
    }

    @Test fun importedSnapshotCanNeverClaimRealizedBrokerTrade() {
        assertFalse(FixedHistoricalEvidenceValidationPolicy.valid(record().copy(isRealizedTrade = true)))
        assertFalse(FixedHistoricalEvidenceValidationPolicy.valid(record().copy(entryPrice = 0.0)))
        assertFalse(FixedHistoricalEvidenceValidationPolicy.valid(record().copy(date = "not-a-date")))
        assertFalse(FixedHistoricalEvidenceValidationPolicy.valid(record().copy(lastObservedAtMs = 0L)))
    }
}
