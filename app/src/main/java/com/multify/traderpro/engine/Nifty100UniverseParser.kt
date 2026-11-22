package com.multify.traderpro.engine

import java.io.Reader
import java.util.Locale

data class Nifty100Constituent(
    val symbol: String,
    val companyName: String,
    val sector: String
)

/** Parses NSE Indices' published Nifty 100 constituent CSV; no inferred sector labels. */
object Nifty100UniverseParser {
    fun parse(reader: Reader): List<Nifty100Constituent> = reader.buffered().use { source ->
        val header = NseInstrumentMasterParser.csvFields(
            source.readLine() ?: error("Empty Nifty 100 constituent CSV")
        ).map { it.trim().removePrefix("\uFEFF").lowercase(Locale.US).replace(" ", "_") }
        fun index(vararg names: String) = header.indexOfFirst { it in names }
        val symbolAt = index("symbol", "trading_symbol")
        val nameAt = index("company_name", "company_name_")
        val sectorAt = index("industry", "sector", "basic_industry")
        val seriesAt = index("series")
        require(symbolAt >= 0 && sectorAt >= 0) {
            "Nifty 100 CSV missing Symbol or Industry/Sector column"
        }
        val result = linkedMapOf<String, Nifty100Constituent>()
        var rows = 0
        for (line in source.lineSequence()) {
            if (line.isBlank()) continue
            require(++rows <= 500) { "Nifty 100 CSV row limit exceeded" }
            val fields = NseInstrumentMasterParser.csvFields(line)
            if (seriesAt >= 0 && !fields.getOrNull(seriesAt).orEmpty().trim().equals("EQ", true)) continue
            val symbol = NseInstrumentMasterParser.validSymbol(fields.getOrNull(symbolAt).orEmpty()) ?: continue
            val name = fields.getOrNull(nameAt).orEmpty().trim().replace(Regex("\\s+"), " ")
                .take(140).ifBlank { symbol }
            val sector = fields.getOrNull(sectorAt).orEmpty().trim().replace(Regex("\\s+"), " ")
                .take(100).ifBlank { "UNCLASSIFIED" }
            result[symbol] = Nifty100Constituent(symbol, name, sector)
        }
        require(result.size in 80..150) { "Nifty 100 CSV yielded unexpected constituent count ${result.size}" }
        result.values.sortedBy { it.symbol }
    }
}
