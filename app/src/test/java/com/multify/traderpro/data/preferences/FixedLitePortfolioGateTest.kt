package com.multify.traderpro.data.preferences

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FixedLitePortfolioGateTest {
    @Test fun newInstallationStartsWithScanningButAllOrderPermissionsOff() {
        val settings = AppSettings()
        assertTrue(settings.fixedScannerEnabled)
        assertFalse(settings.fixedAutoBuyEnabled)
        assertFalse(settings.workingRangeEnabled)
        assertFalse(settings.legacyPortfolioReconciled)
    }

    @Test fun priorAppPortfolioAcknowledgementIsExplicitAndPersistableState() {
        assertFalse(AppSettings().legacyPortfolioReconciled)
        assertTrue(AppSettings(legacyPortfolioReconciled = true).legacyPortfolioReconciled)
    }
}
