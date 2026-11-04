package com.multify.traderpro.engine

import java.util.Locale

/** Exact publication-event identity. Symbol-only keys are legacy state and
 * may not suppress a later independently confirmed same-symbol event.
 */
object FixedNotificationEventKeyPolicy {
    fun key(symbol: String, scanStartedAtMs: Long): String =
        "${symbol.trim().uppercase(Locale.US)}|${scanStartedAtMs}"

    /** Migrate a v8.0.5 single-symbol marker to the earliest known event. */
    fun migrateLegacy(keys: Set<String>, events: List<Pair<String, Long>>): Set<String> {
        val out = keys.filter { it.contains("|") }.toMutableSet()
        for (legacy in keys.filterNot { it.contains("|") }) {
            val first = events.filter { it.first.equals(legacy, true) }
                .minByOrNull { it.second }
            if (first != null) out += key(first.first, first.second)
        }
        return out
    }
}
