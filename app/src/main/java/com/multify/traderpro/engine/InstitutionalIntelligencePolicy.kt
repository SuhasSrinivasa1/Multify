package com.multify.traderpro.engine

import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

data class InstitutionalDeal(
    val day:String,val symbol:String,val company:String,val investor:String,
    val category:String,val side:String,val shares:Long,val price:Double
)
data class InstitutionalStock(
    val symbol:String,val company:String,val shares:Long,val amountRupees:Double,
    val fiiRupees:Double,val diiRupees:Double,val investors:List<String>
)
data class InstitutionalReport(
    val day:String="",val source:String="",val receivedAtMs:Long=0L,
    val disclosedRows:Int=0,val unmatchedRows:Int=0,val duplicateRows:Int=0,
    val deals:List<InstitutionalDeal> = emptyList(),
    val retainedAllRows:Boolean = false
)
data class InstitutionalSnapshot(
    val bulk:InstitutionalReport?=null,val block:InstitutionalReport?=null,
    val history:List<InstitutionalReport> = emptyList(),
    val sessions:List<String> = emptyList(),
    val hourly:InstitutionalHourly? = null,
    val hourlyHistory:List<InstitutionalHourly> = emptyList(),
    val sectors:Map<String,String> = emptyMap(),
    val swing:Set<String> = emptySet(),
    val lows:Set<String> = emptySet(),
    val sectorMoves:Map<String,Double> = emptyMap(),
    val niftyEodPct:Double? = null,
    val contextDay:String = "",
    val outcomes:List<InstitutionalOutcome> = emptyList()
)

/**
 * Conservative named-client subset only. Exact names from issuer category filings
 * and AMFI fund directory, not guesses from a broker's brand or "capital" suffix.
 * DII here covers the matched domestic mutual funds and LIC only, not all DIIs.
 * Historical filings establish identity but not a complete current FPI register.
 */
object InstitutionalIntelligencePolicy {
    const val TOP=20
    const val AMFI_SOURCE="https://www.amfiindia.com/online-center/investonlineInMF"
    const val FPI_SOURCE="https://digital.camsonline.com/cams/documents/shareholding_pattern/CAMS%20SHP%20report%2031122025.html"
    const val FPI_SOURCE_2="https://www.powergrid.in/sites/default/files/Investor_Relation/Reports_Filings/Share_Holding_Pattern/2025-26/SHP_2025-26_Q4.html"
    const val FPI_SOURCE_3="https://digital.camsonline.com/cams/documents/shareholding_pattern/SHPReport31032025.html"
    private fun norm(s:String)=s.trim().uppercase(Locale.ROOT).replace(Regex("\\s+")," ")
    private fun header(s:String)=norm(s).replace(Regex("[^A-Z0-9]"),"")
    private val fii=setOf(
        "GOVERNMENT OF SINGAPORE","VANGUARD TOTAL INTERNATIONAL STOCK INDEX FUND",
        "VANGUARD EMERGING MARKETS STOCK INDEX FUND","GOVERNMENT PENSION FUND GLOBAL",
        "J P MORGAN FUNDS","GOLDMAN SACHS FUNDS - GOLDMAN SACHS INDIA EQUITY PORTFOLIO",
        "ISHARES CORE MSCI EMERGING MARKETS ETF","FIDELITY EMERGING MARKETS FUND AS MANAGED BY FIAM LLC",
        "ARISAIG ASIA FUND LIMITED","FRANKLIN TEMPLETON INVESTMENT FUNDS - FRANKLIN INDIA FUND",
        "GOLDMAN SACHS INDIA EQUITY PORTFOLIO"
    )
    private val dii=setOf(
        "360 ONE MUTUAL FUND","ADITYA BIRLA SUN LIFE MUTUAL FUND","ANGEL ONE MUTUAL FUND",
        "ASK MUTUAL FUND","AXIS MUTUAL FUND","BANDHAN MUTUAL FUND","BANK OF INDIA MUTUAL FUND",
        "BARODA BNP PARIBAS MUTUAL FUND","CANARA ROBECO MUTUAL FUND","DSP MUTUAL FUND",
        "EDELWEISS MUTUAL FUND","FRANKLIN TEMPLETON MUTUAL FUND","GROWW MUTUAL FUND",
        "HDFC MUTUAL FUND","HELIOS MUTUAL FUND","HSBC MUTUAL FUND","ICICI PRUDENTIAL MUTUAL FUND",
        "INVESCO MUTUAL FUND","ITI MUTUAL FUND","JIO BLACKROCK MUTUAL FUND",
        "JM FINANCIAL MUTUAL FUND","KOTAK MAHINDRA MUTUAL FUND","LIC MUTUAL FUND",
        "MAHINDRA MANULIFE MUTUAL FUND","MIRAE ASSET MUTUAL FUND","MONARCH MUTUAL FUND",
        "MOTILAL OSWAL MUTUAL FUND","NIPPON INDIA MUTUAL FUND","NJ MUTUAL FUND",
        "OLD BRIDGE MUTUAL FUND","PGIM INDIA MUTUAL FUND","PPFAS MUTUAL FUND",
        "QUANT MUTUAL FUND","QUANTUM MUTUAL FUND","SAMCO MUTUAL FUND","SBI MUTUAL FUND",
        "SUNDARAM MUTUAL FUND","TATA MUTUAL FUND","TRUST MUTUAL FUND","UNIFI MUTUAL FUND",
        "UNION MUTUAL FUND","UTI MUTUAL FUND","THE WEALTH COMPANY MUTUAL FUND",
        "WHITEOAK CAPITAL MUTUAL FUND","LIFE INSURANCE CORPORATION OF INDIA"
    )
    private fun identityKey(name:String)=norm(name).replace(Regex("[.,]"),"").replace(Regex("\\s+")," ").trim()
    private val verifiedKeys=(fii.map{identityKey(it) to "FII"}+dii.map{identityKey(it) to "DII"})
        .groupBy({it.first},{it.second}).mapValues{it.value.distinct()}
    fun category(name:String):String? = verifiedKeys[identityKey(name)]?.singleOrNull()

