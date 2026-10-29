package com.multify.traderpro.engine

import org.junit.Assert.*
import org.junit.Test

class AppPositionOwnershipPolicyTest {
    private fun allowed(engine: String, reference: String, strategy: String = "App buy"): Boolean =
        AppPositionOwnershipPolicy.appOpened(engine, "LONG", "CNC", "GROWW-ORDER-1", reference, strategy, 10)

    @Test fun appBuyRequiresKnownEngineAndApplicationReference() {
        assertTrue(allowed("MULTIFY_HOLDING", "MLH1234567890abcd"))
        assertTrue(allowed("FORECAST_HOLDING", "FLH1234567890abcd"))
        assertTrue(allowed("FIXED_HOLDING", "FXH1234567890abcd"))
        assertTrue(allowed("FIXED_HOLDING", "FXM1234567890abcd"))
        assertFalse(allowed("PORTFOLIO_HOLDING", "PFH1234567890abcd"))
        assertFalse(allowed("FIXED_HOLDING", "PFH1234567890abcd"))
        assertFalse(allowed("FORECAST_HOLDING", "unknown"))
        assertFalse(allowed("FIXED_HOLDING", "FXH1234567890abcd", "rebased to aggregated Groww portfolio"))
        assertFalse(allowed("FIXED_HOLDING", "FXH1234567890abcd", "OWNERSHIP_UNVERIFIED"))
        assertFalse(AppPositionOwnershipPolicy.appOpened("FIXED_HOLDING", "LONG", "CNC", "EXTERNAL_GROWW_HOLDING", "FXH1234567890abcd", "", 10))
    }

    @Test fun mixedOrReducedBrokerHoldingsAreAlwaysBlocked() {
        assertTrue(AppPositionOwnershipPolicy.exclusiveBrokerQuantity(10, 10, 0))
        assertTrue(AppPositionOwnershipPolicy.exclusiveBrokerQuantity(10, 0, 10))
        assertFalse(AppPositionOwnershipPolicy.exclusiveBrokerQuantity(10, 20, 0))
        assertFalse(AppPositionOwnershipPolicy.exclusiveBrokerQuantity(10, 5, 0))
        assertFalse(AppPositionOwnershipPolicy.exclusiveBrokerQuantity(10, 0, 0))
    }

    @Test fun onlyAppGeneratedGttReferencesCanBeCleanedUp() {
        assertTrue(AppPositionOwnershipPolicy.isAppProtectionReference("MTG1234567890abcd"))
        assertFalse(AppPositionOwnershipPolicy.isAppProtectionReference("PFH1234567890abcd"))
        assertFalse(AppPositionOwnershipPolicy.isAppProtectionReference(null))
    }
}
