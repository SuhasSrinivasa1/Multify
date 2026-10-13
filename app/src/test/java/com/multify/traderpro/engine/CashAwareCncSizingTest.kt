package com.multify.traderpro.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CashAwareCncSizingTest {
    @Test fun leavesCashAndPriceHeadroomForGrowwRms() {
        val s = CashAwareCncSizing.size(50_000.0, 26_259.44, 715.0)
        assertEquals(34, s.quantity)
        assertTrue(s.cashReserve >= 1_000.0)
        assertTrue(s.deployableCash < 26_259.44)
        assertTrue(s.bufferedPrice > 715.0)
    }

    @Test fun configuredBudgetStillCapsLargeBalances() {
        val s = CashAwareCncSizing.size(50_000.0, 200_000.0, 1_000.0)
        assertEquals(49, s.quantity)
        assertEquals(50_000.0, s.deployableCash, 0.0001)
    }

    @Test fun cannotFundOneShareReturnsZero() {
        val s = CashAwareCncSizing.size(50_000.0, 1_500.0, 1_000.0)
        assertEquals(0, s.quantity)
    }
}
