package com.multify.traderpro.engine

import org.junit.Assert.*
import org.junit.Test

class FixedAutoBuyCooldownQueueTest {
    private val t0 = 1_800_000_000_000L
    private fun row(id: Long, at: Long = t0 - 10_000L, activated: Long = t0 - 20_000L) =
        FixedAutoBuyCooldownQueue.Pending(id, "SYM$id", at, activated)

    @Test fun shortCooldownSchedulesOnlyFreshPostActivationIntents() {
        val q = FixedAutoBuyCooldownQueue()
        val plan = q.afterCooldown(listOf(row(1), row(2, at = t0 - 301_000L),
            row(3, activated = t0)), t0, t0 + 90_000L)
        assertEquals(1, plan.pending.size)
        assertEquals(2, plan.expiredOrIneligible)
        assertEquals(1, plan.retryableCount)
        assertEquals(t0 + 91_000L, plan.retryAtMs)
    }

    @Test fun longCooldownCannotReviveExpiredRecommendations() {
        val q = FixedAutoBuyCooldownQueue()
        val plan = q.afterCooldown(listOf(row(1)), t0, t0 + 900_000L)
        assertEquals(1, plan.pending.size)
        assertNull(plan.retryAtMs)
        assertEquals(0, plan.retryableCount)
    }

    @Test fun cooldownNearFreshnessDeadlineNeedsRevalidationHeadroom() {
        val q = FixedAutoBuyCooldownQueue()
        // Current event has only 60s left: don't schedule a retry at 50s.
        val plan = q.afterCooldown(listOf(row(1, at = t0 - 240_000L)), t0, t0 + 50_000L)
        assertNull(plan.retryAtMs)
        assertEquals(0, plan.retryableCount)
    }

    @Test fun boundedIntentsAndToggleReset() {
        val q = FixedAutoBuyCooldownQueue()
        val plan = q.afterCooldown((1L..80L).map { row(it) }, t0, t0 + 1_000L)
        assertEquals(48, plan.pending.size)
        assertEquals(48, q.pendingCount())
        q.clear()
        assertEquals(0, q.pendingCount())
    }

    @Test fun activationChangedDuringCooldownInvalidatesOldIntent() {
        val q = FixedAutoBuyCooldownQueue()
        q.afterCooldown(listOf(row(1)), t0, t0 + 60_000L)
        val altered = q.afterCooldown(listOf(row(1, activated = t0)), t0, t0 + 60_000L)
        assertTrue(altered.pending.isEmpty())
        assertEquals(0, q.pendingCount())
    }
}
