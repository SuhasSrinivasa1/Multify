package com.multify.traderpro.data.repository

import android.content.Context
import com.google.gson.GsonBuilder
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** On a reinstall, previously exported same-day Fixed recommendations must not
 * be re-sent or traded as "new" signals. Never restores orders or holding state.
 * Only symbols explicitly imported from a prior export are blocked here.
 */
internal data class FixedImportedRecommendationKey(
    val date: String, val symbol: String,
    val generatedAtMs: Long = 0L,
    val origin: String = "PREVIOUS_INSTALL_LEGACY_FIXED_EXPORT",
    val allowedToTrade: Boolean = false
)

@Singleton
class FixedImportedSameDayDedupJournal @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("fixed_imported_same_day_dedup_v805", Context.MODE_PRIVATE)
    private val gson = GsonBuilder().create()
    private val lock = Any()

    private fun read(): List<FixedImportedRecommendationKey> = runCatching {
        gson.fromJson(prefs.getString("keys", "[]"), Array<FixedImportedRecommendationKey>::class.java)
            ?.toList().orEmpty().filter(::valid)
    }.getOrDefault(emptyList())

    private fun valid(row: FixedImportedRecommendationKey): Boolean =
        runCatching { LocalDate.parse(row.date) }.isSuccess &&
        row.symbol.matches(Regex("[A-Z0-9&._-]{1,32}")) &&
        row.generatedAtMs > 0L && !row.allowedToTrade

    private fun write(keys: Collection<FixedImportedRecommendationKey>) {
        val dates = keys.map { it.date }.distinct().sortedDescending().take(5).toSet()
        prefs.edit().putString("keys", gson.toJson(keys.filter { it.date in dates }
            .distinctBy { "${it.date}|${it.symbol}|${it.generatedAtMs}" }.take(1200))).commit()
    }

    fun contains(date: String, symbol: String, eventAtMs: Long): Boolean = synchronized(lock) {
        // An archived prior event is not evidence that a later independent
        // PRIMARY event must be blocked.
        read().any { it.date == date && it.symbol.equals(symbol, true) &&
            it.generatedAtMs == eventAtMs }
    }

    internal fun markRestoredFromLegacy(rows: List<FixedRecommendationRecord>, nowDate: String): Int = synchronized(lock) {
        val old = read()
        val indexed = old.associateBy { "${it.date}|${it.symbol}|${it.generatedAtMs}" }.toMutableMap()
        var added = 0
        for (r in rows.take(2500)) {
            val key = FixedImportedRecommendationKey(r.recommendationDate,
                r.symbol.uppercase(Locale.US), generatedAtMs = r.generatedAtMs)
            if (key.date != nowDate || !valid(key) ||
                !r.entryPrice.isFinite() || r.entryPrice <= 0.0 ||
                r.generatedAtMs <= 0L) continue
            val id = "${key.date}|${key.symbol}|${key.generatedAtMs}"
            if (id !in indexed) { indexed[id] = key; added++ }
        }
        if (added > 0) write(indexed.values)
        added
    }

    fun archive(): String = synchronized(lock) { gson.toJson(read()) }

    fun restore(json: String): Int = synchronized(lock) {
        if (json.length > 180_000) return@synchronized 0
        val incoming = runCatching {
            gson.fromJson(json, Array<FixedImportedRecommendationKey>::class.java)?.toList().orEmpty()
        }.getOrDefault(emptyList()).take(1200)
        val existing = read().associateBy { "${it.date}|${it.symbol}|${it.generatedAtMs}" }.toMutableMap()
        var added = 0
        for (r in incoming.filter(::valid)) {
            val key = "${r.date}|${r.symbol}|${r.generatedAtMs}"
            if (key !in existing) { existing[key] = r; added++ }
        }
        if (added > 0) write(existing.values)
        added
    }
}
