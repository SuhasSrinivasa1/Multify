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
    var filter by rememberSaveable { mutableStateOf("ALL") }
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
                FilterChip(selected=filter=="ALL",onClick={filter="ALL"},label={Text("All disclosed")})
                FilterChip(selected=filter=="VERIFIED",onClick={filter="VERIFIED"},label={Text("Verified FII+DII")})
            }
            Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){
                FilterChip(selected=filter=="FII",onClick={filter="FII"},label={Text("FII")})
                FilterChip(selected=filter=="DII",onClick={filter="DII"},label={Text("DII")})
                FilterChip(selected=filter=="UNCLASSIFIED",onClick={filter="UNCLASSIFIED"},label={Text("Unclassified")})
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
            Text("Bulk and block are separate: overlapping reports must not be summed. FII/DII means only exact named clients matched to issuer filings or AMFI directory, not all institutional trades.",style=MaterialTheme.typography.bodySmall)
            if(report!=null){
                Text("Disclosure date: ${report.day} · ${report.source} · ${report.disclosedRows} reported rows; ${report.matchedRows} verified FII/DII, ${report.unmatchedRows} unclassified, ${report.duplicateRows} identical rows excluded; ${report.retainedRows} unique rows retained.",style=MaterialTheme.typography.bodySmall)
                if(report.schemaVersion<2)
                    Text("LEGACY CACHE: v9.0.47 did not retain unmatched rows. The same NSE report will be re-imported once during the next 18:30–08:00 IST refresh; all-disclosed rankings may remain incomplete until then.",color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.labelSmall)
                if(report.day!=state.equityResearch.latestDay)
                    Text("Older cached disclosure — latest saved exchange EOD is ${state.equityResearch.latestDay.ifBlank{"unknown"}}.",color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.labelSmall)
            }else Text("No dated, validated ${source.lowercase(Locale.US)} disclosure cached. Empty or unavailable reports do not imply zero institutional activity.",style=MaterialTheme.typography.bodySmall)
            Button(onClick=onRefresh,enabled=!state.researchScanning,modifier=Modifier.fillMaxWidth()){
                Text(if(state.researchScanning)"Refreshing…" else "Refresh institutional research")
            }
            Text("NSE imports only 18:30–08:00 IST; hourly prices reuse the first completed Fixed sweep in the first 20 minutes of each 10–15 IST clock hour. Missed hours remain missing; no extra Groww requests. Investor identity coverage is partial.",style=MaterialTheme.typography.labelSmall)
            Text("Top ${rows.size} of 20 · ${if(side=="BUY")"purchases" else "sales"}",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
            if(rows.isEmpty())Text("No matched disclosed stocks in this selection. This is not evidence of no market buying or selling.",style=MaterialTheme.typography.bodySmall)
        }
        itemsIndexed(rows,key={_,r->r.symbol}){index,row->
            Card(Modifier.fillMaxWidth()){
                Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){
                    Text("${index+1}. ${row.symbol}",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
                    if(row.company.isNotBlank())Text(row.company,style=MaterialTheme.typography.bodySmall)
                    Text(crores(if(side=="BUY")row.grossBuy else row.grossSell),style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
                    Text("FII ${crores(row.fiiSide)} · DII ${crores(row.diiSide)} · Unclassified ${crores(row.unclassifiedSide)}",style=MaterialTheme.typography.bodySmall)
                    Text("Gross buys ${crores(row.grossBuy)} · gross sells ${crores(row.grossSell)} · net ${crores(row.net)}",style=MaterialTheme.typography.bodySmall)
                    Text("${String.format(Locale.US,"%,d",row.sideShares)} disclosed shares · ${row.investors.size} matched names · ${row.buyDays} buy dates · ${row.repeatedBuyers} repeated buyers",style=MaterialTheme.typography.bodySmall)
                    val ref=if(side=="BUY")row.weightedBuy else row.weightedSell
                    val price=hourly?.prices?.get(row.symbol)
                    val change=InstitutionalHistoryPolicy.movement(price,ref)
                    Text("Weighted disclosed ${if(side=="BUY")"buy" else "sell"} price: ${ref?.let{String.format(Locale.US,"₹%.2f",it)}?:"unavailable"} · sweep price: ${price?.let{String.format(Locale.US,"₹%.2f",it)}?:"unavailable"} · difference ${change?.let{String.format(Locale.US,"%+.2f%%",it)}?:"unavailable"}",style=MaterialTheme.typography.bodySmall)
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
                Text("Sector totals include all matched disclosures, not just the 20 displayed stocks. Unmapped is not a sector.",style=MaterialTheme.typography.labelSmall)
                totals.forEach{v->Text("${v.sector}: net ${crores(v.net)} · buys ${crores(v.buys)} · sells ${crores(v.sells)}",style=MaterialTheme.typography.bodySmall)}
            }
        }
        item{
            Text("Source: NSE ${source.lowercase(Locale.US)} deal disclosure. Every valid unique row is retained. FII/DII filters include only exact verified identities; unclassified clients remain visible separately. This is not complete stock-wise institutional flow or investment advice.",style=MaterialTheme.typography.labelSmall)
        }
    }
}
