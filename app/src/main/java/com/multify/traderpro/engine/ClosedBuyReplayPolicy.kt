package com.multify.traderpro.engine

/**
 * A broker-confirmed SELL which closed the same original BUY order predates
 * this local OPEN row. Together with broker-flat inventory and no active
 * same-symbol GTT this proves a replayed order, not a second holding.
 * No new SELL or speculative P&L entry is permitted by this policy.
 */
object ClosedBuyReplayPolicy {
    data class ConfirmedClosedBuy(
        val symbol: String, val quantity: Int, val openingOrderId: String,
        val closedAtMs: Long, val brokerFillConfirmed: Boolean
    )
    fun isProvenReplay(
        symbol: String, quantity: Int, openingOrderId: String,
        openedAtMs: Long, brokerQuantity: Int, anyActiveGtt: Boolean,
        closedBuys: List<ConfirmedClosedBuy>
    ): Boolean {
        if (symbol.isBlank() || quantity <= 0 || openingOrderId.isBlank() ||
            openedAtMs <= 0L || brokerQuantity != 0 || anyActiveGtt) return false
        return closedBuys.any {
            it.brokerFillConfirmed && it.symbol.equals(symbol, true) &&
                it.quantity == quantity && it.openingOrderId.equals(openingOrderId, true) &&
                it.closedAtMs in 1L until openedAtMs
        }
    }
}
