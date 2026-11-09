package com.multify.traderpro.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CashAwareCncSizingTest {
    @Test fun strictBudgetRejectsPartialCashForMultifyAndForecast() {
        val s = CashAwareCncSizing.sizeStrictBudget(50_000.0, 20_000.0, 715.0)
        assertEquals(0, s.quantity)
        assertEquals(0.0, s.deployableCash, 0.0001)
    }

    @Test fun strictBudgetUsesConfiguredCapWhenFullyFunded() {
        val s = CashAwareCncSizing.sizeStrictBudget(50_000.0, 200_000.0, 1_000.0)
        assertEquals(49, s.quantity)
        assertEquals(50_000.0, s.deployableCash, 0.0001)
        assertTrue(s.bufferedPrice > 1_000.0)
    }

    @Test fun fixedUsesWhateverCashRemainsUpToBudget() {
        val s = CashAwareCncSizing.sizeFixedAvailable(50_000.0, 20_000.0, 1_000.0)
        assertEquals(19, s.quantity)
        assertEquals(20_000.0, s.deployableCash, 0.0001)
        assertEquals(0.0, s.cashReserve, 0.0001)
    }

    @Test fun fixedPerStockBudgetDoesNotAccumulateAcrossOrders() {
        val first = CashAwareCncSizing.sizeFixedAvailable(30_000.0, 97_500.0, 1_000.0)
        val second = CashAwareCncSizing.sizeFixedAvailable(30_000.0, 67_500.0, 500.0)
        val third = CashAwareCncSizing.sizeFixedAvailable(30_000.0, 37_500.0, 250.0)
        assertEquals(29, first.quantity)
        assertEquals(58, second.quantity)
        assertEquals(117, third.quantity)
        listOf(first, second, third).forEach {
            assertEquals(30_000.0, it.deployableCash, 0.0001)
        }
        val limitedByFreshCash = CashAwareCncSizing.sizeFixedAvailable(30_000.0, 8_000.0, 500.0)
        assertEquals(8_000.0, limitedByFreshCash.deployableCash, 0.0001)
        assertEquals(15, limitedByFreshCash.quantity)
    }

    @Test fun fixedStillCapsAtConfiguredBudget() {
        val s = CashAwareCncSizing.sizeFixedAvailable(50_000.0, 200_000.0, 1_000.0)
        assertEquals(49, s.quantity)
        assertEquals(50_000.0, s.deployableCash, 0.0001)
    }
}
