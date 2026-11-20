package com.multify.traderpro.engine

import org.junit.Assert.*
import org.junit.Test

/**
 * Regression drawn from broker-confirmed 20-Nov examples, not an
 * out-of-sample return-validation of the chosen safety threshold.
 */
class WorkingRangeEntryExtensionV9023Test {
    @Test fun asianhotnrWouldNotChasePeakAfterFirstQualifyingPrice() {
        // 13:30:27: first 321.50; 13:33:46: confirmed 326.50;
        // 13:34:28: broker BUY 326.50, subsequently stopped at 319.93.
        assertEquals("FIRST_QUALIFIED_PRICE_EXTENSION_TOO_HIGH",
            WorkingRangeEntryMomentumPolicy.runupRejectionReason(321.50, 326.50))
    }

    @Test fun aNearFirstQualifiedPriceIsNotVetoedSolelyByExtension() {
        assertNull(WorkingRangeEntryMomentumPolicy.runupRejectionReason(305.80, 306.80))
        assertNull(WorkingRangeEntryMomentumPolicy.runupRejectionReason(305.80, 305.80))
    }

    @Test fun missingFirstObservationFailsClosedInsteadOfAssumingZeroRunup() {
        assertEquals("FIRST_QUALIFIED_REFERENCE_UNAVAILABLE",
            WorkingRangeEntryMomentumPolicy.runupRejectionReason(0.0, 326.50))
        assertEquals("FIRST_QUALIFIED_REFERENCE_UNAVAILABLE",
            WorkingRangeEntryMomentumPolicy.runupRejectionReason(Double.NaN, 326.50))
    }

    @Test fun fixedThresholdAndExitPolicyAreNotChangedByThisGuard() {
        assertEquals(0.75, WorkingRangeEntryMomentumPolicy.MAX_RUNUP_FROM_FIRST_QUALIFIED_PCT, 0.0001)
        assertEquals(1.0, WorkingRangeExitPolicy.HARD_STOP_LOSS_PCT, 0.0001)
    }
}
