package com.multify.traderpro.data.repository

import java.util.Locale

/**
 * Source-specific valuation rules for the Lows screen ONLY.
 * The NSE PE report contains individual symbol PE, not industry completeness.
 * Never infer missing P/E or claim a whole-industry winner from partial classification.
 */
internal object NsePeReportResearchPolicy {
    private fun normalize(s: String): String =
        s.lowercase(Locale.US).replace(Regex("[^a-z0-9]"), "")

    /** Official daily PE_ddmmyy.csv: Symbol, Symbol P/E, Adjusted P/E. */
    fun parse(raw: String): Map<String, Double> {
        val lines = raw.lineSequence().filter { it.isNotBlank() }.iterator()
        require(lines.hasNext()) { "NSE_PE_EMPTY" }
        val headers = EquityEodResearchRepository.csvCells(lines.next())
            .map { normalize(it.removePrefix("\uFEFF")) }
        val sym = headers.indexOf("symbol")
        val pe = headers.indexOf("symbolpe")
        require(sym >= 0 && pe >= 0) { "NSE_PE_MISSING_OFFICIAL_COLUMNS" }
        val output = linkedMapOf<String, Double>()
        var sourceRows = 0
        while (lines.hasNext()) {
            val row = EquityEodResearchRepository.csvCells(lines.next())
            if (row.size <= maxOf(sym, pe)) continue
            val name = row[sym].trim().uppercase(Locale.US)
            if (!name.matches(Regex("[A-Z0-9&._-]{1,32}"))) continue
            sourceRows++
            require(sourceRows <= 8_000) { "NSE_PE_OVERSIZE" }
            // Missing/zero/negative P/E is not a low P/E; earnings may be negative.
            val value = row[pe].replace(",", "").trim().toDoubleOrNull()
            if (value != null && value.isFinite() && value > 0.0 && value <= 100_000.0)
                output.putIfAbsent(name, value)
        }
        // Reject a partial/error HTML response even when it resembles CSV.
        require(sourceRows >= 1_200 && output.size >= 600) {
            "NSE_PE_INCOMPLETE_${sourceRows}_${output.size}"
        }
        return output
    }
}

/** Industry names are sourced from NSE Indices constituents; this is not all NSE equities. */
internal object NseIndustryResearchPolicy {
    fun parseIndex(raw: String): Map<String, String> {
        val lines = raw.lineSequence().filter { it.isNotBlank() }.iterator()
        require(lines.hasNext()) { "NSE_INDUSTRY_EMPTY" }
        val headers = EquityEodResearchRepository.csvCells(lines.next())
            .map { it.removePrefix("\uFEFF").trim().lowercase(Locale.US) }
        val symbolIndex = headers.indexOf("symbol")
        val industryIndex = headers.indexOf("industry")
        require(symbolIndex >= 0 && industryIndex >= 0) { "NSE_INDUSTRY_NO_COLUMNS" }
        val result = linkedMapOf<String, String>()
        while (lines.hasNext() && result.size < 2_000) {
            val row = EquityEodResearchRepository.csvCells(lines.next())
            val sym = row.getOrNull(symbolIndex)?.trim()?.uppercase(Locale.US).orEmpty()
            val industry = row.getOrNull(industryIndex)?.trim().orEmpty()
            if (sym.matches(Regex("[A-Z0-9&._-]{1,32}")) &&
                industry.length in 3..120 && !industry.equals("UNKNOWN", true)) {
                result.putIfAbsent(sym, industry)
            }
        }
        require(result.size >= 400) { "NSE_INDUSTRY_INCOMPLETE_${result.size}" }
        return result
    }
}

internal object LowPeScreenResearchPolicy {
    /**
     * Only promote an AMFI SMALL_CAP when its industry and POSITIVE NSE P/E are known,
     * and every positive-P/E NSE name has an authoritative industry classification.
     * Partial Nifty index constituency mapping cannot prove a market-wide minimum;
     * hence this conservatively returns no small caps until coverage is complete.
     */
    fun marketIndustryCoverageComplete(
        peBySymbol: Map<String, Double>,
        industries: Map<String, String>
    ): Boolean = peBySymbol.size >= 600 &&
        industries.size >= peBySymbol.size &&
        peBySymbol.keys.all { industries[it]?.isNotBlank() == true }

    fun eligibleSmallCap(
        row: EquityLowObservation,
        peBySymbol: Map<String, Double>,
        industries: Map<String, String>,
        sameEodDay: Boolean
    ): Boolean {
        if (row.marketCapCategory != "SMALL_CAP" || !sameEodDay ||
            !marketIndustryCoverageComplete(peBySymbol, industries)) return false
        val industry = industries[row.symbol] ?: return false
        val pe = peBySymbol[row.symbol]?.takeIf { it.isFinite() && it > 0.0 } ?: return false
        return peBySymbol.none { (sym, other) ->
            sym != row.symbol && industries[sym] == industry &&
                other.isFinite() && other > 0.0 && other < pe - 1e-9
        }
    }

    fun visibleAndSorted(
        candidates: List<EquityLowObservation>,
        peBySymbol: Map<String, Double>,
        industries: Map<String, String>,
        peMatchesEodDay: Boolean
    ): List<EquityLowObservation> {
        val quotes = if (peMatchesEodDay) peBySymbol else emptyMap()
        return candidates.asSequence()
            .filter { r -> r.marketCapCategory == "LARGE_CAP" ||
                r.marketCapCategory == "MID_CAP" ||
                eligibleSmallCap(r, quotes, industries, peMatchesEodDay) }
            .map { row -> row.copy(
                peRatio = quotes[row.symbol],
                industry = industries[row.symbol]
            ) }
            .sortedWith(
                compareBy<EquityLowObservation> { it.peRatio ?: Double.POSITIVE_INFINITY }
                    .thenBy { kotlin.math.abs(it.distancePct) }
                    .thenBy { it.symbol }
            )
            .toList()
    }
}
