package com.multify.traderpro.engine

/** Endpoint-specific readiness requirements for the Fixed direct-MARKET BUY pipeline. */
object FixedAutoBuyCooldownStagePolicy {
    enum class ApiCategory { LIVE_DATA, NON_TRADING, ORDERS }
    enum class Stage { BEFORE_QUOTE, AFTER_QUOTE_FAILURE, BEFORE_ORDER }

    fun requiredCategories(stage: Stage): Set<ApiCategory> = when (stage) {
        Stage.BEFORE_QUOTE -> setOf(ApiCategory.LIVE_DATA, ApiCategory.NON_TRADING)
        Stage.AFTER_QUOTE_FAILURE -> setOf(ApiCategory.LIVE_DATA)
        // Once a fresh quote exists, unrelated LIVE_DATA cooldown must not veto the order.
        // Ownership proof and the broker's order endpoint remain mandatory.
        Stage.BEFORE_ORDER -> setOf(ApiCategory.NON_TRADING, ApiCategory.ORDERS)
    }
}
