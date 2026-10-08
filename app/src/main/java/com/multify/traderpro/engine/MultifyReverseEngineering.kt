package com.multify.traderpro.engine

data class PositiveMultifyFundamentalCase(
    val symbol: String,
    val callDate: String,
    val multifyReturnPct: Double,
    val sessionCloseChangePct: Double,
    val marketCapCrore: Double?,
    val pe: Double?,
    val rocePct: Double?,
    val roePct: Double?,
    val fundamentalFact: String,
    val fundamentalSource: String,
    val closeSource: String
) {
    val snapshot: String
        get() = buildList {
            marketCapCrore?.let { add("MCap ₹${fmt0(it)} Cr") }
            add("P/E " + (pe?.let { fmt1(it) } ?: "N/M"))
            rocePct?.let { add("ROCE ${fmt1(it)}%") }
            roePct?.let { add("ROE ${fmt1(it)}%") }
        }.joinToString(" · ")

    private fun fmt0(v: Double) = String.format(java.util.Locale.US, "%,.0f", v)
    private fun fmt1(v: Double) = String.format(java.util.Locale.US, "%.1f", v)
}

/**
 * Positive-only reverse-engineering sample.
 * Eligibility: positive-return BUY in the supplied Multify history AND a positive NSE full-session close.
 * Public fundamental values are current/recent snapshots used only for hypothesis generation.
 * They are not point-in-time reconstructed fundamentals and never rewrite a frozen historical decision.
 */
object MultifyReverseEngineering {
    const val STUDY_VERSION = "MFDNA-1.0-positive-close"
    const val RESEARCH_ONLY = true

    val positiveCases = listOf(
        PositiveMultifyFundamentalCase("KARURVYSYA","2026-07-30",0.38,0.06,31790.0,11.6,null,19.1,"5Y profit CAGR ~47%; a profitable call even on a modestly positive full-day close.","https://www.screener.in/company/KARURVYSYA/","https://www.equitypandit.com/historical-data/karurvysya"),
        PositiveMultifyFundamentalCase("THANGAMAYL","2026-07-27",1.15,5.84,14958.0,38.1,25.5,28.1,"High capital efficiency and ~32.5% 5Y profit CAGR; strong positive session.","https://www.screener.in/company/THANGAMAYL/","https://www.equitypandit.com/historical-data/thangamayl"),
        PositiveMultifyFundamentalCase("ESAFSFB","2026-07-24",3.79,8.83,2155.0,null,5.65,-8.86,"Weak/negative ROE profile still produced a successful +8.83% session: quality is not a hard gate.","https://www.screener.in/company/ESAFSFB/","https://www.equitypandit.com/historical-data/esafsfb"),
        PositiveMultifyFundamentalCase("AEGISLOG","2026-07-23",1.54,1.30,51253.0,40.4,33.4,29.7,"High ROCE/ROE and long-term sales growth; a quality-growth example.","https://www.screener.in/company/AEGISLOG/","https://www.equitypandit.com/historical-data/AEGISLOG"),
        PositiveMultifyFundamentalCase("LODHA","2026-07-21",0.40,0.89,110000.0,33.0,14.5,13.8,"Large liquid real-estate name with strong 5Y profit growth; valuation is not especially cheap.","https://www.screener.in/company/LODHA/","https://www.equitypandit.com/historical-data/LODHA"),
        PositiveMultifyFundamentalCase("EXIDEIND","2026-07-17",0.46,3.30,33605.0,27.9,10.1,7.49,"Almost debt free, but modest ROE: balance-sheet quality may matter more than a rigid ROE floor.","https://www.screener.in/company/EXIDEIND/","https://www.equitypandit.com/historical-data/exideind"),
        PositiveMultifyFundamentalCase("PAISALO","2026-07-16",1.44,2.43,7315.0,29.3,12.4,14.3,"5Y profit CAGR ~32.7%; a mid-cap growth example.","https://www.screener.in/company/PAISALO/","https://www.equitypandit.com/historical-data/PAISALO"),
        PositiveMultifyFundamentalCase("NATIONALUM","2026-07-14",0.36,2.73,57417.0,8.51,39.2,29.1,"Low valuation, high ROCE/ROE and almost debt free; classic value/quality positive.","https://www.screener.in/company/NATIONALUM/","https://www.equitypandit.com/historical-data/nationalum"),
        PositiveMultifyFundamentalCase("TRIVENI","2026-07-13",0.25,3.19,null,19.8,8.79,8.36,"Moderate valuation and modest returns on capital still passed; no elite-quality requirement.","https://www.screener.in/company/TRIVENI/","https://www.equitypandit.com/historical-data/triveni"),
        PositiveMultifyFundamentalCase("RAIN","2026-07-10",0.07,1.32,7164.0,503.0,2.31,1.03,"Very high P/E and low ROE/ROCE yet a positive call/day: strong evidence against a cheap-P/E quality filter.","https://www.screener.in/company/RAIN/","https://www.equitypandit.com/historical-data/rain"),
        PositiveMultifyFundamentalCase("BSOFT","2026-07-10",2.12,5.57,7544.0,17.1,23.8,18.6,"Almost debt free with solid ROCE; profitable momentum day.","https://www.screener.in/company/BSOFT/","https://www.equitypandit.com/historical-data/bsoft"),
        PositiveMultifyFundamentalCase("ANANTRAJ","2026-07-09",0.99,4.53,20938.0,68.0,7.88,6.81,"High valuation/low ROE but ~71.5% 5Y profit CAGR and reduced debt; growth/catalyst can dominate valuation.","https://www.screener.in/company/ANANTRAJ/","https://www.equitypandit.com/historical-data/anantraj"),
        PositiveMultifyFundamentalCase("GOKULAGRO","2026-07-09",1.36,3.00,6073.0,17.1,38.7,31.2,"High ROCE/ROE and ~56.7% 5Y profit CAGR; strong quality-growth example.","https://www.screener.in/company/GOKULAGRO/","https://www.equitypandit.com/historical-data/GOKULAGRO"),
        PositiveMultifyFundamentalCase("SUVEN","2026-07-09",1.30,9.35,8156.0,null,-79.5,-81.5,"Negative ROCE/ROE but almost debt free and a +9.35% session: profitability ratios alone cannot explain selection.","https://www.screener.in/company/SUVEN/","https://www.equitypandit.com/historical-data/suven"),
        PositiveMultifyFundamentalCase("STOVEKRAFT","2026-07-08",1.40,0.08,2659.0,54.7,10.9,8.46,"Higher P/E with modest ROE; debt reduction may be contextual rather than an entry trigger.","https://www.screener.in/company/STOVEKRAFT/","https://www.equitypandit.com/historical-data/stovekraft"),
        PositiveMultifyFundamentalCase("SHILPAMED","2026-06-30",1.71,4.32,20143.0,129.0,4.79,3.88,"Very high P/E and low returns on capital, but almost debt free / expected-quarter context; a catalyst-style case.","https://www.screener.in/company/SHILPAMED/","https://www.equitypandit.com/historical-data/SHILPAMED"),
        PositiveMultifyFundamentalCase("FINPIPE","2026-06-18",0.93,2.40,9560.0,16.2,11.4,8.45,"Almost debt free with moderate P/E; a balance-sheet/liquidity rather than high-ROE example.","https://www.screener.in/company/FINPIPE/","https://www.equitypandit.com/historical-data/finpipe"),
        PositiveMultifyFundamentalCase("IDBI","2026-06-17",3.97,17.12,89355.0,9.29,6.58,14.9,"Low P/E and ~47.6% 5Y profit CAGR; very strong event/momentum session.","https://www.screener.in/company/IDBI/","https://www.equitypandit.com/historical-data/idbi"),
        PositiveMultifyFundamentalCase("SUZLON","2026-06-16",0.02,4.23,54107.0,17.2,34.2,39.7,"Almost debt free, high ROCE/ROE and strong 5Y profit growth; renewable-theme quality/momentum example.","https://www.screener.in/company/SUZLON/","https://www.equitypandit.com/historical-data/suzlon"),
        PositiveMultifyFundamentalCase("INOXWIND","2026-06-12",2.70,8.55,10917.0,20.5,11.3,6.3,"Moderate P/E with ~30% 5Y profit CAGR; strong positive renewable-theme session.","https://www.screener.in/company/INOXWIND/","https://www.equitypandit.com/historical-data/inoxwind")
    )

