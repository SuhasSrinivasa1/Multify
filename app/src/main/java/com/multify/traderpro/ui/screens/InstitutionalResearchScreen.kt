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
internal fun InstitutionalResearchScreen(state:TraderUiState,onRefresh:()->Unit,viewModel:TraderViewModel?=null) {
    var showNews by rememberSaveable{mutableStateOf(false)}
    Column(Modifier.fillMaxSize()) {
        if(viewModel!=null)Row(Modifier.fillMaxWidth().padding(horizontal=12.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){
            FilterChip(selected=!showNews,onClick={showNews=false},label={Text("FII / DII")})
            FilterChip(selected=showNews,onClick={showNews=true},label={Text("Live news")})
        }
        Box(Modifier.weight(1f)){
            if(showNews&&viewModel!=null)LiveNewsScreen(viewModel)
            else InstitutionalDisclosuresContent(state,onRefresh)
        }
    }
}

@Composable
private fun InstitutionalDisclosuresContent(state:TraderUiState,onRefresh:()->Unit) {
    var side by rememberSaveable { mutableStateOf("BUY") }
    var filter by rememberSaveable { mutableStateOf("DISCLOSED") }
    var source by rememberSaveable { mutableStateOf("COMBINED") }
    val snapshot=state.equityResearch.institutional
    val report=when(source){"BULK"->snapshot.bulk;"BLOCK"->snapshot.block;"PROMOTER"->snapshot.promoter;else->null}
    var days by rememberSaveable { mutableStateOf(5) }
    var sector by rememberSaveable { mutableStateOf("") }
    val sectors=remember(snapshot.sectors){snapshot.sectors.values.filter{it.isNotBlank()}.distinct().sorted()}
    val ranked=remember(snapshot,source,side,filter,days,sector){
        InstitutionalHistoryPolicy.window(snapshot,source,side,filter,days,sector.takeIf{it.isNotBlank()},limit=10)
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
            Text("Ownership Intelligence",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
            Text("Top 10 net disclosed purchases / sales · research only · no orders",style=MaterialTheme.typography.bodySmall)
            val allRows=listOfNotNull(snapshot.bulk,snapshot.block).flatMap{it.deals}
            val verifiedRows=allRows.filter{it.category=="FII"||it.category=="DII"}
            Text("Latest cached bulk/block: "+verifiedRows.size+" verified / "+allRows.size+" raw rows · "+verifiedRows.map{it.symbol}.distinct().size+" verified stocks · overlaps possible. Unknown investors are not assumed institutional. Full FPI registry coverage pending.",style=MaterialTheme.typography.bodySmall)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                if(side=="BUY") Button(onClick={side="BUY"},modifier=Modifier.weight(1f).testTag("institutional-buys")){Text("Buys")}
                else OutlinedButton(onClick={side="BUY"},modifier=Modifier.weight(1f).testTag("institutional-buys")){Text("Buys")}
                if(side=="SELL") Button(onClick={side="SELL"},modifier=Modifier.weight(1f).testTag("institutional-sells")){Text("Sells")}
                else OutlinedButton(onClick={side="SELL"},modifier=Modifier.weight(1f).testTag("institutional-sells")){Text("Sells")}
            }
            Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){
                FilterChip(selected=filter=="OWNERSHIP",onClick={filter="OWNERSHIP"},label={Text("All verified owners")})
                FilterChip(selected=filter=="MF",onClick={filter="MF"},label={Text("Mutual funds")})
            }
            Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){
                FilterChip(selected=filter=="PROMOTER",onClick={filter="PROMOTER"},label={Text("Promoters")})
                FilterChip(selected=filter=="DISCLOSED",onClick={filter="DISCLOSED"},label={Text("All disclosures")})
                FilterChip(selected=filter=="UNCLASSIFIED",onClick={filter="UNCLASSIFIED"},label={Text("Unclassified")})
            }
            Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){
                FilterChip(selected=filter=="ALL",onClick={filter="ALL"},label={Text("Verified FII+DII")})
                FilterChip(selected=filter=="FII",onClick={filter="FII"},label={Text("FII")})
                FilterChip(selected=filter=="DII",onClick={filter="DII"},label={Text("Other DII")})
            }
            Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){
                FilterChip(selected=source=="COMBINED",onClick={source="COMBINED"},label={Text("Combined")})
                FilterChip(selected=source=="PROMOTER",onClick={source="PROMOTER"},label={Text("PIT filings")})
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
            Text("Window: ${ranked.requested} sessions · through ${ranked.through.ifBlank{"unknown"}} · dated reports by source: ${ranked.sourceCoverage.entries.joinToString { it.key+": "+it.value }}. Missing reports are not zero-flow days.",style=MaterialTheme.typography.bodySmall)
            Text("Bulk trade date: ${snapshot.bulk?.day?:"unavailable"} · block trade date: ${snapshot.block?.day?:"unavailable"} · latest parsed promoter filing: ${snapshot.promoter?.day?:"unavailable"}. Promoter filing date can differ from transaction date.",style=MaterialTheme.typography.labelSmall)
            Text("Promoter refresh: ${snapshot.promoterStatus} · ${if(snapshot.promoterCheckedAtMs>0)java.time.Instant.ofEpochMilli(snapshot.promoterCheckedAtMs).atZone(java.time.ZoneId.of("Asia/Kolkata")).toLocalDateTime().toString() else "not yet checked"} IST",style=MaterialTheme.typography.bodySmall)
            Text("Conservative possible cross-source overlaps excluded: ${ranked.excludedOverlaps}. Combined ranking is partial published evidence, not total market ownership flow.",style=MaterialTheme.typography.labelSmall)
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
            Text("BUY and SELL lists are mutually exclusive for the same filters: gross buys minus gross sells; balanced stocks appear in neither. Opposing investor groups can differ. Combined excludes possible overlapping transactions; source-specific views remain available. Mutual funds are separate from other DII and counted once. Promoter data is dated PIT market transactions only. FII/DII coverage is limited to verified identities. All disclosures includes individuals and other unclassified clients; not all are institutions.",style=MaterialTheme.typography.bodySmall)
            if(report!=null){
                Text("Disclosure date: ${report.day} · ${report.source} · ${report.disclosedRows} reported rows; ${report.deals.count{com.multify.traderpro.engine.InstitutionalIntelligencePolicy.matches("OWNERSHIP",it)}} verified, ${report.unmatchedRows} unclassified, ${report.duplicateRows} identical rows excluded.",style=MaterialTheme.typography.bodySmall)
                Text("Report received: ${java.time.Instant.ofEpochMilli(report.receivedAtMs).atZone(java.time.ZoneId.of("Asia/Kolkata")).toLocalDateTime().toString()} IST · disclosure date is not live price date.",style=MaterialTheme.typography.labelSmall)
                val verified=report.deals.count{it.category=="FII"||it.category=="DII"}
                val denominator=(report.disclosedRows-report.duplicateRows).coerceAtLeast(1)
                Text("Investor verification coverage: ${String.format(Locale.US,"%.1f%%",100.0*verified/denominator)} · ${if(report.retainedAllRows)"all unique rows retained" else "legacy incomplete cache — reimport required"}",style=MaterialTheme.typography.bodySmall)
                if(report.day!=state.equityResearch.latestDay)
                    Text("Older cached disclosure — latest saved exchange EOD is ${state.equityResearch.latestDay.ifBlank{"unknown"}}.",color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.labelSmall)
            }else if(source!="COMBINED") Text("No dated, validated ${source.lowercase(Locale.US)} disclosure cached. Empty or unavailable reports do not imply zero institutional activity.",style=MaterialTheme.typography.bodySmall)
            if(snapshot.history.isEmpty() && snapshot.bulk==null && snapshot.block==null)
                Text("No dated bulk/block disclosure cached. Completed Fixed sweeps can still be captured hourly; later reports may be compared with these actual earlier prices, never treated as live institutional flow.",style=MaterialTheme.typography.labelSmall)
            Button(onClick=onRefresh,enabled=!state.researchScanning,modifier=Modifier.fillMaxWidth()){
                Text(if(state.researchScanning)"Refreshing…" else "Refresh institutional research")
            }
            Text("Promoter filing checks hourly during trading, bounded off-hours catch-up every 30 minutes. Bulk/block import after hours. Prices reuse completed Fixed sweeps; no additional Groww requests. Mutual fund coverage is named exchange transactions only; full monthly scheme holdings are not imported. Neither is live stock-wise institutional flow.",style=MaterialTheme.typography.labelSmall)
            Text("Top ${rows.size} of 10 · ${if(side=="BUY")"net buying" else "net selling"}",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
            if(rows.isEmpty())Text("No net directional stocks match this selection. Balanced flows are excluded. If report coverage is incomplete, missing reports are not zero activity. Try All disclosures.",style=MaterialTheme.typography.bodySmall)
        }
        itemsIndexed(rows,key={_,r->r.symbol}){index,row->
            Card(Modifier.fillMaxWidth()){
                Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){
                    Text("${index+1}. ${row.symbol}",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
                    if(row.company.isNotBlank())Text(row.company,style=MaterialTheme.typography.bodySmall)
                    Text((if(row.net>0)"NET BUY +" else "NET SELL −")+crores(kotlin.math.abs(row.net)),style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
                    Text("Gross selected side · verified FII ${crores(row.fiiSide)} · Other DII ${crores(row.diiSide)} · Mutual funds ${crores(row.mfSide)} · Promoters ${crores(row.promoterSide)} · Unclassified ${crores(((if(side=="BUY")row.grossBuy else row.grossSell)-row.fiiSide-row.diiSide-row.mfSide-row.promoterSide).coerceAtLeast(0.0))}",style=MaterialTheme.typography.bodySmall)
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
                Text("Sector totals include all disclosures matching the chosen investor filter, not just the 10 displayed stocks. Unmapped is not a sector.",style=MaterialTheme.typography.labelSmall)
                totals.forEach{v->Text("${v.sector}: net ${crores(v.net)} · buys ${crores(v.buys)} · sells ${crores(v.sells)}",style=MaterialTheme.typography.bodySmall)}
            }
        }
        item{
            Text("Sources: NSE bulk/block files and PIT XBRL market transactions. Client classification: selected named FPI issuer disclosures and AMFI fund directory. No complete stock-wise FII/DII flow or investment advice is claimed. Zero in a category means no verified rows in this report. Unclassified clients may include individuals, firms or institutions.",style=MaterialTheme.typography.labelSmall)
        }
    }
}
