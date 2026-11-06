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

    @Test fun triggeredParentWithoutReferenceReconstructsOnlyAfterExactIdentityMatch() {
        val expected = AppProtectionGttRecoveryPolicy.expectedReference("MHS", 1L, "ENTERO", 1_793_945_957_436L)
        assertEquals("MHS85a6bec864cb38", expected)
        val parent = SmartOrderPayload(
            smartOrderId = "GTT_1793945959629_DFE6759BCF90", smartOrderType = "GTT",
            referenceId = null, status = "TRIGGERED", tradingSymbol = "ENTERO",
            quantity = 15, productType = "CNC", exchange = "NSE", triggerPrice = "1865.45",
            triggerDirection = "DOWN", order = GttOrder(orderType = "MARKET", transactionType = "SELL")
        )
        val resolved = AppProtectionGttRecoveryPolicy.resolveTriggeredReference(
            mode = HoldingSmartOrderPolicy.MODE_HARD_STOP_GTT, positionId = 1L, symbol = "ENTERO",
            openedAtMs = 1_793_945_957_436L, quantity = 15,
            expectedGttId = "GTT_1793945959629_DFE6759BCF90", expectedTriggerPrice = 1865.45,
            expectedDirection = "DOWN", parent = parent
        )
        assertNotNull(resolved)
        assertEquals(expected, resolved!!.referenceId)
        assertTrue(resolved.reconstructed)
        assertEquals("REFERENCE_RECONSTRUCTED_FROM_LOCAL_SEED", resolved.reasonCode)
    }

    @Test fun triggeredReferenceFallbackRejectsAnyParentIdentityMismatch() {
        val parent = gtt(null, status = "TRIGGERED")
        fun resolve(payload: SmartOrderPayload) = AppProtectionGttRecoveryPolicy.resolveTriggeredReference(
            HoldingSmartOrderPolicy.MODE_HARD_STOP_GTT, positionId, symbol, openedAtMs,
            quantity, "gtt-1", 386.15, "DOWN", payload
        )
        assertNotNull(resolve(parent))
        assertNull(resolve(parent.copy(smartOrderId = "other-gtt")))
        assertNull(resolve(parent.copy(tradingSymbol = "OTHER")))
        assertNull(resolve(parent.copy(quantity = quantity + 1)))
        assertNull(resolve(parent.copy(productType = "MIS")))
        assertNull(resolve(parent.copy(exchange = "BSE")))
        assertNull(resolve(parent.copy(triggerDirection = "UP")))
        assertNull(resolve(parent.copy(triggerPrice = "380.00")))
        assertNull(resolve(parent.copy(referenceId = "manual-reference")))
        assertNull(resolve(parent.copy(order = GttOrder(orderType = "MARKET", transactionType = "BUY"))))
        assertNull(resolve(parent.copy(status = "ACTIVE")))
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
