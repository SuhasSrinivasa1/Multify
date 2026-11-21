package com.multify.traderpro.engine

import java.io.Reader
import java.util.Locale

/**
 * NSE CASH symbol master. Keep the exchange trading symbol distinct from the
 * company name used by the Manual autocomplete; both are sourced from the
 * official instrument files, never guessed from a ticker prefix.
 */
object NseInstrumentMasterParser {
    data class ListedEquity(val symbol: String, val companyName: String)

    private val SYMBOL = Regex("[A-Z0-9&._-]{1,32}")
    private val EQUITY_SERIES = setOf("EQ", "BE", "BZ", "SM", "ST", "IT")
    private const val MAX_ROWS = 1_000_000

    private fun column(value: String): String =
        value.trim().removePrefix("\uFEFF").lowercase(Locale.US).replace(" ", "_")

    fun validSymbol(value: String): String? =
        value.trim().uppercase(Locale.US).takeIf { SYMBOL.matches(it) }

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

    private fun cleanedName(value: String?, symbol: String): String =
        value.orEmpty().replace(Regex("[\\r\\n\\t|]+"), " ")
            .replace(Regex("\\s+"), " ").trim().take(160).ifBlank { symbol }

    private fun nameColumn(header: List<String>): Int =
        header.indexOfFirst { it in setOf(
            "name_of_company", "company_name", "company", "name", "display_name",
            "instrument_name", "security_name", "companyname", "instrument"
        ) }

    fun parseNseInstruments(reader: Reader): List<ListedEquity> = reader.buffered().use { source ->
        val header = csvFields(source.readLine() ?: error("Empty NSE equity CSV")).map(::column)
        val symbolIndex = header.indexOf("symbol")
        val nameIndex = nameColumn(header)
        require(symbolIndex >= 0) { "NSE equity CSV lacks SYMBOL header" }
        val result = linkedMapOf<String, ListedEquity>()
        var rows = 0
        for (line in source.lineSequence()) {
            if (line.isBlank()) continue
            require(++rows <= MAX_ROWS) { "NSE equity CSV too large" }
            val fields = csvFields(line)
            val symbol = fields.getOrNull(symbolIndex)?.let(::validSymbol) ?: continue
            result[symbol] = ListedEquity(symbol, cleanedName(fields.getOrNull(nameIndex), symbol))
        }
        result.values.sortedBy { it.symbol }
    }

    fun parseGrowwInstruments(reader: Reader): List<ListedEquity> = reader.buffered().use { source ->
        val header = csvFields(source.readLine() ?: error("Empty Groww instrument CSV")).map(::column)
        val ex = header.indexOf("exchange")
        val segment = header.indexOf("segment")
        val symbol = header.indexOf("trading_symbol")
        val series = header.indexOf("series")
        val type = header.indexOf("instrument_type")
        val name = nameColumn(header)
        require(ex >= 0 && segment >= 0 && symbol >= 0 && series >= 0) {
            "Groww instruments CSV lacks required NSE CASH headers"
        }
        val required = maxOf(ex, segment, symbol, series, type)
        val result = linkedMapOf<String, ListedEquity>()
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
            val valid = validSymbol(fields[symbol]) ?: continue
            result[valid] = ListedEquity(valid, cleanedName(fields.getOrNull(name), valid))
        }
        result.values.sortedBy { it.symbol }
    }

    /** Backward-compatible callers continue receiving the exact symbol list. */
    fun parseNse(reader: Reader): List<String> = parseNseInstruments(reader).map { it.symbol }
    fun parseGroww(reader: Reader): List<String> = parseGrowwInstruments(reader).map { it.symbol }
}
