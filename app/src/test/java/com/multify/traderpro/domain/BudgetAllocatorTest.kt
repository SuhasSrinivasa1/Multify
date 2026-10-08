package com.multify.traderpro.domain

import com.multify.traderpro.engine.BudgetAllocator
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BudgetAllocatorTest {
    @Test fun rejectsWeakConfidence() {
        val d = BudgetAllocator.allocate(200_000.0, .55, 500.0, 5.0, 10.0)
        assertFalse(d.allowed)
    }

    @Test fun capsNotionalWithinBudget() {
        val d = BudgetAllocator.allocate(200_000.0, .92, 500.0, 5.0, 15.0)
        assertTrue(d.quantity >= 0)
        assertTrue(d.allocationRupees <= 200_000.0)
    }
}
