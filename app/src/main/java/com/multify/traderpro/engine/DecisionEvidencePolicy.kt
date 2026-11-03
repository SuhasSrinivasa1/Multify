package com.multify.traderpro.engine

/**
 * Evidence is not a trading instruction. This policy selects meaningful decisions and
 * their outcomes for local, durable diagnostics. Both winners and failures are kept:
 * filtering for winners alone would introduce survivorship bias.
 *
 * Fixed's frozen recommendation and SELL policies never read this journal.
 */
object DecisionEvidencePolicy {
    private val exact = setOf(
        "FIXED_ENGINE/LONG_SCAN_TIERED",
        "FIXED_OUTCOME/TARGET_HIT",
        "FORECAST/LONG_SCAN_TIERED",
        "FORECAST_RESEARCH/OUTCOMES_BACKFILLED",
        "FORECAST_RESEARCH/SCAN_CAPTURED",
        "FORECAST/CALIBRATION_SESSION_COMPLETED",
        "FORECAST_UNIVEST/OFFICIAL_NOTIFICATION_CAPTURED",
        "FORECAST_UNIVEST/PAIRING_SKIPPED",
        "FORECAST_UNIVEST/NOTIFICATION_IGNORED",
        "FORECAST_UNIVEST/ENTRY_48_FACTOR_SNAPSHOT",
        "FORECAST_UNIVEST/SAME_DAY_EXIT_OUTCOME",
        "FORECAST_UNIVEST/EXIT_MARKET_ESTIMATE_NOT_TRAINED",
        "MULTIFY_RESEARCH/FEATURE_SNAPSHOT",
        "MULTIFY_RESEARCH/UNMATCHED_OFFICIAL_CLOSE",
        "MULTIFY_RESEARCH/OFFICIAL_SAME_DAY_FACTOR_LEARNING",
        "MULTIFY_RESEARCH/FIFTEEN_MINUTE_OUTCOME",
        "FORECAST_LEARNING/MULTIFY_FAST_POSITIVE_CAPTURED",
        "LEARNING/MULTIFY_RETURN_RECORDED",
        "LEARNING/MULTIFY_MFE_RECORDED",
        "EXECUTION/CNC_BUY_SIZED",
        "EXECUTION/SELL_PAUSED_SELLABLE_QUANTITY_MISMATCH",
        "PROTECTION/POST_FILL_GTT_ATTEMPT_FAILED",
        "TRAILING_STATE/STOCK_TRAIL_STATE",
        "GTT/GTT_STATUS",
        "GTT/PROTECTIVE_GTT_RATCHET_FAILED",
        "GTT/PROTECTIVE_GTT_CREATE_FAILED_LOCAL_FALLBACK_ACTIVE",
        "GTT/OVERNIGHT_PROTECTION_PRESERVED",
        "GTT/RECOVERY_GTT_RECREATE",
        "GTT/RECOVERY_TO_TRAIL_MIGRATED",
        "HOLDING/GTT_BROKER_SELL_RECONCILED",
        "SAFETY/UNPROTECTED_CONFIRMED_BUY_CRITICAL",
        "SAFETY/SELL_BLOCKED_UNOWNED_OR_MIXED",
        "SAFETY/DIRECT_SELL_BLOCKED_BY_ACTIVE_OR_TRIGGERED_GTT",
        "SAFETY/NEW_BUY_BLOCKED_UNPROTECTED_HOLDING"
    )
    private val allowedDetailKeys = setOf(
        "symbol", "engine", "source", "source_package", "type", "status", "action",
        "reason", "strategy", "regime", "market_regime", "quality_tier",
        "event_id", "buy_event_id", "sell_event_id", "gtt_id", "order_id",
        "entry", "entry_price", "entry_price_source", "exit", "exit_price",
        "exit_price_source", "observed_price", "last_price", "price", "ltp",
        "target", "target_price", "target_pct", "stop", "stop_price", "old_trigger",
        "new_trigger", "desired_trigger", "trigger_price", "high_water",
        "quantity", "qty", "pnl", "net_pnl", "gross_pnl", "net_profit",
        "reported_return_pct", "return_pct", "realized_pct", "mfe_pct",
        "max_favourable_pct", "feature_count", "observed_factor_count",
        "features", "votes", "factor_json", "family_scores",
        "confidence", "score", "total", "count", "date", "call_date",
        "opened_at_ms", "closed_at_ms", "buy_at_ms", "sell_at_ms",
        "entry_at_ms", "exit_at_ms", "capture_at_ms",
        "posted_ms", "received_ms", "same_day_pair", "training_eligible",
        "positive_cases", "cases_with_entry_factors", "all_official_positive_closes",
        "published_count", "published", "primary", "secondary",
        "research", "evaluated", "target_hit", "newly_publishable_count",
        "retry_after_ms", "desired_mode", "smart_order_mode", "high_water_pct",
        "current_server_stop", "current_protected", "desired_protected",
        "symbol_count", "tracked_holdings", "app_qty", "broker_qty",
        "app_owned_holdings", "protective_sell_priority_preserved",
        "reconstruction_quality", "market_price", "quote_latency_ms"
    )

    fun shouldCapture(category: String, event: String): Boolean {
        val key = category.uppercase() + "/" + event.uppercase()
        if (key in exact) return true
        return when (category.uppercase()) {
            "FIXED_AUTO_BUY", "FORECAST_AUTO_BUY" -> event.contains("FILLED", true) ||
                event.contains("BLOCKED", true) || event.contains("SKIPPED", true) ||
                event.contains("GTT_", true)
            "EXECUTION" -> event.contains("BUY", true) || event.contains("SELL", true)
            "GTT" -> event.contains("CREATED", true) || event.contains("MODIFIED", true) ||
                event.contains("RATCHETED", true) || event.contains("RECONCILED", true)
            else -> false
        }
    }

    fun allowedDetailKey(key: String): Boolean = key.lowercase() in allowedDetailKeys

    fun classification(category: String, event: String): String = when {
        event.contains("FAIL", true) || event.contains("BLOCK", true) ||
            event.contains("SKIPPED", true) || event.contains("IGNORED", true) -> "REJECTED_OR_ERROR"
        category.contains("RESEARCH", true) || category.contains("UNIVEST", true) ||
            category.contains("LEARNING", true) -> "RESEARCH_EVIDENCE"
        event.contains("OUTCOME", true) || event.contains("RETURN", true) ||
            event.contains("RECONCILED", true) -> "OUTCOME"
        else -> "DECISION"
    }
}
