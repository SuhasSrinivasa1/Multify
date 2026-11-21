package com.multify.traderpro.engine

import java.util.Locale

/**
 * Read-only guard against repeatedly processing an orphaned local position as if it
 * still had executable broker protection.
 *
 * The monitor must have a successfully retrieved, complete Groww inventory AND
 * active-GTT list before using this verdict. A 0-share view alone is never an
 * instruction to delete a position, record realized P&L or place another SELL.
 *
 * Triggered/completed GTTs are deliberately excluded: those need exact child
 * SELL reconciliation, not an orphan quarantine.
 */
object BrokerFlatTerminalGttQuarantinePolicy {
    private val noChildEvidenceStatuses =
        setOf("CANCELLED", "CANCELED", "EXPIRED", "REJECTED", "FAILED")

    fun shouldQuarantine(
        brokerQuantity: Int,
        localQuantity: Int,
        smartOrderId: String?,
        smartOrderStatus: String?,
        activeGttIdsForSymbol: Set<String>
    ): Boolean {
        val id = smartOrderId?.trim().orEmpty()
        if (brokerQuantity != 0 || localQuantity <= 0 || id.isEmpty()) return false
        if (id in activeGttIdsForSymbol) return false
        return smartOrderStatus?.trim()?.uppercase(Locale.US) in noChildEvidenceStatuses
    }
}
