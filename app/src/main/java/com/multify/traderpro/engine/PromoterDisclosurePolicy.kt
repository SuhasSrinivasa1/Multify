package com.multify.traderpro.engine

import com.google.gson.JsonParser
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Only dated, explicitly identified promoter cash-equity market transactions.
 * No gifts, pledges, ESOPs, transfers or estimated holdings changes enter net flow. */
object PromoterDisclosurePolicy {
    private fun date(s:String):LocalDate? {
        val v=s.trim().takeWhile{it!=' ' && it!='T'}
        return listOf("dd-MMM-uuuu","dd-MM-uuuu","uuuu-MM-dd","dd/MM/uuuu")
            .firstNotNullOfOrNull{pattern->runCatching{
                LocalDate.parse(v,DateTimeFormatter.ofPattern(pattern,Locale.ENGLISH))
            }.getOrNull()}
    }
    fun parse(json:String,from:LocalDate,through:LocalDate,received:Long):List<InstitutionalReport> {
        require(json.length in 2..4_000_000 && from<=through)
        val root=JsonParser.parseString(json)
        val array=if(root.isJsonArray)root.asJsonArray else root.asJsonObject.getAsJsonArray("data")
            ?:error("PIT_RESPONSE_SCHEMA_UNRECOGNIZED")
        require(array.size()<=10000)
        val rows=mutableListOf<Pair<String,InstitutionalDeal>>()
        var recognizable=0
        for(element in array){
            if(!element.isJsonObject)continue
            val j=element.asJsonObject
            fun field(k:String)=runCatching{j.get(k).asString.trim()}.getOrDefault("")
            if(j.has("symbol") && j.has("personCategory") && j.has("acqMode"))recognizable++
            val role=field("personCategory").uppercase(Locale.ROOT)
            if(role !in setOf("PROMOTER","PROMOTER GROUP","MEMBER OF PROMOTER GROUP","MEMBER OF THE PROMOTER GROUP","PROMOTER AND DIRECTOR"))continue
            if(field("secType").uppercase(Locale.ROOT) !in setOf("EQUITY","EQUITY SHARES","EQUITY SHARE"))continue
            val mode=field("acqMode").uppercase(Locale.ROOT)
            val direction=field("tdpTransactionType").uppercase(Locale.ROOT)
            val side=when(direction){
                "BUY","PURCHASE"->"BUY"
                "SELL","SALE"->"SELL"
                else->continue
            }
            if(mode !in setOf("MARKET PURCHASE","MARKET SALE","MARKET SELL","ON MARKET","ON-MARKET","OPEN MARKET"))continue
            val filing=date(field("date"))?:continue
            val start=date(field("acqfromDt"))?:continue
            val end=date(field("acqtoDt"))?:continue
            if(filing !in from..through || start>end || end>filing)continue
            val symbol=field("symbol").uppercase(Locale.ROOT)
            val name=field("acqName").uppercase(Locale.ROOT)
            val q=field("secAcq").replace(",","").toLongOrNull()?:continue
            val value=field("secVal").replace(",","").toBigDecimalOrNull()?.toDouble()?:continue
            if(!symbol.matches(Regex("[A-Z0-9&._-]{1,32}"))||name.isBlank()||q<=0||!value.isFinite()||value<=0)continue
            val id=field("pid").takeIf{it.isNotBlank()}?.let{it+"|"+field("did")}
            rows+=filing.toString() to InstitutionalDeal(end.toString(),symbol,field("company"),name,"PROMOTER",side,q,value/q,
                reportedValue=value,tradeFrom=start.toString(),filingId=id)
        }
        require(array.size()==0 || recognizable>0){"PIT_RESPONSE_FIELDS_UNRECOGNIZED"}
        // Identical economics repeated across filing IDs are retained once; use newest filing date.
        val unique=rows.sortedByDescending{it.first}.distinctBy{(_,d)->
            listOf(d.symbol,d.investor,d.side,d.tradeFrom,d.day,d.shares.toString(),d.reportedValue.toString())}
        return unique.groupBy{it.first}.map{(filing,items)->
            InstitutionalReport(filing,"PROMOTER",received,items.size,0,0,items.map{it.second},true)
        }
    }
}
