package com.multify.traderpro.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrokerFlatTerminalGttQuarantinePolicyTest {
    private fun check(
        brokerQty: Int = 0,
        localQty: Int = 107,
        id: String? = "gtt-1",
        status: String? = "CANCELLED",
        active: Set<String> = emptySet()
    ): Boolean = BrokerFlatTerminalGttQuarantinePolicy.shouldQuarantine(
        brokerQty, localQty, id, status, active
    )

    @Test fun brokerFlatCancelledOrExpiredGttIsQuarantined() {
        assertTrue(check())
        assertTrue(check(status = "canceled"))
        assertTrue(check(status = "EXPIRED"))
        assertTrue(check(status = "REJECTED"))
        assertTrue(check(status = "FAILED"))
    }

    @Test fun activeGttCannotBeQuarantinedEvenWhenInventoryShowsZero() {
        assertFalse(check(status = "CANCELLED", active = setOf("gtt-1")))
        assertFalse(check(status = "ACTIVE"))
        assertFalse(check(status = "OPEN"))
    }

    @Test fun triggeredOrCompletedParentMustGoThroughChildSellReconciliation() {
        assertFalse(check(status = "TRIGGERED"))
        assertFalse(check(status = "COMPLETED"))
        assertFalse(check(status = "EXECUTED"))
    }

    @Test fun unknownStatusOrIdMustNotBeTreatedAsConfirmedTerminal() {
        assertFalse(check(status = null))
        assertFalse(check(id = null))
        assertFalse(check(id = "  "))
    }

    @Test fun positionStillExistsOrBrokerInventoryNotFlat() {
        assertFalse(check(brokerQty = 1))
        assertFalse(check(brokerQty = 107))
        assertFalse(check(localQty = 0))
        assertFalse(check(brokerQty = -1))
    }
}
