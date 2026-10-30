package com.multify.traderpro.engine

import com.multify.traderpro.data.network.GrowwOrderBookRow

/** Pure read-only broker-exit matching. Requires the exact app GTT reference; no symbol/qty guess. */
object GttExitReconciliationPolicy {
    private val terminal = setOf("EXECUTED", "COMPLETED")

    fun shouldQuarantinePendingChild(
        mode: String, gttId: String?, currentStatus: String,
        brokerQuantity: Int, trackedQuantity: Int
    ): Boolean =
        !gttId.isNullOrBlank() && mode in setOf("TRAILING_GTT", "HARD_STOP_GTT") &&
            (currentStatus.uppercase() in setOf("TRIGGERED", "COMPLETED") || brokerQuantity != trackedQuantity)

    fun confirmedFullSells(
        orders: List<GrowwOrderBookRow>,
        symbol: String,
        quantity: Int,
        openingOrderId: String,
        expectedGttReferenceId: String
    ): List<GrowwOrderBookRow> {
        if (expectedGttReferenceId.isBlank()) return emptyList()
        return orders.filter {
            it.tradingSymbol.equals(symbol, true) &&
                it.transactionType.equals("SELL", true) &&
                it.product.equals("CNC", true) &&
                it.quantity == quantity &&
                it.filledQuantity == quantity &&
                it.orderStatus.uppercase() in terminal &&
                it.growwOrderId.isNotBlank() &&
                !it.growwOrderId.equals(openingOrderId, true) &&
                it.orderReferenceId.equals(expectedGttReferenceId, true) &&
                (it.averageFillPrice ?: 0.0).let { price -> price.isFinite() && price > 0.0 }
        }.distinctBy { it.growwOrderId }
    }

    fun uniqueConfirmedFullSell(
        orders: List<GrowwOrderBookRow>,
        symbol: String,
        quantity: Int,
        openingOrderId: String,
        expectedGttReferenceId: String
    ): GrowwOrderBookRow? =
        confirmedFullSells(orders, symbol, quantity, openingOrderId, expectedGttReferenceId).singleOrNull()

    fun identifiedPartialFills(
        orders: List<GrowwOrderBookRow>,
        symbol: String,
        quantity: Int,
        openingOrderId: String,
        expectedGttReferenceId: String
    ): List<GrowwOrderBookRow> {
        if (expectedGttReferenceId.isBlank()) return emptyList()
        return orders.filter {
            it.tradingSymbol.equals(symbol, true) &&
                it.transactionType.equals("SELL", true) &&
                it.product.equals("CNC", true) &&
                it.filledQuantity in 1 until quantity &&
                it.growwOrderId.isNotBlank() &&
                !it.growwOrderId.equals(openingOrderId, true) &&
                it.orderReferenceId.equals(expectedGttReferenceId, true)
        }.distinctBy { it.growwOrderId }
    }
}