    private val formats=listOf("dd-MMM-uuuu","dd-MMM-uu","dd-MM-uuuu","dd/MM/uuuu","uuuu-MM-dd")
        .map{java.time.format.DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern(it).toFormatter(Locale.ENGLISH).withResolverStyle(java.time.format.ResolverStyle.STRICT)}
    private fun date(s:String):LocalDate? = formats.firstNotNullOfOrNull{f->
        runCatching{LocalDate.parse(s.trim(),f)}.getOrNull()
    }
    private fun cells(line:String):List<String>{
        val result=mutableListOf<String>();val b=StringBuilder();var quoted=false;var i=0
        while(i<line.length){
            val c=line[i]
            when {
                c=='"' && quoted && i+1<line.length && line[i+1]=='"' -> {b.append('"');i++}
                c=='"' -> quoted=!quoted
                c==',' && !quoted -> {result+=b.toString().trim();b.setLength(0)}
                else -> b.append(c)
            }
            i++
        }
        require(!quoted){"UNBALANCED_CSV"}
        result+=b.toString().trim()
        return result
    }
    fun parseLatest(csv:String,latestAllowed:LocalDate,source:String,receivedAtMs:Long):InstitutionalReport {
        require(csv.length in 20..2_000_000)
        val lines=csv.lineSequence().filter{it.isNotBlank()}.iterator()
        require(lines.hasNext())
        val columns=cells(lines.next().removePrefix("\uFEFF")).map(::header)
        val d=columns.indexOfFirst{it in setOf("DATE","TRADEDATE")}
        require(d>=0 && lines.hasNext()){"NO_DATED_DISCLOSURES"}
        val row=cells(lines.next())
        require(row.size==columns.size){"BAD_ROW_WIDTH"}
        val reported=date(row[d])?:error("BAD_TRADE_DATE")
        require(reported<=latestAllowed && reported>=latestAllowed.minusDays(7)){"OUTSIDE_RECENT_DISCLOSURE_WINDOW"}
        return parse(csv,reported,source,receivedAtMs)
    }
    fun parse(csv:String,expected:LocalDate,source:String,receivedAtMs:Long):InstitutionalReport {
        require(source in setOf("BULK","BLOCK"))
        require(csv.length in 20..2_000_000)
        val lines=csv.lineSequence().filter{it.isNotBlank()}.iterator()
        require(lines.hasNext())
        val cols=cells(lines.next().removePrefix("\uFEFF")).map(::header)
        fun column(vararg names:String):Int=cols.indexOfFirst{it in names}.also{require(it>=0){"MISSING_COLUMN_"+names.first()}}
        val d=column("DATE","TRADEDATE")
        val sy=column("SYMBOL")
        val co=column("SECURITYNAME","COMPANYNAME")
        val inv=column("CLIENTNAME","CLIENT")
        val side=column("BUYSELL","BUYORSELL")
        val qty=column("QUANTITYTRADED","QUANTITY")
        val price=column("TRADEPRICEWGHTAVGPRICE","TRADEPRICEWEIGHTEDAVGPRICE","TRADEPRICE","WEIGHTEDAVERAGEPRICE","TRADEPRICEWEIGHTEDAVERAGEPRICE","TRADEPRICEWGHTAVERAGEPRICE","TRADEPRICEWGHTAVGPRICE","TRADEPRICEWGHTDAVGPRICE","TRADEPRICEWEIGHTEDAVGPRICE","TRADEPRICEWGHTDAVERAGEPRICE","TRADEPRICEWAVGPRICE","PRICE")
        val out=mutableListOf<InstitutionalDeal>()
        val seen=hashSetOf<List<String>>()
        var rows=0;var unknown=0;var duplicates=0
        while(lines.hasNext()){
            val row=cells(lines.next())
            require(row.size==cols.size){"BAD_ROW_WIDTH"}
            require(date(row[d])==expected){"STALE_OR_MIXED_DATE"}
            val symbol=norm(row[sy]);val name=norm(row[inv])
            require(symbol.matches(Regex("[A-Z0-9&._-]{1,32}")) && name.isNotBlank())
            val direction=when(norm(row[side])){"BUY","B"->"BUY";"SELL","S"->"SELL";else->error("BAD_SIDE")}
            val q=row[qty].replace(",","").toLongOrNull()?:error("BAD_QUANTITY")
            val p=row[price].replace(",","").toBigDecimalOrNull()?:error("BAD_PRICE")
            require(q>0 && p>BigDecimal.ZERO && p.toDouble().isFinite() && (p.toDouble()*q).isFinite())
            rows++;require(rows<=10000)
            val key=listOf(symbol,name,direction,q.toString(),p.stripTrailingZeros().toPlainString())
            // Identical disclosed sides are conservatively counted once within a file.
            if(!seen.add(key)){duplicates++;continue}
            val cat=category(name) ?: "UNCLASSIFIED"
            if(cat=="UNCLASSIFIED")unknown++
            out+=InstitutionalDeal(expected.toString(),symbol,row[co],name,cat,direction,q,p.toDouble())
        }
        // Header-only files have no verifiable trade date; never overwrite a dated snapshot.
        require(rows>0){"NO_DATED_DISCLOSURES"}
        return InstitutionalReport(expected.toString(),source,receivedAtMs,rows,unknown,duplicates,out,true)
    }
    fun includes(filter:String,category:String):Boolean = when(filter){
        "DISCLOSED" -> true
        "ALL" -> category=="FII" || category=="DII"
        else -> filter==category
    }
    fun top(report:InstitutionalReport?,side:String,filter:String):List<InstitutionalStock> {
        require(side in setOf("BUY","SELL") && filter in setOf("ALL","FII","DII","DISCLOSED","UNCLASSIFIED"))
        return report?.deals.orEmpty().filter{it.side==side && includes(filter,it.category)}
            .groupBy{it.symbol}.map{(symbol,deals)->
                val f=deals.filter{it.category=="FII"}.sumOf{it.shares*it.price}
                val d=deals.filter{it.category=="DII"}.sumOf{it.shares*it.price}
                InstitutionalStock(symbol,deals.first().company,deals.sumOf{it.shares},deals.sumOf{it.shares*it.price},f,d,
                    deals.map{it.investor}.distinct().sorted())
            }.sortedWith(compareByDescending<InstitutionalStock>{it.amountRupees}.thenBy{it.symbol}).take(TOP)
    }
}
