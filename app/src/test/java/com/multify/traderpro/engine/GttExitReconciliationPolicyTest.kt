package com.multify.traderpro.engine

import com.multify.traderpro.data.network.GrowwOrderBookRow
import org.junit.Assert.*
import org.junit.Test

class GttExitReconciliationPolicyTest {
    private fun sell(qty:Int=32,filled:Int=32,price:Double?=300.28,id:String="S1",status:String="EXECUTED") =
        GrowwOrderBookRow(growwOrderId=id,tradingSymbol="ARTEMISMED",transactionType="SELL",
            product="CNC",quantity=qty,filledQuantity=filled,averageFillPrice=price,orderStatus=status)
    @Test fun triggeredZeroInventoryIsReconciledBeforeOwnershipVeto() {
        assertTrue(GttExitReconciliationPolicy.shouldQuarantinePendingChild("TRAILING_GTT","GTT1","TRIGGERED",0,32))
        assertFalse(GttExitReconciliationPolicy.shouldQuarantinePendingChild("NONE",null,"",0,32))
    }
    @Test fun onlyUniqueRealBrokerFillCanClose() {
        assertNotNull(GttExitReconciliationPolicy.uniqueConfirmedFullSell(listOf(sell()),"ARTEMISMED",32,"BUY"))
        assertNull(GttExitReconciliationPolicy.uniqueConfirmedFullSell(listOf(sell(filled=10)),"ARTEMISMED",32,"BUY"))
        assertNull(GttExitReconciliationPolicy.uniqueConfirmedFullSell(listOf(sell(price=null)),"ARTEMISMED",32,"BUY"))
        assertNull(GttExitReconciliationPolicy.uniqueConfirmedFullSell(listOf(sell(id="A"),sell(id="B")),"ARTEMISMED",32,"BUY"))
        assertEquals(10,GttExitReconciliationPolicy.identifiedPartialFills(listOf(sell(filled=10)),
            "ARTEMISMED",32,"BUY").sumOf{it.filledQuantity})
    }
}
