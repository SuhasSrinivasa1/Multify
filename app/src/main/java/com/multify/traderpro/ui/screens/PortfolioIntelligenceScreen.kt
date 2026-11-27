package com.multify.traderpro.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.multify.traderpro.data.repository.PortfolioPreview
import com.multify.traderpro.data.repository.LiveNewsIntelligence
import com.multify.traderpro.engine.PortfolioLadderPolicy
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

private fun money(v:Double)=String.format(Locale.US,"₹%,.2f",v)
private fun stamp(v:Long?)=v?.let{Instant.ofEpochMilli(it).atZone(ZoneId.of("Asia/Kolkata")).toLocalDateTime().toString().replace('T',' ').take(16)+" IST"}?:"Unavailable"

@Composable
internal fun LiveNewsScreen(viewModel:TraderViewModel) {
    var snapshot by remember{mutableStateOf<LiveNewsIntelligence.Snapshot?>(null)}
    var filter by rememberSaveable{mutableStateOf("ALL")}
    LaunchedEffect(viewModel){while(true){snapshot=viewModel.liveNewsSnapshot();delay(15_000L)}}
    val s=snapshot
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
        item{
            Text("Live news intelligence",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
            Text("Exchange announcements · no automatic orders · market-hour alerts")
            Text("Last poll: "+stamp(s?.lastPollMs?.takeIf{it>0}),style=MaterialTheme.typography.labelSmall)
            Text(s?.sourceStatus?:"Waiting for engine",style=MaterialTheme.typography.bodySmall)
            Text("BSE source availability and issuer mapping require live validation. Missing feeds are not zero news. Reported percentages are not price forecasts.",style=MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement=Arrangement.spacedBy(4.dp)){
                for(c in listOf("ALL","POSITIVE","NEGATIVE","MIXED"))FilterChip(selected=filter==c,onClick={filter=c},label={Text(c.lowercase().replaceFirstChar{it.uppercase()})})
            }
        }
        val rows=s?.events.orEmpty().filter{filter=="ALL"||it.liveNews?.sentiment==filter}
        if(rows.isEmpty())item{Text("No captured events in this view yet.")}
        items(rows,key={it.id}){row->
            val m=row.liveNews?:return@items
            Card(Modifier.fillMaxWidth()){
                Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){
                    val color=when(m.sentiment){"POSITIVE"->androidx.compose.ui.graphics.Color(0xff15803d);"NEGATIVE"->MaterialTheme.colorScheme.error;else->MaterialTheme.colorScheme.onSurfaceVariant}
                    Text(row.symbol.ifBlank{"Unresolved issuer"}+" · "+m.sentiment,color=color,fontWeight=FontWeight.Bold)
                    Text(row.headline,style=MaterialTheme.typography.titleSmall)
                    Text(row.source+" · "+stamp(row.publishedAtMs),style=MaterialTheme.typography.labelSmall)
                    Text(row.category+" · "+m.evidence.ifBlank{"No clear directional evidence"},style=MaterialTheme.typography.bodySmall)
                    if(m.reportedPercentages.isNotEmpty())Text("Reported percentages: "+m.reportedPercentages.joinToString{String.format(Locale.US,"%.2f%%",it)},style=MaterialTheme.typography.bodySmall)
                    Text("Catalysts 1h / 3h / 5h: "+m.cluster1h+" / "+m.cluster3h+" / "+m.cluster5h,style=MaterialTheme.typography.bodySmall)
                    Text("Sampled response 15/30/60m: "+listOf(m.outcome15Pct,m.outcome30Pct,m.outcome60Pct).joinToString(" / "){it?.let{String.format(Locale.US,"%+.2f%%",it)}?:"—"},style=MaterialTheme.typography.labelSmall)
                    Text(row.url,style=MaterialTheme.typography.labelSmall,maxLines=2)
                }
            }
        }
        item{
            Text("Prior-session learning",style=MaterialTheme.typography.titleMedium)
            Text("Sampled 60m direction · one stock/category/day · missing labels excluded · no automatic model promotion",style=MaterialTheme.typography.bodySmall)
            for(p in s?.patterns.orEmpty().take(20))Text(p.key+": "+p.correct+"/"+p.samples+" correct direction · "+p.days+" days")
            if(s?.patterns.orEmpty().isEmpty())Text("Learning · no matured prior-session cohorts")
        }
    }
}

