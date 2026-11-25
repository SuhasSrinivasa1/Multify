package com.multify.traderpro.data.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.multify.traderpro.engine.EquityEodBar
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.InputStreamReader
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.util.Locale
import java.util.zip.ZipInputStream

/** Validates a user-selected NSE UDiFF ZIP or Full Bhavcopy CSV; performs no network or broker calls. */
internal object NseBhavcopyFileImporter {
    data class Parsed(val fileName: String, val date: LocalDate, val rows: List<EquityEodBar>)

    private const val MIN_VALID_EQ_ROWS = 1500
    private const val MAX_ROWS = 12000
    private const val MAX_CSV_CHARS = 20_000_000L
    private val basic = DateTimeFormatter.BASIC_ISO_DATE
    private val symbolRegex = Regex("[A-Z0-9&._-]{1,32}")

    fun displayName(context: Context, uri: Uri): String =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0).orEmpty() else ""
        } ?: uri.lastPathSegment.orEmpty()

    fun parse(context: Context, uri: Uri): Parsed {
        val name = displayName(context, uri).ifBlank { "NSE report" }
        val raw = context.contentResolver.openInputStream(uri) ?: error("Unable to open selected report")
        val result = BufferedInputStream(raw).use { input ->
            input.mark(4)
            val signature = ByteArray(4)
            val read = input.read(signature)
            input.reset()
            val isZip = read >= 2 && signature[0] == 0x50.toByte() && signature[1] == 0x4b.toByte()
            if (isZip) {
                ZipInputStream(input).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null && entry.isDirectory) entry = zip.nextEntry
                    require(entry != null && entry.name.endsWith(".csv", true)) {
                        "ZIP must begin with a CSV report; unexpected entries are rejected safely"
                    }
                    val parsed = parseUdiFF(
                        BufferedReader(InputStreamReader(zip, Charsets.UTF_8)),
                        entry.name
                    )
                    zip.closeEntry()
                    parsed
                }
            } else {
                BufferedReader(InputStreamReader(input, Charsets.UTF_8)).use { reader ->
                    parseFullBhavcopy(reader)
                }
            }
        }
        return Parsed(name, result.first, result.second)
    }

    internal fun parseUdiFF(
        reader: BufferedReader,
        entryName: String
    ): Pair<LocalDate, List<EquityEodBar>> {
        val headings = cells(reader.readLine() ?: error("UDiFF header missing"))
            .map { it.removePrefix("\uFEFF").trim() }
        val indices = listOf("TckrSymb", "SctySrs", "TradDt", "OpnPric", "HghPric", "LwPric", "ClsPric", "TtlTradgVol")
            .associateWith { field ->
                headings.indexOf(field).takeIf { it >= 0 } ?: error("UDiFF missing " + field)
            }
        val map = linkedMapOf<String, EquityEodBar>()
        var actualDate: LocalDate? = null
        var rowsRead = 0
        var chars = 0L
        while (true) {
            val line = reader.readLine() ?: break
            chars += line.length
            check(line.length <= 1_000_000 && chars <= MAX_CSV_CHARS) { "UDiFF CSV exceeds safe size" }
            rowsRead++
            check(rowsRead <= MAX_ROWS) { "UDiFF has too many rows" }
            val values = cells(line)
            if (values.size <= (indices.values.maxOrNull() ?: 0)) continue
            if (values[indices.getValue("SctySrs")].trim() != "EQ") continue
            val rowDate = parseDate(values[indices.getValue("TradDt")]) ?: error("UDiFF trade date invalid")
            if (actualDate == null) actualDate = rowDate else check(actualDate == rowDate) {
                "UDiFF contains mixed trade dates"
            }
            val symbol = values[indices.getValue("TckrSymb")].trim().uppercase(Locale.US)
            if (!symbolRegex.matches(symbol)) continue
            val open = values[indices.getValue("OpnPric")].toDoubleOrNull() ?: continue
            val high = values[indices.getValue("HghPric")].toDoubleOrNull() ?: continue
            val low = values[indices.getValue("LwPric")].toDoubleOrNull() ?: continue
            val close = values[indices.getValue("ClsPric")].toDoubleOrNull() ?: continue
            val volume = values[indices.getValue("TtlTradgVol")].replace(",", "").toLongOrNull() ?: 0L
            if (!validOhlc(open, high, low, close, volume)) continue
            check(symbol !in map) { "UDiFF duplicate EQ symbol " + symbol }
            map[symbol] = EquityEodBar(rowDate, symbol, low, close, volume, open = open, high = high)
        }
        val date = actualDate ?: error("UDiFF has no EQ rows")
        val fileDate = Regex("20\\d{6}").find(entryName)?.value?.let {
            runCatching { LocalDate.parse(it, basic) }.getOrNull()
        }
        if (fileDate != null) check(fileDate == date) { "UDiFF filename date does not match TradDt" }
        check(map.size >= MIN_VALID_EQ_ROWS) { "UDiFF EQ coverage too small: " + map.size }
        return date to map.values.toList()
    }

    internal fun parseFullBhavcopy(reader: BufferedReader): Pair<LocalDate, List<EquityEodBar>> {
        val headings = cells(reader.readLine() ?: error("Full Bhavcopy header missing"))
            .map { it.removePrefix("\uFEFF").trim().uppercase(Locale.US) }
        val indices = listOf("SYMBOL", "SERIES", "DATE1", "OPEN_PRICE", "HIGH_PRICE", "LOW_PRICE", "CLOSE_PRICE", "TTL_TRD_QNTY")
            .associateWith { field ->
                headings.indexOf(field).takeIf { it >= 0 } ?: error("Full Bhavcopy missing " + field)
            }
        val map = linkedMapOf<String, EquityEodBar>()
        var actualDate: LocalDate? = null
        var rowsRead = 0
        var chars = 0L
        while (true) {
            val line = reader.readLine() ?: break
            chars += line.length
            check(line.length <= 1_000_000 && chars <= MAX_CSV_CHARS) { "Full Bhavcopy CSV exceeds safe size" }
            rowsRead++
            check(rowsRead <= MAX_ROWS) { "Full Bhavcopy has too many rows" }
            val values = cells(line)
            if (values.size <= (indices.values.maxOrNull() ?: 0)) continue
            if (values[indices.getValue("SERIES")].trim() != "EQ") continue
            val rowDate = parseDate(values[indices.getValue("DATE1")]) ?: error("Full Bhavcopy DATE1 invalid")
            if (actualDate == null) actualDate = rowDate else check(actualDate == rowDate) {
                "Full Bhavcopy contains mixed dates"
            }
            val symbol = values[indices.getValue("SYMBOL")].trim().uppercase(Locale.US)
            if (!symbolRegex.matches(symbol)) continue
            val open = values[indices.getValue("OPEN_PRICE")].replace(",", "").toDoubleOrNull() ?: continue
            val high = values[indices.getValue("HIGH_PRICE")].replace(",", "").toDoubleOrNull() ?: continue
            val low = values[indices.getValue("LOW_PRICE")].replace(",", "").toDoubleOrNull() ?: continue
            val close = values[indices.getValue("CLOSE_PRICE")].replace(",", "").toDoubleOrNull() ?: continue
            val volume = values[indices.getValue("TTL_TRD_QNTY")].replace(",", "").toLongOrNull() ?: 0L
            if (!validOhlc(open, high, low, close, volume)) continue
            check(symbol !in map) { "Full Bhavcopy duplicate EQ symbol " + symbol }
            map[symbol] = EquityEodBar(rowDate, symbol, low, close, volume, open = open, high = high)
        }
        val date = actualDate ?: error("Full Bhavcopy has no EQ rows")
        check(map.size >= MIN_VALID_EQ_ROWS) { "Full Bhavcopy EQ coverage too small: " + map.size }
        return date to map.values.toList()
    }

    private fun parseDate(raw: String): LocalDate? {
        val value = raw.trim().removePrefix("\uFEFF")
        return runCatching { LocalDate.parse(value) }.getOrNull()
            ?: runCatching {
                val format = DateTimeFormatterBuilder().parseCaseInsensitive()
                    .appendPattern("dd-MMM-uuuu").toFormatter(Locale.US)
                LocalDate.parse(value, format)
            }.getOrNull()
    }

    private fun validOhlc(open: Double, high: Double, low: Double, close: Double, volume: Long): Boolean =
        open.isFinite() && high.isFinite() && low.isFinite() && close.isFinite() &&
            low > 0.0 && open >= low && high >= maxOf(open, close) && close >= low && volume >= 0L

    private fun cells(line: String): List<String> {
        val out = mutableListOf<String>()
        val value = StringBuilder()
        var quoted = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' && quoted && i + 1 < line.length && line[i + 1] == '"' -> {
                    value.append('"')
                    i++
                }
                c == '"' -> quoted = !quoted
                c == ',' && !quoted -> {
                    out += value.toString().trim()
                    value.setLength(0)
                }
                else -> value.append(c)
            }
            i++
        }
        check(!quoted) { "CSV contains unbalanced quotes" }
        out += value.toString().trim()
        return out
    }
}
