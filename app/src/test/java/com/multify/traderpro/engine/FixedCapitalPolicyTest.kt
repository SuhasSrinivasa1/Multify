package com.multify.traderpro.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class FixedCapitalPolicyTest {
    @Test fun neverExpandsToLargerConfiguredBudget() {
        assertEquals(100_000.0, FixedCapitalPolicy.cap(200_000.0, 100_000.0, 100_000.0), 0.001)
    }

    @Test fun respectsSmallerExistingCampaignCap() {
        assertEquals(80_000.0, FixedCapitalPolicy.cap(80_000.0, 100_000.0, 100_000.0), 0.001)
    }

    @Test fun fallsBackWithoutCreatingNewCapital() {
        assertEquals(55_000.0, FixedCapitalPolicy.cap(0.0, 0.0, 55_000.0), 0.001)
        assertEquals(0.0, FixedCapitalPolicy.cap(0.0, 0.0, 0.0), 0.001)
    }
}
