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
