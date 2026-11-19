package com.multify.traderpro.data.logging

import android.content.Context
import com.multify.traderpro.data.network.ForecastDto

/**
 * Fixed screen composition observation. NOT a physically measured pixel paint,
 * notification receipt, quote exchange time or execution timestamp.
 * This is optional user-visible telemetry only; never accessed by trading.
 */
internal object FixedUiRenderEvidenceStore {
    private const val PREF = "fixed_screen_render_observed_v8322"
    fun recordFixedScreenObserved(context: Context, rows: List<ForecastDto>, atMs: Long) {
        if (rows.isEmpty()) return
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val edit = prefs.edit()
        var changed = false
        for (row in rows.take(150)) {
            if (row.generatedAtMs <= 0L) continue
            val key = row.generatedAtMs.toString() + "|" + row.symbol.uppercase()
            if (prefs.contains(key)) continue
            edit.putLong(key, atMs)
            changed = true
        }
        if (changed) edit.apply()
    }

    /**
     * Optional Compose state observation, NOT proof a pixel was painted or that
     * a broker order was sent. Stores only meaningful label/state transitions.
     * Never invoked from scanner or broker execution, and no network requests.
     */
    private const val OPERATOR_PREF = "fixed_operator_transitions_v9019"
    fun recordOperatorStateObserved(
        context: Context, symbol: String, eventScanAtMs: Long,
        recommendationStatus: String, buttonLabel: String, enabled: Boolean,
        marketabilityState: String, atMs: Long
    ) {
        if (eventScanAtMs <= 0L || symbol.isBlank()) return
        val prefs = context.getSharedPreferences(OPERATOR_PREF, Context.MODE_PRIVATE)
        val identity = eventScanAtMs.toString() + "|" + symbol.uppercase()
        val current = listOf(recommendationStatus, buttonLabel, enabled,
            marketabilityState).joinToString("|")
        val lastKey = "last|" + identity
        if (prefs.getString(lastKey, null) == current) return
        val edit = prefs.edit().putString(lastKey, current)
        // A durable finite ring of transition observations; keep 400 newest.
        val historical = prefs.all.keys.filter { it.startsWith("t|") }.sorted()
        for (old in historical.take((historical.size - 399).coerceAtLeast(0))) edit.remove(old)
        val key = "t|" + atMs.toString().padStart(13, '0') + "|" + identity
        edit.putString(key, current).apply()
    }

    fun exportOperatorTransitions(context: Context): Map<String, Any?> {
        val prefs = context.getSharedPreferences(OPERATOR_PREF, Context.MODE_PRIVATE)
        val rows = prefs.all.entries.filter { it.key.startsWith("t|") }
            .sortedBy { it.key }
            .takeLast(400)
            .mapNotNull { entry ->
                val parts = entry.key.split('|')
                val state = (entry.value as? String)?.split('|') ?: return@mapNotNull null
                if (parts.size != 4 || state.size < 4) return@mapNotNull null
                mapOf(
                    "observed_at_ms" to (parts[1].toLongOrNull()),
                    "recommendation_scan_start_at_ms" to (parts[2].toLongOrNull()),
                    "symbol" to parts[3],
                    "recommendation_status" to state[0],
                    "button_label" to state[1],
                    "ui_button_enabled" to state[2].toBoolean(),
                    "marketability_snapshot_status" to state[3]
                )
            }
        return mapOf(
            "source" to "CLIENT_COMPOSE_OPERATOR_STATE_OBSERVATION",
            "not_physical_pixel_render_or_broker_fill" to true,
            "not_authoritative_entry_gate" to true,
            "no_extra_broker_calls" to true,
            "rows" to rows
        )
    }

    fun export(context: Context): Map<String, Any?> {
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val cases = prefs.all.entries.asSequence()
            .mapNotNull { entry ->
                val observed = entry.value as? Long ?: return@mapNotNull null
                val parts = entry.key.split('|')
                if (parts.size != 2) return@mapNotNull null
                val generated = parts[0].toLongOrNull() ?: return@mapNotNull null
                mapOf(
                    "symbol" to parts[1],
                    "recommendation_scan_start_at_ms" to generated,
                    "fixed_screen_composition_observed_at_ms" to observed,
                    "scan_to_screen_observation_ms" to (observed - generated)
                )
            }.sortedByDescending { it["fixed_screen_composition_observed_at_ms"] as? Long ?: 0L }
            .take(300).toList()
        return mapOf(
            "source" to "OPTIONAL_FIXED_SCREEN_COMPOSE_OBSERVATION",
            "not_physical_pixel_render_or_notification_receipt" to true,
            "no_visible_screen_no_sample" to true,
            "used_in_trading" to false,
            "records" to cases
        )
    }
}
