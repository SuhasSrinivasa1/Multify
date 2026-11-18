package com.multify.traderpro.engine

import java.io.Reader
import java.util.Locale

/**
 * Pure, testable symbol-master parsers. Never uses price values or mutates the
 * frozen Fixed strategy. Groww's documented instrument.csv is a backup source
 * for NSE CASH equities when NSE's cookie-protected CSV returns HTTP 403.
 */
object NseInstrumentMasterParser {
    private val SYMBOL = Regex("[A-Z0-9&._-]{1,32}")
    private val EQUITY_SERIES = setOf("EQ", "BE", "BZ", "SM", "ST", "IT")
    private const val MAX_ROWS = 1_000_000

    private fun column(value: String): String =
        value.trim().removePrefix("\uFEFF").lowercase(Locale.US).replace(" ", "_")

    fun validSymbol(value: String): String? =
        value.trim().uppercase(Locale.US).takeIf { SYMBOL.matches(it) }

    /** Includes quote/comma handling and BOM headers; malformed/HTML files fail closed. */
    fun csvFields(line: String): List<String> {
        val out = ArrayList<String>(20)
        val buf = StringBuilder()
        var quoted = false
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            when {
                ch == '"' && quoted && i + 1 < line.length && line[i + 1] == '"' -> {
                    buf.append('"')
                    i++
                }
                ch == '"' -> quoted = !quoted
                ch == ',' && !quoted -> {
                    out.add(buf.toString())
                    buf.setLength(0)
                }
                else -> buf.append(ch)
            }
            i++
        }
        require(!quoted) { "Malformed quoted instrument CSV record" }
        out.add(buf.toString())
        return out
    }

    fun parseNse(reader: Reader): List<String> = reader.buffered().use { source ->
        val header = csvFields(source.readLine() ?: error("Empty NSE equity CSV")).map(::column)
        val symbolIndex = header.indexOf("symbol")
        require(symbolIndex >= 0) { "NSE equity CSV lacks SYMBOL header" }
        val result = LinkedHashSet<String>()
        var rows = 0
        for (line in source.lineSequence()) {
            if (line.isBlank()) continue
            require(++rows <= MAX_ROWS) { "NSE equity CSV too large" }
            val fields = csvFields(line)
            if (symbolIndex >= fields.size) continue
            validSymbol(fields[symbolIndex])?.let(result::add)
        }
        result.sorted()
    }

    fun parseGroww(reader: Reader): List<String> = reader.buffered().use { source ->
        val header = csvFields(source.readLine() ?: error("Empty Groww instrument CSV")).map(::column)
        val ex = header.indexOf("exchange")
        val segment = header.indexOf("segment")
        val symbol = header.indexOf("trading_symbol")
        val series = header.indexOf("series")
        val type = header.indexOf("instrument_type")
        require(ex >= 0 && segment >= 0 && symbol >= 0 && series >= 0) {
            "Groww instruments CSV lacks required NSE CASH headers"
        }
        val required = maxOf(ex, segment, symbol, series, type)
        val result = LinkedHashSet<String>()
        var rows = 0
        for (line in source.lineSequence()) {
            if (line.isBlank()) continue
            require(++rows <= MAX_ROWS) { "Groww instrument CSV too large" }
            val fields = csvFields(line)
            if (fields.size <= required) continue
            if (!fields[ex].trim().equals("NSE", true) ||
                !fields[segment].trim().equals("CASH", true) ||
                fields[series].trim().uppercase(Locale.US) !in EQUITY_SERIES ||
                (type >= 0 && fields[type].trim().equals("INDEX", true))
            ) continue
            validSymbol(fields[symbol])?.let(result::add)
        }
        result.sorted()
    }
}
