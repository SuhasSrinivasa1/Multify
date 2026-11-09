package com.multify.traderpro.engine

import org.junit.Assert.*
import org.junit.Test

class ExecutionFillQuantityPolicyTest {
    @Test fun partialCancelledLimitBuyRecordsOnlySharesActuallyFilled() {
        assertEquals(22, ExecutionFillQuantityPolicy.confirmedFilledQuantity(80, 22, "CANCELLED"))
        assertEquals(12, ExecutionFillQuantityPolicy.confirmedFilledQuantity(12, 12, "EXECUTED"))
    }

    @Test fun missingQuantityNeverTurnsPartialOrOpenOrderIntoFullFill() {
        assertNull(ExecutionFillQuantityPolicy.confirmedFilledQuantity(80, null, "CANCELLED"))
        assertNull(ExecutionFillQuantityPolicy.confirmedFilledQuantity(80, null, "OPEN"))
        assertNull(ExecutionFillQuantityPolicy.confirmedFilledQuantity(80, 0, "EXECUTED"))
        assertNull(ExecutionFillQuantityPolicy.confirmedFilledQuantity(80, 90, "EXECUTED"))
        assertEquals(80, ExecutionFillQuantityPolicy.confirmedFilledQuantity(80, null, "EXECUTED"))
    }
}
