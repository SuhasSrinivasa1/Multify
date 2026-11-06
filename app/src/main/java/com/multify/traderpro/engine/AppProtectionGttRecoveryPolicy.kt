package com.multify.traderpro.engine

import com.multify.traderpro.data.network.SmartOrderPayload
import java.security.MessageDigest
import java.util.Locale
import kotlin.math.abs

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

    fun expectedReferenceForMode(
        mode: String, positionId: Long, symbol: String, openedAtMs: Long
    ): String? {
        val prefix = prefixModes.entries.firstOrNull { it.value == mode }?.key ?: return null
        return expectedReference(prefix, positionId, symbol, openedAtMs)
    }

    data class TriggeredReferenceResolution(
        val referenceId: String,
        val reconstructed: Boolean,
        val reasonCode: String
    )

    /**
     * Groww may omit reference_id on a triggered GTT parent. Reconstruct only the
     * deterministic reference originally sent by this app, and only after every
     * parent identity field matches the locally managed position and GTT.
     */
    fun resolveTriggeredReference(
        mode: String,
        positionId: Long,
        symbol: String,
        openedAtMs: Long,
        quantity: Int,
        expectedGttId: String,
        expectedTriggerPrice: Double?,
        expectedDirection: String?,
        parent: SmartOrderPayload
    ): TriggeredReferenceResolution? {
        if (quantity <= 0 || expectedGttId.isBlank() || symbol.isBlank()) return null
        val expected = expectedReferenceForMode(mode, positionId, symbol, openedAtMs) ?: return null
        if (!parent.smartOrderId.equals(expectedGttId, true) ||
            !parent.smartOrderType.equals("GTT", true) ||
            parent.status.uppercase(Locale.US) !in setOf("TRIGGERED", "COMPLETED") ||
            !parent.tradingSymbol.equals(symbol, true) ||
            parent.quantity != quantity ||
            !parent.productType.equals("CNC", true) ||
            !parent.exchange.equals("NSE", true) ||
            !parent.order?.transactionType.equals("SELL", true)
        ) return null
        val expectedOrderType = if (mode == HoldingSmartOrderPolicy.MODE_RECOVERY_GTT) "LIMIT" else "MARKET"
        if (!parent.order?.orderType.equals(expectedOrderType, true)) return null
        val direction = parent.triggerDirection?.uppercase(Locale.US) ?: return null
        if (direction !in setOf("UP", "DOWN") ||
            !direction.equals(expectedDirection?.uppercase(Locale.US), true)
        ) return null
        val actualTrigger = parent.triggerPrice?.toDoubleOrNull()
            ?.takeIf { it.isFinite() && it > 0.0 } ?: return null
        if (expectedTriggerPrice == null || !expectedTriggerPrice.isFinite() ||
            expectedTriggerPrice <= 0.0 || abs(actualTrigger - expectedTriggerPrice) > 0.11
        ) return null
        val brokerReference = parent.referenceId?.takeIf { it.isNotBlank() }
        if (brokerReference != null && !brokerReference.equals(expected, true)) return null
        return TriggeredReferenceResolution(
            referenceId = expected,
            reconstructed = brokerReference == null,
            reasonCode = if (brokerReference == null) "REFERENCE_RECONSTRUCTED_FROM_LOCAL_SEED"
                else "EXACT_PARENT_REFERENCE"
        )
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
                order.referenceId != null &&
                order.referenceId in expected.keys
        }
        val order = matches.singleOrNull() ?: return null
        val reference = order.referenceId ?: return null
        val mode = expected[reference] ?: return null
        val trigger = order.triggerPrice?.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0.0 } ?: return null
        val direction = order.triggerDirection?.uppercase(Locale.US)?.takeIf { it == "UP" || it == "DOWN" } ?: return null
        if (mode != HoldingSmartOrderPolicy.MODE_RECOVERY_GTT && direction != "DOWN") return null
        return Recovered(order, mode, trigger, direction)
    }
}
