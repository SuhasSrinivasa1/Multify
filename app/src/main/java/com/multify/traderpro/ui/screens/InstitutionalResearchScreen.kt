package com.multify.traderpro.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.multify.traderpro.engine.InstitutionalHistoryPolicy
import com.multify.traderpro.data.repository.LargeIndexResearchPolicy
import java.util.Locale

private fun crores(value:Double):String = String.format(Locale.US,"₹%,.2f Cr",value/10_000_000.0)

@Composable
internal fun InstitutionalResearchScreen(state:TraderUiState,onRefresh:()->Unit) {
    var side by rememberSaveable { mutableStateOf("BUY") }
    var filter by rememberSaveable { mutableStateOf("DISCLOSED") }
    var source by rememberSaveable { mutableStateOf("BULK") }
    val snapshot=state.equityResearch.institutional
    val report=if(source=="BULK")snapshot.bulk else snapshot.block
    var days by rememberSaveable { mutableStateOf(1) }
    var sector by rememberSaveable { mutableStateOf("") }
    val sectors=remember(snapshot.sectors){snapshot.sectors.values.filter{it.isNotBlank()}.distinct().sorted()}
    val ranked=remember(snapshot,source,side,filter,days,sector){
        InstitutionalHistoryPolicy.window(snapshot,source,side,filter,days,sector.takeIf{it.isNotBlank()})
    }
    val rows=ranked.rows
    val hourly=snapshot.hourly
    val now=java.time.ZonedDateTime.now(java.time.ZoneId.of("Asia/Kolkata"))
    val today=now.toLocalDate().toString()
    var selectedHourDay by rememberSaveable { mutableStateOf("") }
    val hourlyDays=remember(snapshot.hourlyHistory,today){(snapshot.hourlyHistory.map{it.day}+today).distinct().sortedDescending()}
    val hourDay=selectedHourDay.takeIf{it in hourlyDays}?:today
    val hourlyBySlot=remember(snapshot.hourlyHistory,hourDay){snapshot.hourlyHistory.filter{it.day==hourDay}.associateBy{it.hour}}
    val ageMinutes=hourly?.let{(System.currentTimeMillis()-it.receivedAtMs)/60000}
    val currentHour=hourly!=null && hourly.day==now.toLocalDate().toString() &&
        hourly.hour==now.hour && ageMinutes!=null && ageMinutes in 0..60 && now.hour in 10..15
    LazyColumn(
        modifier=Modifier.fillMaxSize().testTag("institutional-screen"),
        contentPadding=PaddingValues(14.dp),
        verticalArrangement=Arrangement.spacedBy(10.dp)
    ){
        item{
            Text("Institutional Intelligence",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
            Text("Top 20 NSE disclosed gross transaction values · research only · no orders",style=MaterialTheme.typography.bodySmall)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                if(side=="BUY") Button(onClick={side="BUY"},modifier=Modifier.weight(1f).testTag("institutional-buys")){Text("Buys")}
                else OutlinedButton(onClick={side="BUY"},modifier=Modifier.weight(1f).testTag("institutional-buys")){Text("Buys")}
                if(side=="SELL") Button(onClick={side="SELL"},modifier=Modifier.weight(1f).testTag("institutional-sells")){Text("Sells")}
                else OutlinedButton(onClick={side="SELL"},modifier=Modifier.weight(1f).testTag("institutional-sells")){Text("Sells")}
            }
            Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){
                FilterChip(selected=filter=="DISCLOSED",onClick={filter="DISCLOSED"},label={Text("All disclosures")})
                FilterChip(selected=filter=="UNCLASSIFIED",onClick={filter="UNCLASSIFIED"},label={Text("Unclassified")})
            }
            Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){
                FilterChip(selected=filter=="ALL",onClick={filter="ALL"},label={Text("Verified FII+DII")})
                FilterChip(selected=filter=="FII",onClick={filter="FII"},label={Text("FII")})
                FilterChip(selected=filter=="DII",onClick={filter="DII"},label={Text("DII")})
            }
            Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){
                FilterChip(selected=source=="BULK",onClick={source="BULK"},label={Text("Bulk deals")})
                FilterChip(selected=source=="BLOCK",onClick={source="BLOCK"},label={Text("Block deals")})
            }
            Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){
                for(n in listOf(1,5,10,30)){
                    FilterChip(selected=days==n,onClick={days=n},label={Text("${n}D")})
                }
            }
            var chooseSector by remember{mutableStateOf(false)}
            Box {
                OutlinedButton(onClick={chooseSector=true}){Text(sector.ifBlank{"All sectors"})}
                DropdownMenu(expanded=chooseSector,onDismissRequest={chooseSector=false}){
                    DropdownMenuItem(text={Text("All sectors")},onClick={sector="";chooseSector=false})
                    for(name in sectors)DropdownMenuItem(text={Text(name)},onClick={sector=name;chooseSector=false})
                }
            }
            Text("Coverage: ${ranked.reports}/${ranked.requested} requested session disclosures · through ${ranked.through.ifBlank{"unknown"}}. Missing/undated reports are not zero-flow days.",style=MaterialTheme.typography.bodySmall)
            Text("Nifty EOD ${snapshot.contextDay.ifBlank{"unknown"}}: ${snapshot.niftyEodPct?.let{String.format(Locale.US,"%+.2f%%",it)}?:"unavailable"} · not live index data.",style=MaterialTheme.typography.bodySmall)
            Text(if(hourly==null)"Hourly Groww price observation unavailable." else
                "Groww sweep price snapshot: ${hourly.day} ${String.format(Locale.US,"%02d:00",hourly.hour)} slot · client receipt ${java.time.Instant.ofEpochMilli(hourly.receivedAtMs).atZone(java.time.ZoneId.of("Asia/Kolkata")).toLocalTime().toString().take(8)} IST · ${if(currentHour)"current hour" else "older observation"} · not live institutional flow.",style=MaterialTheme.typography.bodySmall)
            val todaySlots=snapshot.hourlyHistory.filter{it.day==now.toLocalDate().toString()}.map{it.hour}.toSet()
            Text("Today's captured hours: ${todaySlots.sorted().joinToString(", ").ifBlank{"none"}} · missing hours are not backfilled.",style=MaterialTheme.typography.labelSmall)
            var showHourDays by remember{mutableStateOf(false)}
            Box {
                OutlinedButton(onClick={showHourDays=true}){Text("Hourly prices: $hourDay")}
                DropdownMenu(expanded=showHourDays,onDismissRequest={showHourDays=false}){
                    hourlyDays.forEach{d->DropdownMenuItem(text={Text(d)},onClick={selectedHourDay=d;showHourDays=false})}
                }
            }
            Text("Six hourly slots · first eligible sweep per hour · actual observation times, not exact-hour quotes. Up to 60 snapshots kept.",style=MaterialTheme.typography.labelSmall)
            Text("Bulk and block are separate: overlapping reports must not be summed. FII/DII means only verified named clients matched to issuer filings or AMFI directory. All disclosures includes individuals and other unclassified clients; not all are institutions.",style=MaterialTheme.typography.bodySmall)
            if(report!=null){
                Text("Disclosure date: ${report.day} · ${report.source} · ${report.disclosedRows} reported rows; ${report.deals.count{it.category=="FII"||it.category=="DII"}} verified, ${report.unmatchedRows} unclassified, ${report.duplicateRows} identical rows excluded.",style=MaterialTheme.typography.bodySmall)
                val verified=report.deals.count{it.category=="FII"||it.category=="DII"}
                val denominator=(report.disclosedRows-report.duplicateRows).coerceAtLeast(1)
                Text("Investor verification coverage: ${String.format(Locale.US,"%.1f%%",100.0*verified/denominator)} · ${if(report.retainedAllRows)"all unique rows retained" else "legacy incomplete cache — reimport required"}",style=MaterialTheme.typography.bodySmall)
                if(report.day!=state.equityResearch.latestDay)
                    Text("Older cached disclosure — latest saved exchange EOD is ${state.equityResearch.latestDay.ifBlank{"unknown"}}.",color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.labelSmall)
            }else Text("No dated, validated ${source.lowercase(Locale.US)} disclosure cached. Empty or unavailable reports do not imply zero institutional activity.",style=MaterialTheme.typography.bodySmall)
            if(snapshot.history.isEmpty() && snapshot.bulk==null && snapshot.block==null)
                Text("No validated bulk/block disclosure is cached, so there are no issuer symbols to sample hourly yet. Hourly prices begin only after a dated report is imported and are copied from completed Fixed sweeps; no extra Groww requests are made.",style=MaterialTheme.typography.labelSmall)
            Button(onClick=onRefresh,enabled=!state.researchScanning,modifier=Modifier.fillMaxWidth()){
                Text(if(state.researchScanning)"Refreshing…" else "Refresh institutional research")
            }
            Text("NSE imports only 18:30–08:00 IST; hourly prices reuse the first eligible completed Fixed sweep in each 10–15 IST clock hour. Missed hours remain missing; no extra Groww requests. Investor identity coverage is partial; all unique disclosures are retained regardless of identity.",style=MaterialTheme.typography.labelSmall)
            Text("Top ${rows.size} of 20 · ${if(side=="BUY")"purchases" else "sales"}",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
            if(rows.isEmpty())Text("No disclosed stocks match this selection. If report coverage is incomplete, missing reports are not zero activity. Try All disclosures.",style=MaterialTheme.typography.bodySmall)
        }
        itemsIndexed(rows,key={_,r->r.symbol}){index,row->
            Card(Modifier.fillMaxWidth()){
                Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){
                    Text("${index+1}. ${row.symbol}",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
                    if(row.company.isNotBlank())Text(row.company,style=MaterialTheme.typography.bodySmall)
                    Text(crores(if(side=="BUY")row.grossBuy else row.grossSell),style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
                    Text("Verified FII ${crores(row.fiiSide)} · Verified DII ${crores(row.diiSide)} · Unclassified ${crores(((if(side=="BUY")row.grossBuy else row.grossSell)-row.fiiSide-row.diiSide).coerceAtLeast(0.0))}",style=MaterialTheme.typography.bodySmall)
                    Text("Gross buys ${crores(row.grossBuy)} · gross sells ${crores(row.grossSell)} · net ${crores(row.net)}",style=MaterialTheme.typography.bodySmall)
                    Text("${String.format(Locale.US,"%,d",row.sideShares)} disclosed shares · ${row.investors.size} named clients · ${row.buyDays} buy dates · ${row.repeatedBuyers} repeated buyers",style=MaterialTheme.typography.bodySmall)
                    val ref=if(side=="BUY")row.weightedBuy else row.weightedSell
                    val price=hourly?.prices?.get(row.symbol)
                    val change=InstitutionalHistoryPolicy.movement(price,ref)
                    Text("Weighted disclosed ${if(side=="BUY")"buy" else "sell"} price: ${ref?.let{String.format(Locale.US,"₹%.2f",it)}?:"unavailable"} · sweep price: ${price?.let{String.format(Locale.US,"₹%.2f",it)}?:"unavailable"} · difference ${change?.let{String.format(Locale.US,"%+.2f%%",it)}?:"unavailable"}",style=MaterialTheme.typography.bodySmall)
                    var showHours by rememberSaveable { mutableStateOf(false) }
                    TextButton(onClick={showHours=!showHours}){Text(if(showHours)"Hide hourly prices" else "Show 10:00–15:00 price observations")}
                    if(showHours)for(h in 10..15){
                        val sample=hourlyBySlot[h]
                        val p=sample?.prices?.get(row.symbol)
                        val observed=sample?.let{java.time.Instant.ofEpochMilli(it.receivedAtMs).atZone(java.time.ZoneId.of("Asia/Kolkata")).toLocalTime().toString().take(5)}
                        Text(String.format(Locale.US,"%02d:00 slot",h)+" · "+(p?.let{String.format(Locale.US,"₹%.2f",it)}?:"unavailable")+(if(p!=null && observed!=null)" · observed $observed IST" else ""),style=MaterialTheme.typography.labelSmall)
                    }
                    val industry=snapshot.sectors[row.symbol]
                    val index=industry?.let{LargeIndexResearchPolicy.sectorIndex(it)}
                    val sectorMove=index?.let{snapshot.sectorMoves[it]}
                    Text("Sector: ${industry?:"unmapped"} · EOD ${sectorMove?.let{String.format(Locale.US,"%+.2f%%",it)}?:"unavailable"} · ${if(row.symbol in snapshot.swing)"Swing Watch · " else ""}${if(row.symbol in snapshot.lows)"Adaptive Lows" else "No Lows overlap"}",style=MaterialTheme.typography.labelSmall)
                    val outcome=snapshot.outcomes.filter{it.source==source&&it.symbol==row.symbol}.maxByOrNull{it.day}
                    if(outcome!=null)Text("Prior disclosure ${outcome.day}: subsequent 5-session close return ${String.format(Locale.US,"%+.2f%%",outcome.returnPct)} (${outcome.baselineDay}–${outcome.outcomeDay}); unadjusted research only.",style=MaterialTheme.typography.labelSmall)
                    Text(row.investors.take(3).joinToString(" · ")+(if(row.investors.size>3)" · +${row.investors.size-3} more" else ""),style=MaterialTheme.typography.labelSmall)
                }
            }
        }
        item{
            var showSectorTotals by rememberSaveable{mutableStateOf(false)}
            OutlinedButton(onClick={showSectorTotals=!showSectorTotals},modifier=Modifier.fillMaxWidth()){
                Text(if(showSectorTotals)"Hide sector net flow" else "Show top sector net flow")
            }
            if(showSectorTotals){
                val totals=remember(snapshot,source,filter,days){InstitutionalHistoryPolicy.sectorTotals(snapshot,source,filter,days)}
                Text("Sector totals include all disclosures matching the chosen investor filter, not just the 20 displayed stocks. Unmapped is not a sector.",style=MaterialTheme.typography.labelSmall)
                totals.forEach{v->Text("${v.sector}: net ${crores(v.net)} · buys ${crores(v.buys)} · sells ${crores(v.sells)}",style=MaterialTheme.typography.bodySmall)}
            }
        }
        item{
            Text("Source: NSE ${source.lowercase(Locale.US)} deal disclosure. Client classification: selected named FPI issuer disclosures and AMFI fund directory. No complete stock-wise FII/DII flow or investment advice is claimed. Zero in a category means no verified rows in this report. Unclassified clients may include individuals, firms or institutions.",style=MaterialTheme.typography.labelSmall)
        }
    }
}
