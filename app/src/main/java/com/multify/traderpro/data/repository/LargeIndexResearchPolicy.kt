package com.multify.traderpro.data.repository

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Validated NSE all-indices EOD CSV. It is NOT a live index quote. */
internal data class LargeIndexDaily(
    val day: LocalDate,
    val name: String,
    val close: Double,
    val changePct: Double?,
    val pe: Double?,
    val pb: Double?
)

internal object LargeIndexResearchPolicy {
    private val ddMMyyyy = DateTimeFormatter.ofPattern("ddMMyyyy", Locale.US)
    private val candidateDateFormats = listOf(
        DateTimeFormatter.ofPattern("dd-MM-yyyy", Locale.US),
        DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.US),
        DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.US),
        DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.US),
        DateTimeFormatter.ISO_LOCAL_DATE
    )
    private fun normalized(x: String) = x.uppercase(Locale.US)
        .replace(Regex("[^A-Z0-9]"), "")
    fun indexName(x: String) = x.trim().uppercase(Locale.US).replace(Regex("\\s+"), " ")

    private fun numeric(x: String?): Double? = x?.trim()?.replace(",", "")?.replace("%", "")
        ?.toDoubleOrNull()?.takeIf { it.isFinite() }
    private fun positive(x: String?): Double? = numeric(x)?.takeIf { it > 0 && it < 1e7 }
    private fun date(x: String?): LocalDate? {
        val value = x?.trim().orEmpty()
        return candidateDateFormats.firstNotNullOfOrNull { fmt ->
            runCatching { LocalDate.parse(value, fmt) }.getOrNull()
        }
    }

    fun parseCsv(csv: String, expectedDay: LocalDate): List<LargeIndexDaily> {
        require(csv.length in 1_500..500_000) { "INDEX_EOD_BAD_SIZE" }
        val lines=csv.lineSequence().filter { it.isNotBlank() }.iterator()
        require(lines.hasNext()) { "INDEX_EOD_HEADER_MISSING" }
        val headers=EquityEodResearchRepository.csvCells(lines.next())
            .map { normalized(it.removePrefix("\uFEFF")) }
        fun idx(vararg names: String) = names.firstNotNullOfOrNull { n ->
            headers.indexOf(n).takeIf { it >= 0 }
        }
        val nameCol=idx("INDEXNAME") ?: error("INDEX_EOD_NAME_MISSING")
        val closeCol=idx("CLOSINGINDEXVALUE","CLOSING","CLOSE") ?: error("INDEX_EOD_CLOSE_MISSING")
        val dayCol=idx("INDEXDATE","DATE") ?: error("INDEX_EOD_DATE_MISSING")
        val changeCol=idx("CHANGE","CHANGEPERCENT","CHANGEPCT")
        val peCol=idx("PE","PERATIO")
        val pbCol=idx("PB","PBRATIO")
        val out=linkedMapOf<String,LargeIndexDaily>()
        var seen=0
        while(lines.hasNext()){
            val row=EquityEodResearchRepository.csvCells(lines.next())
            if(row.size <= maxOf(nameCol,closeCol,dayCol)) continue
            seen++; require(seen <= 500) { "INDEX_EOD_TOO_MANY_ROWS" }
            val day=date(row[dayCol]) ?: continue
            if(day != expectedDay) continue
            val name=indexName(row[nameCol])
            val close=positive(row[closeCol]) ?: continue
            if(name.isBlank() || name.length>75)continue
            val change=changeCol?.let{ numeric(row.getOrNull(it)) }
                ?.takeIf { it in -25.0..25.0 }
            out.putIfAbsent(name,LargeIndexDaily(day,name,close,change,
                peCol?.let{positive(row.getOrNull(it))},
                pbCol?.let{positive(row.getOrNull(it))}))
        }
        require(out.size >= 30 && out.containsKey("NIFTY 50")) {
            "INDEX_EOD_INCOMPLETE_${out.size}"
        }
        return out.values.toList()
    }

    /** These classifications are hypotheses for research, not execution gates. */
    fun regime(dailyChangePct: Double?): String = when {
        dailyChangePct == null || !dailyChangePct.isFinite() -> "UNKNOWN"
        dailyChangePct >= 0.25 -> "BULLISH"
        dailyChangePct <= -0.25 -> "BEARISH"
        else -> "FLAT"
    }

    /**
     * The Nifty index membership is not a generic industry mapping.
     * Return null unless a sector has an established NSE sector index counterpart.
     */
    fun sectorIndex(industry: String): String? {
        val x=industry.uppercase(Locale.US).trim()
        return when {
            x=="BANK" || x.contains("BANKING") || x=="BANKS" -> "NIFTY BANK"
            x=="IT" || x.contains("INFORMATION TECHNOLOGY") || x.contains("SOFTWARE") -> "NIFTY IT"
            x.contains("FINANCIAL SERVICES") || x.contains("FINANCE") -> "NIFTY FINANCIAL SERVICES"
            x=="AUTO" || x.contains("AUTOMOBILE") -> "NIFTY AUTO"
            x=="PHARMA" || x.contains("PHARMACEUTICAL") -> "NIFTY PHARMA"
            x=="FMCG" -> "NIFTY FMCG"
            x=="METAL" || x.contains("METAL") -> "NIFTY METAL"
            x=="ENERGY" || x.contains("POWER") -> "NIFTY ENERGY"
            x=="REALTY" || x.contains("REAL ESTATE") -> "NIFTY REALTY"
            x=="MEDIA" -> "NIFTY MEDIA"
            x.contains("HEALTHCARE") -> "NIFTY HEALTHCARE INDEX"
            x.contains("OIL") && x.contains("GAS") -> "NIFTY OIL & GAS"
            x.contains("CONSUMER DURABLE") -> "NIFTY CONSUMER DURABLES"
            else -> null
        }
    }
}
