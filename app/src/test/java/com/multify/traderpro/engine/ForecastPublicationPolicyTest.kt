package com.multify.traderpro.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForecastPublicationPolicyTest {
    @Test fun neverPublishesBeforeBroadDiscoveryIsReady() {
        assertFalse(ForecastPublicationPolicy.isReady(false, true, true, 5))
    }

    @Test fun marketWideDiscoveredEarlyPrimaryCanPublishWithoutAnotherScan() {
        assertTrue(ForecastPublicationPolicy.isReady(true, true, true, 0))
    }

    @Test fun fallbackPrimaryRetainsOnePriorConfirmation() {
        assertFalse(ForecastPublicationPolicy.isReady(true, true, false, 0))
        assertTrue(ForecastPublicationPolicy.isReady(true, true, false, 1))
    }

    @Test fun nonPrimaryNeverPublishes() {
        assertFalse(ForecastPublicationPolicy.isReady(true, false, true, 10))
    }
}
