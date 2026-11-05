package com.multify.traderpro.engine

import com.multify.traderpro.data.network.SmartOrderPayload
import java.security.MessageDigest
import java.util.Locale

/**
 * Recovers only the exact GTT reference generated for a managed position.
 * Symbol/quantity matches alone are deliberately insufficient.
 */
object AppProtectionGttRecoveryPolicy {
    data class Recovered(
        val payload: SmartOrderPayload,
        val mode: String,
        val triggerPrice: Double,
        val direction: String
    )

    private val prefixModes = mapOf(
        "MHS" to HoldingSmartOrderPolicy.MODE_HARD_STOP_GTT,
        "MTG" to HoldingSmartOrderPolicy.MODE_TRAILING_GTT,
        "MTR" to HoldingSmartOrderPolicy.MODE_RECOVERY_GTT
    )

    fun expectedReference(prefix: String, positionId: Long, symbol: String, openedAtMs: Long): String? {
        if (prefix !in prefixModes || positionId <= 0L || symbol.isBlank() || openedAtMs <= 0L) return null
        val seed = "$positionId-$symbol-$openedAtMs"
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(seed.toByteArray())
            .joinToString("") { "%02x".format(it) }
        return prefix + digest.take(14)
    }

    fun recover(
        positionId: Long,
        symbol: String,
        openedAtMs: Long,
        quantity: Int,
        activeOrders: List<SmartOrderPayload>
    ): Recovered? {
        if (positionId <= 0L || symbol.isBlank() || openedAtMs <= 0L || quantity <= 0) return null
        val expected = prefixModes.keys.mapNotNull { prefix ->
            expectedReference(prefix, positionId, symbol, openedAtMs)?.let { it to prefixModes.getValue(prefix) }
        }.toMap()
        val matches = activeOrders.filter { order ->
            order.smartOrderId.isNotBlank() &&
                order.tradingSymbol.equals(symbol, ignoreCase = true) &&
                order.quantity == quantity &&
                order.productType.equals("CNC", ignoreCase = true) &&
                order.status.uppercase(Locale.US) in setOf("ACTIVE", "OPEN") &&
                order.order?.transactionType.equals("SELL", ignoreCase = true) &&
                order.referenceId in expected.keys
        }
        val order = matches.singleOrNull() ?: return null
        val mode = expected[order.referenceId] ?: return null
        val trigger = order.triggerPrice?.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0.0 } ?: return null
        val direction = order.triggerDirection?.uppercase(Locale.US)?.takeIf { it == "UP" || it == "DOWN" } ?: return null
        if (mode != HoldingSmartOrderPolicy.MODE_RECOVERY_GTT && direction != "DOWN") return null
        return Recovered(order, mode, trigger, direction)
    }
}
