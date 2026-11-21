package com.multify.traderpro.engine

import org.junit.Assert.*
import org.junit.Test

class AppPositionOwnershipPolicyTest {
    private fun allowed(engine: String, reference: String, strategy: String = "App buy"): Boolean =
        AppPositionOwnershipPolicy.appOpened(engine, "LONG", "CNC", "GROWW-ORDER-1", reference, strategy, 10)

    @Test fun appBuyRequiresKnownEngineAndApplicationReference() {
        assertTrue(allowed("MULTIFY_HOLDING", "MLH1234567890abcd"))
        assertTrue(allowed("UNIVEST_HOLDING", "UVH1234567890abcd"))
        assertFalse(allowed("UNIVEST_HOLDING", "MLH1234567890abcd"))
        assertTrue(allowed("FORECAST_HOLDING", "FLH1234567890abcd"))
        assertTrue(allowed("FIXED_HOLDING", "FXH1234567890abcd"))
        assertTrue(allowed("FIXED_HOLDING", "FXM1234567890abcd"))
        assertTrue(allowed("FIXED_HOLDING", "WRM1234567890abcd"))
        assertTrue(AppPositionOwnershipPolicy.appOpened(
            "MANUAL_HOLDING", "LONG", "MIS", "GROWW-ORDER-1",
            "MNH1234567890abcd", "Manual MIS", 10
        ))
        assertFalse(allowed("MANUAL_HOLDING", "FXH1234567890abcd"))
        assertFalse(allowed("FIXED_HOLDING", "MNH1234567890abcd"))
        assertFalse(allowed("PORTFOLIO_HOLDING", "PFH1234567890abcd"))
        assertFalse(allowed("FIXED_HOLDING", "PFH1234567890abcd"))
        assertFalse(allowed("FORECAST_HOLDING", "unknown"))
        assertFalse(allowed("FIXED_HOLDING", "FXH1234567890abcd", "rebased to aggregated Groww portfolio"))
        assertFalse(allowed("FIXED_HOLDING", "FXH1234567890abcd", "OWNERSHIP_UNVERIFIED"))
        assertFalse(AppPositionOwnershipPolicy.appOpened("FIXED_HOLDING", "LONG", "CNC", "EXTERNAL_GROWW_HOLDING", "FXH1234567890abcd", "", 10))
    }

    @Test fun knownAppMisLongIsOwnedButExternalMisNeverIs() {
        assertFalse(AppPositionOwnershipPolicy.appOpened(
            "FIXED_HOLDING", "LONG", "MIS", "BROKER-ORDER-123", "FXM1234567890abcd", "Fixed MIS", 10
        ))
        assertTrue(AppPositionOwnershipPolicy.appOpened(
            "MANUAL_HOLDING", "LONG", "MIS", "BROKER-ORDER-456", "MNH1234567890abcd", "Manual MIS", 8
        ))
        assertFalse(AppPositionOwnershipPolicy.appOpened(
            "FIXED_HOLDING", "SHORT", "MIS", "BROKER-ORDER-123", "FXM1234567890abcd", "", 10
        ))
        assertFalse(AppPositionOwnershipPolicy.appOpened(
            "FIXED_HOLDING", "LONG", "MIS", "", "FXM1234567890abcd", "", 10
        ))
        assertFalse(AppPositionOwnershipPolicy.appOpened(
            "MANUAL_HOLDING", "LONG", "CNC", "BROKER-ORDER-456", "MNH1234567890abcd", "Manual CNC", 8
        ))
    }

    @Test fun mixedOrReducedBrokerHoldingsAreAlwaysBlocked() {
        assertTrue(AppPositionOwnershipPolicy.exclusiveBrokerQuantity(10, 10, 0))
        assertTrue(AppPositionOwnershipPolicy.exclusiveBrokerQuantity(10, 0, 10))
        assertFalse(AppPositionOwnershipPolicy.exclusiveBrokerQuantity(10, 20, 0))
        assertFalse(AppPositionOwnershipPolicy.exclusiveBrokerQuantity(10, 5, 0))
        assertFalse(AppPositionOwnershipPolicy.exclusiveBrokerQuantity(10, 0, 0))
    }

    @Test fun freshPostFillInventoryLagProofIsStrictlyTimeBounded() {
        assertTrue(AppPositionOwnershipPolicy.freshPostFillInventoryLagAllowed(1_000L, 1_000L))
        assertTrue(AppPositionOwnershipPolicy.freshPostFillInventoryLagAllowed(1_000L, 121_000L))
        assertFalse(AppPositionOwnershipPolicy.freshPostFillInventoryLagAllowed(1_000L, 121_001L))
        assertFalse(AppPositionOwnershipPolicy.freshPostFillInventoryLagAllowed(1_000L, 999L))
        assertFalse(AppPositionOwnershipPolicy.freshPostFillInventoryLagAllowed(0L, 1_000L))
    }

    @Test fun onlyAppGeneratedGttReferencesCanBeCleanedUp() {
        assertTrue(AppPositionOwnershipPolicy.isAppProtectionReference("MTG1234567890abcd"))
        assertFalse(AppPositionOwnershipPolicy.isAppProtectionReference("PFH1234567890abcd"))
        assertFalse(AppPositionOwnershipPolicy.isAppProtectionReference(null))
    }
}
