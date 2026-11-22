package com.multify.traderpro.engine

import java.io.Reader
import java.time.LocalDate
import java.util.Locale

/** Strict parser for NSE CM UDiFF final bhavcopy CSV. Only normal EQ-series cash shares are retained. */
object NseBhavcopyParser {
    data class Bar(
        val symbol: String,
        val tradeDate: String,
        val open: Double,
        val high: Double,
        val low: Double,
        val close: Double,
        val volume: Long
    )

    fun parse(reader: Reader, expectedDate: LocalDate): List<Bar> = reader.buffered().use { source ->
        val header = NseInstrumentMasterParser.csvFields(
            source.readLine() ?: error("Empty NSE UDiFF bhavcopy")
        ).map { it.trim().removePrefix("\uFEFF").lowercase(Locale.US) }
        fun index(vararg names: String): Int = header.indexOfFirst { it in names }
        val symbolAt = index("tckrsymb", "symbol")
        val seriesAt = index("sctysrs", "series")
        val openAt = index("opnpric", "open_price")
        val highAt = index("hghpric", "high_price")
        val lowAt = index("lwpric", "low_price")
        val closeAt = index("clspric", "close_price")
        val volumeAt = index("ttltradgvol", "total_traded_quantity")
        require(listOf(symbolAt, seriesAt, openAt, highAt, lowAt, closeAt, volumeAt).all { it >= 0 }) {
            "NSE UDiFF bhavcopy missing required OHLCV columns"
        }
        val result = linkedMapOf<String, Bar>()
        var rows = 0
        for (line in source.lineSequence()) {
            if (line.isBlank()) continue
            require(++rows <= 100_000) { "NSE UDiFF bhavcopy row limit exceeded" }
            val f = NseInstrumentMasterParser.csvFields(line)
            if (!f.getOrNull(seriesAt).orEmpty().trim().equals("EQ", true)) continue
            val symbol = NseInstrumentMasterParser.validSymbol(f.getOrNull(symbolAt).orEmpty()) ?: continue
            fun price(at: Int): Double? = f.getOrNull(at)?.trim()?.replace(",", "")?.toDoubleOrNull()
                ?.takeIf { it.isFinite() && it > 0.0 }
            val open = price(openAt) ?: continue
            val high = price(highAt) ?: continue
            val low = price(lowAt) ?: continue
            val close = price(closeAt) ?: continue
            val volume = f.getOrNull(volumeAt)?.trim()?.replace(",", "")?.toLongOrNull()
                ?.takeIf { it >= 0L } ?: continue
            if (high < low || high < maxOf(open, close) || low > minOf(open, close)) continue
            result[symbol] = Bar(symbol, expectedDate.toString(), open, high, low, close, volume)
        }
        require(result.size >= MIN_VALID_EQUITY_ROWS) {
            "NSE UDiFF bhavcopy contained only ${result.size} validated EQ rows"
        }
        result.values.sortedBy { it.symbol }
    }

    const val MIN_VALID_EQUITY_ROWS = 300
}
