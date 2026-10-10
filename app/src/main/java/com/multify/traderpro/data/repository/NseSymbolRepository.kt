package com.multify.traderpro.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.Duration
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NseSymbolRepository @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences("nse_symbol_master", Context.MODE_PRIVATE)
    private val client = OkHttpClient.Builder()
        .connectTimeout(Duration.ofSeconds(12))
        .readTimeout(Duration.ofSeconds(20))
        .build()
    @Volatile private var cachedSymbols: Set<String>? = null

    data class Status(val count: Int, val updatedAtMs: Long)

    fun status(): Status = Status(
        count = prefs.getInt(KEY_COUNT, 0),
        updatedAtMs = prefs.getLong(KEY_UPDATED, 0L)
    )

    private fun symbolSet(): Set<String> {
        cachedSymbols?.let { return it }
        val packed = prefs.getString(KEY_SYMBOLS, "").orEmpty()
        val loaded = if (packed.isBlank()) emptySet() else packed.split('|').asSequence().filter { it.isNotBlank() }.toSet()
        cachedSymbols = loaded
        return loaded
    }

    fun symbols(): List<String> = symbolSet().toList()

    fun isKnown(symbol: String): Boolean {
        val symbols = symbolSet()
        if (symbols.isEmpty()) return true
        return symbol.trim().uppercase(Locale.US) in symbols
    }

    fun isStale(maxAgeMs: Long = 25L * 24 * 60 * 60 * 1000): Boolean {
        val updated = prefs.getLong(KEY_UPDATED, 0L)
        return updated == 0L || System.currentTimeMillis() - updated > maxAgeMs
    }

    fun refresh(): Status {
        val request = Request.Builder()
            .url(EQUITY_URL)
            .header("User-Agent", "Mozilla/5.0 MultifyTraderPro/3.1")
            .header("Accept", "text/csv,*/*")
            .build()
        val body = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("NSE symbol sync HTTP ${response.code}")
            response.body?.string() ?: error("NSE symbol sync returned an empty file")
        }
        val symbols = body.lineSequence()
            .drop(1)
            .mapNotNull { line -> csvFirst(line)?.trim()?.uppercase(Locale.US)?.takeIf { it.matches(Regex("[A-Z0-9&._-]{1,32}")) } }
            .distinct()
            .sorted()
            .toList()
        require(symbols.size > 500) { "NSE symbol sync returned only ${symbols.size} symbols" }
        val now = System.currentTimeMillis()
        prefs.edit()
            .putString(KEY_SYMBOLS, symbols.joinToString("|"))
            .putInt(KEY_COUNT, symbols.size)
            .putLong(KEY_UPDATED, now)
            .apply()
        cachedSymbols = symbols.toSet()
        return Status(symbols.size, now)
    }

    private fun csvFirst(line: String): String? {
        if (line.isBlank()) return null
        if (!line.startsWith('"')) return line.substringBefore(',')
        val end = line.indexOf('"', 1)
        return if (end > 1) line.substring(1, end).replace("\"\"", "\"") else null
    }

    companion object {
        const val EQUITY_URL = "https://nsearchives.nseindia.com/content/equities/EQUITY_L.csv"
        private const val KEY_SYMBOLS = "symbols"
        private const val KEY_COUNT = "count"
        private const val KEY_UPDATED = "updated_at"
    }
}
