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

    @Test fun fixedStillCapsAtConfiguredBudget() {
        val s = CashAwareCncSizing.sizeFixedAvailable(50_000.0, 200_000.0, 1_000.0)
        assertEquals(49, s.quantity)
        assertEquals(50_000.0, s.deployableCash, 0.0001)
    }
}
