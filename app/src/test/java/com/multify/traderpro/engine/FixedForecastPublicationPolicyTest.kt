package com.multify.traderpro.engine

import org.junit.Assert.*
import org.junit.Test

class FixedForecastPublicationPolicyTest {
    @Test fun fullyQualifiedDiscoveredPrimaryCanPublishOnFirstScan() {
        assertTrue(FixedForecastPublicationPolicy.isReady(true, true, true, 0))
    }
    @Test fun fallbackStillNeedsTwoIndependentPrimaryConfirmations() {
        assertFalse(FixedForecastPublicationPolicy.isReady(true, true, false, 0))
        assertFalse(FixedForecastPublicationPolicy.isReady(true, true, false, 1))
        assertTrue(FixedForecastPublicationPolicy.isReady(true, true, false, 2))
    }
    @Test fun noCandidateOrIncompleteDiscoveryNeverPublishes() {
        assertFalse(FixedForecastPublicationPolicy.isReady(false, true, true, 0))
        assertFalse(FixedForecastPublicationPolicy.isReady(true, false, true, 0))
    }
}
