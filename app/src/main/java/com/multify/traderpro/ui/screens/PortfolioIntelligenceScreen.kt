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
    val context=LocalContext.current
    val prefs=remember{context.getSharedPreferences("portfolio_ladder_preview_v1",Context.MODE_PRIVATE)}
    var total by rememberSaveable{mutableStateOf(prefs.getString("total","0")?:"0")}
    var perStock by rememberSaveable{mutableStateOf(prefs.getString("stock","0")?:"0")}
    var perAdd by rememberSaveable{mutableStateOf(prefs.getString("add","0")?:"0")}
    var saved by remember{mutableStateOf(false)}
    var preview by remember{mutableStateOf<PortfolioPreview?>(null)}
    LaunchedEffect(viewModel){while(true){preview=viewModel.portfolioPreview();delay(15_000L)}}
    fun amount(s:String)=s.toDoubleOrNull()?.takeIf{it.isFinite()&&it>=0}
    val budget=amount(total);val stock=amount(perStock);val add=amount(perAdd)
    val valid=budget!=null&&stock!=null&&add!=null&&stock<=budget&&add<=stock
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(14.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
        item{
            Text("Portfolio intelligence",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
            Text("RESEARCH / PREVIEW ONLY",color=MaterialTheme.colorScheme.error,fontWeight=FontWeight.Bold)
            Text("No BUY, SELL or GTT changes. Existing positions remain under original protection.")
            Text("Downward averaging budget",style=MaterialTheme.typography.titleMedium)
            OutlinedTextField(value=total,onValueChange={total=it;saved=false},label={Text("Total reserved budget (₹)")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),modifier=Modifier.fillMaxWidth())
            OutlinedTextField(value=perStock,onValueChange={perStock=it;saved=false},label={Text("Maximum per stock (₹)")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),modifier=Modifier.fillMaxWidth())
            OutlinedTextField(value=perAdd,onValueChange={perAdd=it;saved=false},label={Text("Maximum per averaging step (₹)")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),modifier=Modifier.fillMaxWidth())
            Button(onClick={saved=prefs.edit().putString("total",total).putString("stock",perStock).putString("add",perAdd).commit()},enabled=valid,modifier=Modifier.fillMaxWidth()){Text(if(saved)"Budget saved" else "Save preview budget")}
            if(!valid)Text("Require 0 ≤ per step ≤ per stock ≤ total.",color=MaterialTheme.colorScheme.error)
            Text("Profit: +10%, +20%, +30% of live broker average; 33% of remaining whole shares. First downward ladder 90%, 80%, 70% of anchor. Following a confirmed profit sale: fill minus 5% of anchor; then subtract 10% of anchor per confirmed add. After an averaging fill, re-arm +10% of new live average.")
            Text("Cached Alpha inventory: "+stamp(preview?.inventoryAtMs),style=MaterialTheme.typography.labelSmall)
            Text("Settled holdings below; separate CNC position symbols: "+preview?.cncPositionSymbols.orEmpty().joinToString().ifBlank{"—"})
            Text("Historical sale milestones cannot be inferred from current profit. Each row is an uninitialized first-step preview. Observed prices are not execution quotes.",style=MaterialTheme.typography.bodySmall)
        }
        val holdings=preview?.holdings.orEmpty()
        if(holdings.isEmpty())item{Text("No cached broker holdings available. This does not establish an empty portfolio.")}
        items(holdings,key={it.symbol}){h->
            Card(Modifier.fillMaxWidth()){
                Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){
                    Text(h.symbol,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
                    Text("Shares: "+h.quantity+" · free "+h.free+" · T1 "+h.t1)
                    Text("Broker average: "+money(h.average)+" · observed: "+(h.price?.let{money(it)}?:"unavailable"))
                    Text("Price time: "+stamp(h.priceAtMs),style=MaterialTheme.typography.labelSmall)
                    if(h.average.isFinite()&&h.average>0){
                        val p=PortfolioLadderPolicy.initial(h.average)
                        Text("First profit threshold: "+money(PortfolioLadderPolicy.sellTrigger(p,h.average))+" · "+PortfolioLadderPolicy.sellQuantity(h.quantity)+" shares")
                        Text("First averaging threshold: "+money(p.nextAddPrice)+" · next "+money(h.average*.80))
                        val px=h.price
                        if(px!=null&&px.isFinite()&&px>0)Text("Observed gross P&L: "+String.format(Locale.US,"%+.2f%%",(px/h.average-1)*100))
                        if(valid)Text("Budget-based add preview: "+PortfolioLadderPolicy.addQuantity(p,stock!!,add!!)+" shares")
                    }else Text("Missing valid broker cost basis.")
                }
            }
        }
    }
}
