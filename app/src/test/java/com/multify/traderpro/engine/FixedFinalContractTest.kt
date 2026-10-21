package com.multify.traderpro.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FixedFinalContractTest {
    @Test fun finalReferenceIsPinnedToAcceptedV741Session() {
        assertEquals("FIXED-V741-20261021-190239-FINAL", FixedFinalContract.CONTRACT_ID)
        assertEquals("ca0eecaf63d2cc3500e11fd73f8de9bf2d87e39f", FixedFinalContract.SOURCE_COMMIT)
        assertEquals("d807204a6d5d71a69f45ef4d21d7ef63063b85b56d401a6795e8f265a9f393fe", FixedFinalContract.SOURCE_LOG_SHA256)
        assertEquals(107, FixedFinalContract.CANONICAL_LEARNING_CALLS)
        assertEquals(1.1161043030113955, FixedFinalContract.CANONICAL_MULTIFY_MFE_AVG_PCT, 1e-12)
    }

    @Test fun hundredRupeeRuleIsAnOutputOverlayNotAModelUniverseFilter() {
        assertTrue(FixedFinalContract.MIN_FIXED_PUBLICATION_PRICE_EXCLUSIVE == 100.0)
        assertTrue("BOHRAIND" in FixedFinalContract.REFERENCE_FORECAST_SYMBOLS)
        assertFalse("BOHRAIND" in FixedFinalContract.REFERENCE_FIXED_ELIGIBLE_SYMBOLS)
        assertTrue("IPCALAB" in FixedFinalContract.REFERENCE_FIXED_ELIGIBLE_SYMBOLS)
    }

    @Test fun acceptedReferenceSessionSymbolsAreFrozen() {
        assertEquals(
            listOf("UTTAMSUGAR", "CHEMBONDCH", "BOHRAIND", "ORIRAIL", "DWARKESH", "SHAHINVEST", "TCC", "IPCALAB"),
            FixedFinalContract.REFERENCE_FORECAST_SYMBOLS
        )
        assertEquals(
            listOf("UTTAMSUGAR", "CHEMBONDCH", "ORIRAIL", "SHAHINVEST", "IPCALAB"),
            FixedFinalContract.REFERENCE_FIXED_ELIGIBLE_SYMBOLS
        )
    }
}
