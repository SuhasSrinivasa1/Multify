package com.multify.traderpro.engine

import org.junit.Assert.*
import org.junit.Test

class BudgetOnlyCncSizingTest {
    @Test fun configuredPerStockAmountDeterminesQtyWithoutAnyWalletInput() {
        val sizing = BudgetOnlyCncSizing.size(30_000.0, 364.70)
        assertEquals(80, sizing.quantity)
        assertEquals(30_000.0, sizing.deployableCash, 0.01)
        assertTrue(sizing.quantity * sizing.bufferedPrice <= 30_000.0)
        assertTrue((sizing.quantity + 1) * sizing.bufferedPrice > 30_000.0)
    }

    @Test fun budgetIsReappliedForEveryIndependentOrderNotDailyReserved() {
        val first = BudgetOnlyCncSizing.size(30_000.0, 2456.90)
        val second = BudgetOnlyCncSizing.size(30_000.0, 275.18)
        assertTrue(first.quantity > 0)
        assertTrue(second.quantity > 0)
        assertEquals(30_000.0, first.deployableCash, 0.01)
        assertEquals(30_000.0, second.deployableCash, 0.01)
    }

    @Test fun invalidOrTooSmallBudgetDoesNotCreateZeroCashBrokerOrders() {
        assertEquals(0, BudgetOnlyCncSizing.size(0.0, 100.0).quantity)
        assertEquals(0, BudgetOnlyCncSizing.size(100.0, 1_000.0).quantity)
        assertEquals(0, BudgetOnlyCncSizing.size(Double.NaN, 100.0).quantity)
        assertEquals(0, BudgetOnlyCncSizing.size(30_000.0, Double.POSITIVE_INFINITY).quantity)
    }
}
