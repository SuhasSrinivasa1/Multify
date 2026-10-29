package com.multify.traderpro.engine

/**
 * Broker holdings are fungible. We never infer application ownership from the presence of
 * a Groww holding, a ticker match, or an imported portfolio position.
 *
 * An app-created opening order AND its matching app reference are both required. Legacy
 * portfolio rows rebased to an aggregate Groww holding are permanently excluded.
 */
object AppPositionOwnershipPolicy {
    private val enginePrefixes = mapOf(
        "MULTIFY_HOLDING" to setOf("MLH"),
        "FORECAST_HOLDING" to setOf("FLH"),
        "FIXED_HOLDING" to setOf("FXH", "FXM")
    )

    fun appOpened(
        engine: String,
        side: String,
        product: String,
        openingOrderId: String,
        openingReference: String,
        strategy: String,
        quantity: Int
    ): Boolean {
        val prefixes = enginePrefixes[engine] ?: return false
        if (side != "LONG" || product != "CNC" || quantity <= 0) return false
        if (openingOrderId.isBlank() || openingOrderId.equals("EXTERNAL_GROWW_HOLDING", true)) return false
        if (strategy.contains("aggregated Groww portfolio", true) ||
            strategy.contains("OWNERSHIP_UNVERIFIED", true)) return false
        // The app's stableRef is a 3-char prefix and 14 lower-case hexadecimal characters.
        if (openingReference.length != 17) return false
        return prefixes.any { prefix ->
            openingReference.startsWith(prefix) && openingReference.drop(3).all { it in '0'..'9' || it in 'a'..'f' }
        }
    }

    fun exclusiveBrokerQuantity(appQuantity: Int, dematQuantity: Int, cncQuantity: Int): Boolean =
        appQuantity > 0 && maxOf(dematQuantity.coerceAtLeast(0), cncQuantity.coerceAtLeast(0)) == appQuantity

    fun isAppProtectionReference(reference: String?): Boolean =
        reference != null && reference.length == 17 &&
            setOf("MHS", "MTG", "MTR").any { prefix ->
                reference.startsWith(prefix) && reference.drop(3).all { it in '0'..'9' || it in 'a'..'f' }
            }
}