@Composable
internal fun PortfolioIntelligenceScreen(viewModel:TraderViewModel){
    var step by rememberSaveable{mutableStateOf("")}
    var saved by remember{mutableStateOf(false)}
    var filter by rememberSaveable{mutableStateOf("ALL")}
    var preview by remember{mutableStateOf<com.multify.traderpro.data.repository.PortfolioShadowResearch.Snapshot?>(null)}
    val scope=rememberCoroutineScope()
    LaunchedEffect(viewModel){while(true){
        val s=viewModel.portfolioShadowSnapshot()
        preview=s
        if(step.isBlank())step=s.budget.toString()
        delay(30_000L)
    }}
    val amount=step.toDoubleOrNull()?.takeIf{it.isFinite()&&it>=0}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(14.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
        item{
            Text("Portfolio intelligence",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
            Text("SHADOW / PAPER RESEARCH ONLY",color=MaterialTheme.colorScheme.error,fontWeight=FontWeight.Bold)
            Text("No real orders, no broker GTT changes. Paper quantities and sampled prices are not actual fills.")
            OutlinedTextField(value=step,onValueChange={step=it;saved=false},label={Text("Maximum per averaging step (₹)")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),modifier=Modifier.fillMaxWidth())
            Text("Same budget for upward and downward research. 0.5% illustrative fee reserve; fresh cash and portfolio caps not verified here.")
            Button(onClick={scope.launch{saved=viewModel.savePortfolioStepBudget(amount?:0.0)}},enabled=amount!=null,modifier=Modifier.fillMaxWidth()){Text(if(saved)"Saved" else "Save shared budget")}
            Text("Cached Alpha inventory: "+stamp(preview?.inventoryAt),style=MaterialTheme.typography.labelSmall)
            Text("Daily EMA20/50 and ATR use unadjusted EOD data. Sweep samples are not closed 5m/15m candles. No live BUY eligibility.")
            Text("Paper profit milestones +10% steps; 33% remaining. After observed +20%: peak minus 5/8/10 points, non-loosening. Adoption skips earlier milestones.")
            Row(horizontalArrangement=Arrangement.spacedBy(4.dp)){
                for(c in listOf("ALL","UP","DOWN","TRAIL"))FilterChip(selected=filter==c,onClick={filter=c},label={Text(c)})
            }
        }
        val rows=preview?.rows.orEmpty().filter{filter=="ALL"||it.candidate.kind.startsWith(filter)||(filter=="TRAIL"&&it.state?.armed==true)}
        if(rows.isEmpty())item{Text("No matching cached holdings. Missing data is not an empty portfolio.")}
        items(rows,key={it.holding.isin.ifBlank{it.holding.symbol}}){r->
            val h=r.holding;val s=r.state
            Card(Modifier.fillMaxWidth()){
                Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){
                    Text(h.symbol,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
                    Text("Actual shares "+h.quantity+" · free "+h.free+" · T1 "+h.t1)
                    Text("Broker average "+money(h.average)+" · observed "+(h.price?.let{money(it)}?:"unavailable"))
                    Text("Price time "+stamp(h.priceAtMs))
                    if(h.average>0) h.price?.let{Text("Observed gross P&L "+String.format(Locale.US,"%+.2f%%",(it/h.average-1)*100))}
                    Text(r.candidate.kind+" · "+r.candidate.reason)
                    if(r.candidate.quantity>0)Text("Research quantity "+r.candidate.quantity+" · not approved for execution")
                    r.trend?.let{Text("Daily bars "+it.count+" · EMA20 "+money(it.ema20)+" · EMA50 "+money(it.ema50)+" · ATR "+money(it.atr))}
                    if(s!=null){
                        Text("Paper remaining "+s.paperRemaining+" · observed peak "+money(s.peak))
                        Text("Next paper profit milestone "+money(s.reference*(1+s.nextProfit/100.0)))
                        Text("Initial research zones: upward "+money(s.reference*1.05)+" · downward "+listOf(.90,.80,.70).joinToString{money(s.reference*it)})
                        com.multify.traderpro.engine.PortfolioShadowPolicy.levels(s).forEachIndexed{i,v->
                            Text("Paper protective stage "+(i+1)+" · "+money(v)+if(i<s.stages)" · sampled touch" else " · waiting")
                        }
                        if(s.changed)Text("Actual holding changed; reconciliation required. Prior research retained.",color=MaterialTheme.colorScheme.error)
                        for(ev in s.events.takeLast(5).reversed())Text(ev.kind+" · "+ev.paperQuantity+" paper shares · observed "+money(ev.observed)+" · "+stamp(ev.at),style=MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}
