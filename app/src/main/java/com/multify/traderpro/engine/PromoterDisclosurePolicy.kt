package com.multify.traderpro.engine

import com.google.gson.JsonParser
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory
import org.xml.sax.InputSource
import java.io.StringReader

data class PromoterFiling(val id:String,val previousId:String,val symbol:String,val company:String,
    val filingDay:String,val xmlUrl:String)
data class PromoterFilingResult(val filing:PromoterFiling,val receivedAtMs:Long,val deals:List<InstitutionalDeal>)

/** Published PIT XBRL filings only. No holdings estimates, pledges, gifts or off-market transfers. */
object PromoterDisclosurePolicy {
    private fun date(s:String):LocalDate? {
        val v=s.trim().takeWhile{it!=' ' && it!='T'}
        return listOf("dd-MMM-uuuu","dd-MM-uuuu","uuuu-MM-dd","dd/MM/uuuu")
            .firstNotNullOfOrNull{pattern->runCatching{
                LocalDate.parse(v,DateTimeFormatter.ofPattern(pattern,Locale.ENGLISH))
            }.getOrNull()}
    }
    fun parseList(json:String,from:LocalDate,through:LocalDate):List<PromoterFiling>{
        require(json.length in 2..4_000_000 && from<=through)
        val root=JsonParser.parseString(json).asJsonObject
        val array=root.getAsJsonArray("data")?:error("PIT_RESPONSE_SCHEMA_UNRECOGNIZED")
        require(array.size()<=10000)
        val rows=array.mapNotNull{element->
            val j=element.asJsonObject
            fun f(k:String)=runCatching{j.get(k).asString.trim()}.getOrDefault("")
            val id=f("appId");val symbol=f("symbol").uppercase(Locale.ROOT)
            val day=date(f("broadcastDateTime"))?:return@mapNotNull null
            val url=f("xmlFileName")
            val uri=runCatching{java.net.URI(url)}.getOrNull()?:return@mapNotNull null
            if(id.isBlank()||!symbol.matches(Regex("[A-Z0-9&._-]{1,32}"))||day !in from..through ||
                uri.scheme!="https"||uri.host!="nsearchives.nseindia.com"||uri.userInfo!=null||
                (uri.port!=-1&&uri.port!=443)||!uri.path.startsWith("/corporate/")||
                !uri.path.endsWith(".xml",true))return@mapNotNull null
            val previous=f("prevAppId").takeUnless{it in setOf("","0","-","NA","null")} .orEmpty()
            PromoterFiling(id,previous,symbol,f("companyName"),day.toString(),url)
        }
        require(array.size()==0 || rows.isNotEmpty()){"PIT_RESPONSE_FIELDS_UNRECOGNIZED"}
        return rows.distinctBy{it.id}
    }
    fun parseXml(filing:PromoterFiling,xml:String,received:Long):PromoterFilingResult {
        require(xml.length in 1..2_000_000 && !xml.contains("<!DOCTYPE",true) && !xml.contains("<!ENTITY",true))
        val factory=DocumentBuilderFactory.newInstance().apply{isNamespaceAware=true;isExpandEntityReferences=false}
        val builder=factory.newDocumentBuilder()
        builder.setEntityResolver{_,_->throw org.xml.sax.SAXException("External XML entity rejected")}
        val doc=builder.parse(InputSource(StringReader(xml)))
        val contexts=linkedMapOf<String,MutableMap<String,String>>()
        val nodes=doc.getElementsByTagName("*")
        for(i in 0 until nodes.length){
            val e=nodes.item(i) as? org.w3c.dom.Element?:continue
            val ctx=e.getAttribute("contextRef")
            if(ctx.startsWith("Disclosure"))contexts.getOrPut(ctx){linkedMapOf()}[e.localName?:e.tagName]=e.textContent.trim()
        }
        require(contexts.isNotEmpty()){"PIT_XML_NO_TRANSACTION_CONTEXT"}
        val rows=contexts.values.mapNotNull{f->
            fun field(k:String)=f[k].orEmpty()
            val role=field("CategoryOfPerson").uppercase(Locale.ROOT)
            if(role !in setOf("PROMOTER","PROMOTER GROUP","MEMBER OF PROMOTER GROUP","MEMBER OF THE PROMOTER GROUP","PROMOTER AND DIRECTOR"))return@mapNotNull null
            val type=field("TypeOfInstrument").ifBlank{field("TypeOfSecurity")}.uppercase(Locale.ROOT)
            if(type !in setOf("EQUITY","EQUITY SHARES","EQUITY SHARE"))return@mapNotNull null
            val mode=field("ModeOfAcquisitionOrDisposal").uppercase(Locale.ROOT)
            if(mode !in setOf("MARKET PURCHASE","MARKET SALE","MARKET SELL","ON MARKET","ON-MARKET","OPEN MARKET"))return@mapNotNull null
            val side=when(field("SecuritiesAcquiredOrDisposedTransactionType").uppercase(Locale.ROOT)){
                "BUY","PURCHASE"->"BUY";"SELL","SALE"->"SELL";else->return@mapNotNull null
            }
            val start=date(field("DateOfAllotmentAdviceOrAcquisitionOfSharesOrSaleOfSharesSpecifyFromDate"))?:return@mapNotNull null
            val end=date(field("DateOfAllotmentAdviceOrAcquisitionOfSharesOrSaleOfSharesSpecifyToDate"))?:return@mapNotNull null
            if(start>end||end.toString()>filing.filingDay)return@mapNotNull null
            val name=field("NameOfThePerson").uppercase(Locale.ROOT)
            val q=field("SecuritiesAcquiredOrDisposedNumberOfSecurity").replace(",","").toLongOrNull()?:return@mapNotNull null
            val amount=field("SecuritiesAcquiredOrDisposedValueOfSecurity").replace(",","").toDoubleOrNull()?:return@mapNotNull null
            if(name.isBlank()||q<=0||amount<=0||!amount.isFinite())return@mapNotNull null
            InstitutionalDeal(end.toString(),filing.symbol,filing.company,name,"PROMOTER",side,q,amount/q,
                reportedValue=amount,tradeFrom=start.toString(),filingId=filing.id)
        }.distinct()
        return PromoterFilingResult(filing,received,rows)
    }
    fun reports(results:List<PromoterFilingResult>,superseded:Set<String>):List<InstitutionalReport>{
        return results.filter{it.filing.id !in superseded}.groupBy{it.filing.filingDay}.map{(day,files)->
            val rows=files.flatMap{it.deals}.distinctBy{listOf(it.symbol,it.investor,it.side,it.tradeFrom,it.day,it.shares,it.reportedValue)}
            InstitutionalReport(day,"PROMOTER",files.maxOf{it.receivedAtMs},rows.size,0,0,rows,true)
        }
    }
}
