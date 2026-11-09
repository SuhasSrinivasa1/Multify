package com.multify.traderpro.engine

/** Broker-confirmed execution quantity, never the originally requested LIMIT quantity. */
object ExecutionFillQuantityPolicy {
    fun confirmedFilledQuantity(requestedQuantity: Int, reportedFilledQuantity: Int?, terminalStatus: String): Int? {
        if (requestedQuantity <= 0) return null
        if (reportedFilledQuantity != null) {
            return reportedFilledQuantity.takeIf { it in 1..requestedQuantity }
        }
        // Groww's EXECUTED terminal status itself confirms the whole order when
        // older responses omit filled_quantity. A CANCELLED partial needs actual qty.
        return requestedQuantity.takeIf { terminalStatus.equals("EXECUTED", true) }
    }
}
