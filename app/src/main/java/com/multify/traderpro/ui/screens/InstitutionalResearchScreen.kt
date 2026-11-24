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
import com.multify.traderpro.engine.InstitutionalIntelligencePolicy
import java.util.Locale

private fun crores(value:Double):String = String.format(Locale.US,"₹%,.2f Cr",value/10_000_000.0)

@Composable
internal fun InstitutionalResearchScreen(state:TraderUiState,onRefresh:()->Unit) {
    var side by rememberSaveable { mutableStateOf("BUY") }
    var filter by rememberSaveable { mutableStateOf("ALL") }
    var source by rememberSaveable { mutableStateOf("BULK") }
    val snapshot=state.equityResearch.institutional
    val report=if(source=="BULK")snapshot.bulk else snapshot.block
    val rows=remember(report,side,filter){InstitutionalIntelligencePolicy.top(report,side,filter)}
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
                FilterChip(selected=filter=="ALL",onClick={filter="ALL"},label={Text("FII + DII")})
                FilterChip(selected=filter=="FII",onClick={filter="FII"},label={Text("FII")})
                FilterChip(selected=filter=="DII",onClick={filter="DII"},label={Text("DII")})
            }
            Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){
                FilterChip(selected=source=="BULK",onClick={source="BULK"},label={Text("Bulk deals")})
                FilterChip(selected=source=="BLOCK",onClick={source="BLOCK"},label={Text("Block deals")})
            }
            Text("Bulk and block are separate: overlapping reports must not be summed. FII/DII means only exact named clients matched to issuer filings or AMFI directory, not all institutional trades.",style=MaterialTheme.typography.bodySmall)
            if(report!=null){
                Text("Disclosure date: ${report.day} · ${report.source} · ${report.disclosedRows} reported rows; ${report.deals.size} matched, ${report.unmatchedRows} unmatched, ${report.duplicateRows} identical rows excluded.",style=MaterialTheme.typography.bodySmall)
                if(report.day!=state.equityResearch.latestDay)
                    Text("Older cached disclosure — latest saved exchange EOD is ${state.equityResearch.latestDay.ifBlank{"unknown"}}.",color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.labelSmall)
            }else Text("No dated, validated ${source.lowercase(Locale.US)} disclosure cached. Empty or unavailable reports do not imply zero institutional activity.",style=MaterialTheme.typography.bodySmall)
            Button(onClick=onRefresh,enabled=!state.researchScanning,modifier=Modifier.fillMaxWidth()){
                Text(if(state.researchScanning)"Refreshing…" else "Refresh institutional research")
            }
            Text("NSE imports only 18:30–08:00 IST; daytime refresh is local. No additional Groww calls. Investor identity coverage is partial; unknown clients are excluded, never guessed.",style=MaterialTheme.typography.labelSmall)
            Text("Top ${rows.size} of 20 · ${if(side=="BUY")"purchases" else "sales"}",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
            if(rows.isEmpty())Text("No matched disclosed stocks in this selection. This is not evidence of no market buying or selling.",style=MaterialTheme.typography.bodySmall)
        }
        itemsIndexed(rows,key={_,r->r.symbol}){index,row->
            Card(Modifier.fillMaxWidth()){
                Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){
                    Text("${index+1}. ${row.symbol}",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
                    if(row.company.isNotBlank())Text(row.company,style=MaterialTheme.typography.bodySmall)
                    Text(crores(row.amountRupees),style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
                    Text("FII ${crores(row.fiiRupees)} · DII ${crores(row.diiRupees)}",style=MaterialTheme.typography.bodySmall)
                    Text("${String.format(Locale.US,"%,d",row.shares)} disclosed shares · ${row.investors.size} matched names",style=MaterialTheme.typography.bodySmall)
                    Text(row.investors.take(3).joinToString(" · ")+(if(row.investors.size>3)" · +${row.investors.size-3} more" else ""),style=MaterialTheme.typography.labelSmall)
                }
            }
        }
        item{
            Text("Source: NSE ${source.lowercase(Locale.US)} deal disclosure. Client classification: selected named FPI issuer disclosures and AMFI fund directory. No complete stock-wise FII/DII flow or investment advice is claimed. Zero in a category means no matched rows in this report.",style=MaterialTheme.typography.labelSmall)
        }
    }
}
