package com.multify.traderpro.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

internal data class FixedPersistedMarketDiscoveryState(
    val date: String,
    val sessionOpenLtps: Map<String, Double>,
    val previousLtps: Map<String, Double>,
    val updatedAtMs: Long
)

internal data class FixedPersistedMarketDiscoveryHealth(
    val date: String,
    val coverageRatio: Double,
    val batchErrors: Int,
    val abortedForNetwork: Boolean,
    val baselineReady: Boolean,
    val updatedAtMs: Long
)

internal object FixedMarketDiscoveryStateCodec {
    fun encode(values: Map<String, Double>): String = values.entries.asSequence()
        .filter { it.key.isNotBlank() && it.value.isFinite() && it.value > 0.0 }
        .sortedBy { it.key }
        .joinToString("|") { "${it.key}~${it.value}" }

    fun decode(packed: String): Map<String, Double> {
        if (packed.isBlank()) return emptyMap()
        return buildMap {
            packed.split('|').forEach { part ->
                val separator = part.lastIndexOf('~')
                if (separator <= 0 || separator >= part.lastIndex) return@forEach
                val symbol = part.substring(0, separator)
                val value = part.substring(separator + 1).toDoubleOrNull()
                if (symbol.isNotBlank() && value != null && value.isFinite() && value > 0.0) put(symbol, value)
            }
        }
    }
}

/** Frozen copy of the v7.4.1 discovery-state persistence semantics. */
@Singleton
class FixedMarketDiscoveryStateStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences("fixed_market_discovery_state_v75", Context.MODE_PRIVATE)

    internal fun load(date: String): FixedPersistedMarketDiscoveryState? {
        val storedDate = prefs.getString(KEY_DATE, null) ?: return null
        if (storedDate != date) {
            clear()
            return null
        }
        val previous = FixedMarketDiscoveryStateCodec.decode(prefs.getString(KEY_PREVIOUS, "").orEmpty())
        val sessionOpen = FixedMarketDiscoveryStateCodec.decode(prefs.getString(KEY_SESSION_OPEN, "").orEmpty())
        if (previous.isEmpty()) return null
        return FixedPersistedMarketDiscoveryState(
            date = storedDate,
            sessionOpenLtps = sessionOpen,
            previousLtps = previous,
            updatedAtMs = prefs.getLong(KEY_UPDATED_AT, 0L)
        )
    }

    internal fun seedIfEmpty(date: String, source: PersistedMarketDiscoveryState?) {
        if (load(date) != null || source == null || source.date != date) return
        save(date, source.sessionOpenLtps, source.previousLtps)
    }

    internal fun save(date: String, sessionOpenLtps: Map<String, Double>, previousLtps: Map<String, Double>) {
        prefs.edit()
            .putString(KEY_DATE, date)
            .putString(KEY_SESSION_OPEN, FixedMarketDiscoveryStateCodec.encode(sessionOpenLtps))
            .putString(KEY_PREVIOUS, FixedMarketDiscoveryStateCodec.encode(previousLtps))
            .putLong(KEY_UPDATED_AT, System.currentTimeMillis())
            .commit()
    }

    internal fun recordHealth(
        date: String,
        coverageRatio: Double,
        batchErrors: Int,
        abortedForNetwork: Boolean,
        baselineReady: Boolean
    ) {
        prefs.edit()
            .putString(KEY_HEALTH_DATE, date)
            .putString(KEY_HEALTH_COVERAGE, coverageRatio.coerceIn(0.0, 1.0).toString())
            .putInt(KEY_HEALTH_BATCH_ERRORS, batchErrors.coerceAtLeast(0))
            .putBoolean(KEY_HEALTH_ABORTED, abortedForNetwork)
            .putBoolean(KEY_HEALTH_BASELINE_READY, baselineReady)
            .putLong(KEY_HEALTH_UPDATED_AT, System.currentTimeMillis())
            .commit()
    }

    internal fun loadHealth(date: String): FixedPersistedMarketDiscoveryHealth? {
        if (prefs.getString(KEY_HEALTH_DATE, null) != date) return null
        val updated = prefs.getLong(KEY_HEALTH_UPDATED_AT, 0L)
        if (updated <= 0L) return null
        return FixedPersistedMarketDiscoveryHealth(
            date = date,
            coverageRatio = prefs.getString(KEY_HEALTH_COVERAGE, "0")?.toDoubleOrNull()?.coerceIn(0.0, 1.0) ?: 0.0,
            batchErrors = prefs.getInt(KEY_HEALTH_BATCH_ERRORS, 0),
            abortedForNetwork = prefs.getBoolean(KEY_HEALTH_ABORTED, false),
            baselineReady = prefs.getBoolean(KEY_HEALTH_BASELINE_READY, false),
            updatedAtMs = updated
        )
    }

    internal fun clear() { prefs.edit().clear().commit() }

    companion object {
        private const val KEY_DATE = "date"
        private const val KEY_SESSION_OPEN = "session_open"
        private const val KEY_PREVIOUS = "previous"
        private const val KEY_UPDATED_AT = "updated_at"
        private const val KEY_HEALTH_DATE = "health_date"
        private const val KEY_HEALTH_COVERAGE = "health_coverage"
        private const val KEY_HEALTH_BATCH_ERRORS = "health_batch_errors"
        private const val KEY_HEALTH_ABORTED = "health_aborted"
        private const val KEY_HEALTH_BASELINE_READY = "health_baseline_ready"
        private const val KEY_HEALTH_UPDATED_AT = "health_updated_at"
    }
}
