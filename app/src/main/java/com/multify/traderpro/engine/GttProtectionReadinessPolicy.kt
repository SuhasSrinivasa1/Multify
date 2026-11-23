package com.multify.traderpro.engine

/**
 * An app-owned BUY may be counted as protected only after the broker reports an
 * active, identified SELL smart order. Local stopPrice and trigger intentions
 * are never sufficient. Triggered/completed GTTs require child-fill reconciliation.
 */
object GttProtectionReadinessPolicy {
    /** A missing broker status is not proof of an active server-side stop. */
    fun brokerStatusOrUnverified(rawStatus: String?): String =
        rawStatus?.trim()?.uppercase(java.util.Locale.US)?.takeIf { it.isNotEmpty() }
            ?: "UNVERIFIED"

    fun verified(mode: String, orderId: String?, status: String?): Boolean =
        HoldingSmartOrderPolicy.isServerProtected(mode, orderId) &&
            mode in setOf(HoldingSmartOrderPolicy.MODE_HARD_STOP_GTT,
                HoldingSmartOrderPolicy.MODE_TRAILING_GTT) &&
            brokerStatusOrUnverified(status) in setOf("ACTIVE", "OPEN")
}
