package com.multify.traderpro.data.repository

data class AmfiMarketCapLabel(
    val category: String,
    val rank: Int
)

data class AmfiMarketCapDataset(
    val period: String,
    val source: String,
    val labels: Map<String, AmfiMarketCapLabel>
) {
    companion object {
        val EMPTY = AmfiMarketCapDataset("", "", emptyMap())
    }
}

/**
 * Parses the deterministic CSV generated from AMFI's official semi-annual
 * average-market-cap workbook. Categories are accepted only when rank bands
 * agree with the SEBI/AMFI definitions; unmatched symbols are never estimated.
 */
object AmfiMarketCapClassificationParser {
    private val symbolPattern = Regex("[A-Z0-9&._-]{1,32}")

    fun parse(csv: String): AmfiMarketCapDataset {
        val lines = csv.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        val period = lines.firstOrNull { it.startsWith("#period=") }
            ?.substringAfter("=", "")?.trim().orEmpty()
        val source = lines.firstOrNull { it.startsWith("#source=") }
            ?.substringAfter("=", "")?.trim().orEmpty()
        val records = lines.filterNot { it.startsWith("#") }
        val header = records.firstOrNull()?.split(",") ?: return AmfiMarketCapDataset(period, source, emptyMap())
        val symbolIndex = header.indexOf("symbol")
        val categoryIndex = header.indexOf("category")
        val rankIndex = header.indexOf("rank")
        if (symbolIndex < 0 || categoryIndex < 0 || rankIndex < 0) {
            return AmfiMarketCapDataset(period, source, emptyMap())
        }

        val labels = linkedMapOf<String, AmfiMarketCapLabel>()
        records.drop(1).forEach { line ->
            val cells = line.split(",")
            if (cells.size <= maxOf(symbolIndex, categoryIndex, rankIndex)) return@forEach
            val symbol = cells[symbolIndex].trim().uppercase()
            val category = cells[categoryIndex].trim().uppercase()
            val rank = cells[rankIndex].trim().toIntOrNull() ?: return@forEach
            if (!symbolPattern.matches(symbol) || rank <= 0) return@forEach
            val expected = when {
                rank <= 100 -> "LARGE_CAP"
                rank <= 250 -> "MID_CAP"
                else -> "SMALL_CAP"
            }
            if (category != expected) return@forEach
            labels.putIfAbsent(symbol, AmfiMarketCapLabel(category, rank))
        }
        return AmfiMarketCapDataset(period, source, labels)
    }
}