    val hypotheses = listOf(
        "No rigid valuation ceiling: numeric P/E spans roughly 8.5x to 503x, with some loss-making/NM cases.",
        "No minimum profitability floor: positive-close calls include both >30% ROE names and negative-ROE names.",
        "The common denominator is more plausibly tradeability + fresh catalyst/theme + short-horizon price/volume confirmation than one fundamental style.",
        "Fundamentals are best treated as universe, event-risk and confidence priors: growth, leverage, market cap/liquidity, capital efficiency and earnings/corporate-event context.",
        "Likely timing parameters to test are RVOL/volume acceleration, VWAP state/slope, EMA9/20 structure, opening-range/breakout behavior, relative strength, spread/liquidity and immediate post-alert trajectory.",
        "These 20 winners are hypothesis generation only. No fundamental factor is promoted into live weights until positive + negative examples pass walk-forward/out-of-sample validation."
    )

    fun csv(): String = buildString {
        appendLine("symbol,call_date,multify_return_pct,session_close_change_pct,market_cap_crore,pe,roce_pct,roe_pct,fundamental_fact,fundamental_source,close_source")
        positiveCases.forEach { x ->
            appendLine(listOf(
                x.symbol, x.callDate, x.multifyReturnPct, x.sessionCloseChangePct, x.marketCapCrore,
                x.pe, x.rocePct, x.roePct, x.fundamentalFact, x.fundamentalSource, x.closeSource
            ).joinToString(",") { csvCell(it) })
        }
    }

    fun report(): String = buildString {
        appendLine("Multify DNA — positive-close reverse engineering")
        appendLine("Study: $STUDY_VERSION")
        appendLine("RESEARCH ONLY: true")
        appendLine()
        appendLine("Eligibility: profitable BUY in supplied Multify history AND positive full-session close.")
        appendLine("Public fundamental figures are current/recent snapshots used for hypothesis generation, not reconstructed historical fundamentals.")
        appendLine("They never rewrite frozen live decisions and are not live execution weights.")
        appendLine()
        appendLine("Observed sample facts")
        appendLine("---------------------")
        positiveCases.forEachIndexed { i, x ->
            appendLine("${i + 1}. ${x.symbol} ${x.callDate} · Multify ${fmtSigned(x.multifyReturnPct)}% · session ${fmtSigned(x.sessionCloseChangePct)}% · ${x.snapshot} · ${x.fundamentalFact}")
        }
        appendLine()
        appendLine("Reverse-engineering hypotheses")
        appendLine("------------------------------")
        hypotheses.forEach { appendLine("- $it") }
    }

    private fun fmtSigned(v: Double) = String.format(java.util.Locale.US, "%+.2f", v)
    private fun csvCell(v: Any?): String {
        val s = v?.toString().orEmpty()
        return "\"" + s.replace("\"", "\"\"") + "\""
    }
}
