package com.multify.traderpro.data.repository

import java.util.Locale

/**
 * Conservative company-name resolver. Only anchored, unambiguous name prefixes (or an
 * explicit known NSE ticker in the headline) may map a filing to an orderable symbol.
 * Do not use fuzzy substring matches or unrelated companies in a description.
 */
internal object ForecastNewsSymbolResolutionPolicy {
    fun normalized(s: String): String = s.uppercase(Locale.ROOT)
        .replace(Regex("[^A-Z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()

    private fun stem(s: String): String = normalized(s)
        .replace(Regex("\\b(LIMITED|LTD|PRIVATE|PVT|INCORPORATED|INC|THE)\\b"), " ")
        .replace(Regex("\\s+"), " ").trim()

    fun resolve(title: String, catalog: Map<String, String>): String {
        val normalizedTitle = normalized(title)
        if (normalizedTitle.isEmpty()) return ""
        val header = normalizedTitle.substringBefore(" HAS INFORMED")
            .substringBefore(" INTIMATES ").substringBefore(" INFORMS ")
            .substringBefore(" ANNOUNCES ").substringBefore(" DISCLOSES ").trim()
        val cleaned = stem(header)
        if (cleaned.length < 6) return ""
        val matches = catalog.entries.asSequence().filter { (name, _) ->
            val key = stem(name)
            key.length >= 6 && (cleaned == key || cleaned.startsWith("$key "))
        }.map { it.value }.distinct().take(2).toList()
        return if (matches.size == 1) matches.first() else ""
    }
}
