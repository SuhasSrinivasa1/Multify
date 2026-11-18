package com.multify.traderpro.engine

import org.junit.Assert.*
import org.junit.Test

class ClosedBuyReplayPolicyTest {
    private val closed = listOf(ClosedBuyReplayPolicy.ConfirmedClosedBuy(
        "VESUVIUS", 107, "GLT-SAME-ORIGINAL", 1000L, true))
    private fun replay(
        visibleQty: Int = 0, activeGtt: Boolean = false,
        order: String = "GLT-SAME-ORIGINAL", openedAt: Long = 1200L,
        rows: List<ClosedBuyReplayPolicy.ConfirmedClosedBuy> = closed
    ) = ClosedBuyReplayPolicy.isProvenReplay(
        "VESUVIUS", 107, order, openedAt, visibleQty, activeGtt, rows)

    @Test fun brokerClosedOriginalBuyCannotReappearAsFreshHolding() {
        assertTrue(replay())
        assertFalse(replay(visibleQty=107))
        assertFalse(replay(activeGtt=true))
        assertFalse(replay(order="NEW-BROKER-ORDER"))
        assertFalse(replay(openedAt=999))
        assertFalse(replay(rows=closed.map { it.copy(brokerFillConfirmed=false) }))
        assertFalse(replay(rows=closed.map { it.copy(quantity=108) }))
        assertFalse(replay(rows=closed.map { it.copy(symbol="INDOCO") }))
    }
}
