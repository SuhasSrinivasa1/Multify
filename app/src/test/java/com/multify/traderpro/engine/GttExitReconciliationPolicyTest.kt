package com.multify.traderpro.engine

import com.multify.traderpro.data.network.GrowwOrderBookRow
import org.junit.Assert.*
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Test

class GttExitReconciliationPolicyTest {
    private fun sell(
        qty: Int = 32, filled: Int = 32, price: Double? = 300.28,
        id: String = "S1", status: String = "EXECUTED", reference: String = "MHS-APP-REF"
    ) = GrowwOrderBookRow(
        growwOrderId = id, tradingSymbol = "ARTEMISMED", transactionType = "SELL",
        product = "CNC", quantity = qty, filledQuantity = filled,
        averageFillPrice = price, orderStatus = status, orderReferenceId = reference
    )

    private val openedAt = LocalDateTime.parse("2026-11-09T09:22:54")
        .atZone(ZoneId.of("Asia/Kolkata")).toInstant().toEpochMilli()
    private val nowAt = LocalDateTime.parse("2026-11-09T09:28:05")
        .atZone(ZoneId.of("Asia/Kolkata")).toInstant().toEpochMilli()

    private fun resolve(
        orders: List<GrowwOrderBookRow>, brokerQuantity: Int = 0,
        triggeredAt: String? = "2026-11-09T09:27:20+05:30",
        parentReference: String? = null
    ) = GttExitReconciliationPolicy.resolveConfirmedTriggeredExit(
        orders, "ARTEMISMED", 32, "BUY", 1L, openedAt,
        parentReference, triggeredAt, brokerQuantity, nowAt
    )

    @Test fun missingParentReferenceCanReconcileExactAppCreatedGttReference() {
        val refs = GttExitReconciliationPolicy.knownAppProtectionReferences(1, "ARTEMISMED", openedAt)
        assertEquals(3, refs.size)
        val full = sell(reference = refs.first(), id = "S-MATCH")
        assertEquals("EXACT_APP_GTT_REFERENCE", resolve(listOf(full), triggeredAt = null)?.evidence)
    }

    @Test fun missingParentReferenceCanReconcileUniqueTimeLinkedFullBrokerSell() {
        val child = sell(reference = "CHILD-ORDER", id = "S-CHILD")
            .copy(createdAt = "2026-11-09T09:27:23")
        val found = resolve(listOf(child))
        assertEquals("UNIQUE_EXECUTED_SELL_AFTER_GTT_TRIGGER_BROKER_FLAT", found?.evidence)
        assertEquals("S-CHILD", found?.order?.growwOrderId)
    }

    @Test fun triggerAloneOrAmbiguousOrPartialSellNeverClosesPosition() {
        val good = sell(reference = "BROKER-CHILD", id = "S-1")
            .copy(createdAt = "2026-11-09T09:27:23")
        val other = sell(reference = "MANUAL-ORDER", id = "S-2")
            .copy(createdAt = "2026-11-09T09:27:24")
        assertNull(resolve(emptyList()))
        assertNull(resolve(listOf(good), brokerQuantity = 32))
        assertNull(resolve(listOf(good.copy(filledQuantity = 8))))
        assertNull(resolve(listOf(good.copy(orderStatus = "OPEN"))))
        assertNull(resolve(listOf(good.copy(averageFillPrice = null))))
        assertNull(resolve(listOf(good.copy(createdAt = ""))))
        assertNull(resolve(listOf(good.copy(createdAt = "2026-11-09T09:24:00"))))
        assertNull(resolve(listOf(good, other)))
        assertNull(resolve(listOf(good), triggeredAt = null))
        assertNull(resolve(listOf(good), parentReference = "MHSwrong"))
    }

    @Test fun anotherAppsGttReferenceCannotBeMisattributed() {
        val otherAppRef = "MHS" + "a".repeat(14)
        val child = sell(reference = otherAppRef, id = "S-OTHER")
            .copy(createdAt = "2026-11-09T09:27:23")
        assertNull(resolve(listOf(child)))
    }

    @Test fun triggeredZeroInventoryIsReconciledBeforeOwnershipVeto() {
        assertTrue(GttExitReconciliationPolicy.shouldQuarantinePendingChild("TRAILING_GTT", "GTT1", "TRIGGERED", 0, 32))
        assertFalse(GttExitReconciliationPolicy.shouldQuarantinePendingChild("NONE", null, "", 0, 32))
    }

    @Test fun onlyExactGttReferenceAndUniqueRealBrokerFillCanClose() {
        assertNotNull(GttExitReconciliationPolicy.uniqueConfirmedFullSell(
            listOf(sell()), "ARTEMISMED", 32, "BUY", "MHS-APP-REF"
        ))
        assertNull(GttExitReconciliationPolicy.uniqueConfirmedFullSell(
            listOf(sell(reference = "MANUAL-SELL")), "ARTEMISMED", 32, "BUY", "MHS-APP-REF"
        ))
        assertNull(GttExitReconciliationPolicy.uniqueConfirmedFullSell(
            listOf(sell(filled = 10)), "ARTEMISMED", 32, "BUY", "MHS-APP-REF"
        ))
        assertNull(GttExitReconciliationPolicy.uniqueConfirmedFullSell(
            listOf(sell(price = null)), "ARTEMISMED", 32, "BUY", "MHS-APP-REF"
        ))
        assertNull(GttExitReconciliationPolicy.uniqueConfirmedFullSell(
            listOf(sell(id = "A"), sell(id = "B")), "ARTEMISMED", 32, "BUY", "MHS-APP-REF"
        ))
        assertEquals(10, GttExitReconciliationPolicy.identifiedPartialFills(
            listOf(sell(filled = 10)), "ARTEMISMED", 32, "BUY", "MHS-APP-REF"
        ).sumOf { it.filledQuantity })
    }
}
