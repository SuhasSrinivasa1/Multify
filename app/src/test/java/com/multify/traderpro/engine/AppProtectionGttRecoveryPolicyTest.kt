package com.multify.traderpro.engine

import com.multify.traderpro.data.network.GttOrder
import com.multify.traderpro.data.network.SmartOrderPayload
import org.junit.Assert.*
import org.junit.Test

class AppProtectionGttRecoveryPolicyTest {
    private val positionId = 41L
    private val symbol = "FDC"
    private val openedAtMs = 1_798_500_000_000L
    private val quantity = 124

    private fun gtt(
        reference: String?,
        id: String = "gtt-1",
        status: String = "ACTIVE",
        symbol: String = "FDC",
        quantity: Int = 124,
        product: String = "CNC",
        transaction: String = "SELL",
        direction: String = "DOWN",
        trigger: String = "386.15"
    ) = SmartOrderPayload(
        smartOrderId = id,
        smartOrderType = "GTT",
        referenceId = reference,
        status = status,
        tradingSymbol = symbol,
        quantity = quantity,
        productType = product,
        triggerPrice = trigger,
        triggerDirection = direction,
        order = GttOrder(orderType = "MARKET", transactionType = transaction)
    )

    @Test fun exactAppHardStopReferenceIsRecovered() {
        val ref = AppProtectionGttRecoveryPolicy.expectedReference("MHS", positionId, symbol, openedAtMs)
        val recovered = AppProtectionGttRecoveryPolicy.recover(
            positionId, symbol, openedAtMs, quantity, listOf(gtt(ref))
        )
        assertNotNull(recovered)
        assertEquals(HoldingSmartOrderPolicy.MODE_HARD_STOP_GTT, recovered!!.mode)
        assertEquals("gtt-1", recovered.payload.smartOrderId)
        assertEquals(386.15, recovered.triggerPrice, 0.0001)
        assertEquals("DOWN", recovered.direction)
    }

    @Test fun sameSymbolManualOrMismatchedGttIsNeverAdopted() {
        val expected = AppProtectionGttRecoveryPolicy.expectedReference("MHS", positionId, symbol, openedAtMs)
        assertNull(AppProtectionGttRecoveryPolicy.recover(
            positionId, symbol, openedAtMs, quantity, listOf(gtt("manual-reference"))
        ))
        assertNull(AppProtectionGttRecoveryPolicy.recover(
            positionId, symbol, openedAtMs, quantity, listOf(gtt(expected, quantity = 125))
        ))
        assertNull(AppProtectionGttRecoveryPolicy.recover(
            positionId, symbol, openedAtMs, quantity, listOf(gtt(expected, product = "MIS"))
        ))
        assertNull(AppProtectionGttRecoveryPolicy.recover(
            positionId, symbol, openedAtMs, quantity, listOf(gtt(expected, transaction = "BUY"))
        ))
    }

    @Test fun onlyOneExactActiveGttCanBeRecovered() {
        val expected = AppProtectionGttRecoveryPolicy.expectedReference("MHS", positionId, symbol, openedAtMs)
        assertNull(AppProtectionGttRecoveryPolicy.recover(
            positionId, symbol, openedAtMs, quantity,
            listOf(gtt(expected, id = "gtt-1"), gtt(expected, id = "gtt-2"))
        ))
        assertNull(AppProtectionGttRecoveryPolicy.recover(
            positionId, symbol, openedAtMs, quantity, listOf(gtt(expected, status = "TRIGGERED"))
        ))
    }
}
