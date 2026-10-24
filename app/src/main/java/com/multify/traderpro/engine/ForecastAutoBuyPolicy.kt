package com.multify.traderpro.engine

object ForecastAutoBuyPolicy {
    const val BROKER_KIND_GTT = "GTT"
    const val BROKER_KIND_LEGACY_LIMIT = "LIMIT_ORDER"
    const val SMART_ORDER_TYPE = "GTT"
    const val TRIGGER_DIRECTION = "UP"
    const val CHILD_ORDER_TYPE = "MARKET"

    fun isCompleted(status: String): Boolean =
        status.equals("COMPLETED", ignoreCase = true)

    fun isTerminal(status: String): Boolean =
        status.uppercase() in setOf("COMPLETED", "CANCELLED", "EXPIRED", "FAILED", "REJECTED", "EXECUTED", "DELIVERY_AWAITED")

    fun canCancelBeforeTrigger(status: String): Boolean =
        status.equals("ACTIVE", ignoreCase = true)

    fun shouldCancel(
        stateDate: String,
        today: String,
        autoBuyEnabled: Boolean,
        safetyHalt: Boolean,
        forecastActive: Boolean,
        executionEligible: Boolean,
        forceCancel: Boolean
    ): Boolean =
        forceCancel ||
            stateDate != today ||
            !autoBuyEnabled ||
            safetyHalt ||
            !forecastActive ||
            !executionEligible
}
