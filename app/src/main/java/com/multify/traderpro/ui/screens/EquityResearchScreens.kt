package com.multify.traderpro.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.multify.traderpro.data.repository.EquityResearchSnapshot
import com.multify.traderpro.data.repository.LargeWatchEvidence
import com.multify.traderpro.engine.LargeCapEquityObservation
import com.multify.traderpro.engine.SwingPullbackResearchPolicy
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private fun pc(v: Double?): String = v?.takeIf { it.isFinite() }
    ?.let { String.format(Locale.US,"%+.2f%%",it) } ?: "—"
private fun price(v: Double): String = String.format(Locale.US,"₹%,.2f",v)
private fun lowDistanceLabel(distancePct: Double, months: Int): String = when {
    distancePct < -0.000001 -> String.format(Locale.US,"%.2f%% BELOW previous %dM low",kotlin.math.abs(distancePct),months)
    distancePct > 0.000001 -> String.format(Locale.US,"%.2f%% ABOVE previous %dM low",distancePct,months)
    else -> "AT previous ${months}M low"
}
private fun oneMonthDistanceLabel(distancePct: Double): String = when {
    distancePct < -0.000001 -> String.format(Locale.US,"%.2f%% below previous 1M low",kotlin.math.abs(distancePct))
    distancePct > 0.000001 -> String.format(Locale.US,"%.2f%% above previous 1M low",distancePct)
    else -> "at previous 1M low"
}
private val india=ZoneId.of("Asia/Kolkata")
private fun atIst(ms: Long): String =
    if(ms<=0L) "Not yet fetched" else Instant.ofEpochMilli(ms).atZone(india)
        .format(DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm 'IST'"))

@Composable
internal fun LargeCapResearchScreen(state: TraderUiState, onRefresh: () -> Unit) {
    val r=state.equityResearch
    val intraday=r.largeCaps.filter { it.intradayResearch }
    val swing=r.largeCaps.filter { it.swingResearch }
    LazyColumn(
        modifier=Modifier.fillMaxSize(),
        contentPadding=PaddingValues(14.dp),
        verticalArrangement=Arrangement.spacedBy(10.dp)
    ) {
        item {
            ResearchNotice("LARGE-CAP NSE EQUITY", "Research only · cash shares · Fixed is unchanged")
            Spacer(Modifier.height(8.dp))
            Text("Intraday momentum + multi-day swing opportunities",style=MaterialTheme.typography.titleMedium)
            Text("Swing: EOD trend watch for the next session. Intraday: fresh Fixed-sweep strength during the session. Nonqualifying large caps are monitored internally but hidden.",style=MaterialTheme.typography.bodySmall)
            Text("Universe: ${r.memberSource} · ${r.largeCaps.size} stocks with usable EOD history",
                style=MaterialTheme.typography.bodySmall)
            Text("Daily prices: ${r.latestDay.ifBlank { "not loaded" }} · ${r.completedDays} available sessions",
                style=MaterialTheme.typography.bodySmall)
            Text("Intraday pulse: ${if(r.currentPulseAtMs>0)atIst(r.currentPulseAtMs) else "No fresh, complete Fixed sweep"}",
                style=MaterialTheme.typography.bodySmall)
            Text("Nifty 50 official EOD (${r.niftyContextDay.ifBlank { "unavailable" }}): ${r.niftyEodRegime} · day ${pc(r.niftyEodMovePct)} · previous ${pc(r.niftyPreviousEodMovePct)} · 5 sessions ${pc(r.niftyFiveDayMovePct)}",
                style=MaterialTheme.typography.bodyMedium,fontWeight=FontWeight.SemiBold)
            Text("Nifty index P/E ${r.niftyIndexPe?.let { String.format(Locale.US,"%.2fx",it) } ?: "missing"} · index P/B ${r.niftyIndexPb?.let { String.format(Locale.US,"%.2fx",it) } ?: "missing"} · sourced NSE index CSV, never stock P/B",
                style=MaterialTheme.typography.labelSmall)
            Text("Live large-cap breadth PROXY ${r.liveLargeCapBreadthProxyPct?.let { String.format(Locale.US,"%.1f%% positive",it) } ?: "unavailable"}; not the live Nifty index. Live Nifty regime is not claimed without a verified index quote.",
                style=MaterialTheme.typography.labelSmall)
            Spacer(Modifier.height(5.dp))
            OutlinedButton(onClick=onRefresh) { Text("Refresh local research view") }
            Text("No extra Groww requests. Intraday observations reuse Fixed's existing NSE-wide LTP sweep; the EOD history updates after-hours.",
                style=MaterialTheme.typography.labelSmall)
        }
        item {
            Text("Intraday watch · ${intraday.size}",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
            if(intraday.isEmpty()) Text(
                if(r.currentPulseAtMs==0L) "No fresh intraday sweep. Watching begins after valid Fixed market snapshots during NSE hours."
                else "No candidates currently satisfy the experimental relative-strength conditions.",
                style=MaterialTheme.typography.bodySmall
            )
        }
        items(intraday, key={ "intraday_${it.symbol}" }) { row ->
            LargeCapRow(row,"INTRADAY SHADOW",r.latestDay,r.largeWatchEvidence[row.symbol])
        }
        item {
            Text("Swing watch · ${swing.size}",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
            if(swing.isEmpty()) Text(
                if(r.completedDays<60) "Awaiting daily NSE archive backfill; swing evidence is incomplete."
                else "No confirmed research-only trend candidates from available EOD history.",
                style=MaterialTheme.typography.bodySmall
            )
        }
        items(swing,key={"swing_${it.symbol}"}) { row ->
            LargeCapRow(row,"SWING SHADOW",r.latestDay,r.largeWatchEvidence[row.symbol])
        }
        item {
            Text("Daily shadow-learning ledger",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
            Text("Existing SWING/INTRADAY candidates are captured once per symbol/day with ${r.largeWatchEvidence.values.firstOrNull()?.features?.size ?: 0} evidence fields; missing P/B or Nifty data stays missing. Outcome: 3rd future EOD close for Swing, same-day EOD close for Intraday; +0.50% is a research label, NOT a fill/target-before-stop claim.",
                style=MaterialTheme.typography.bodySmall)
            Text("Variants evolve by observed performance, not by changing Fixed. Shadow freeze eligibility requires ≥100 matured calls, ≥20 independent days, ≥90% observed success, ≥85% Wilson lower bound and separate chronological holdout evidence. Never automatically promote into trading.",
                style=MaterialTheme.typography.labelSmall)
            r.largeWatchLearning.take(10).forEach { m ->
                Text("${m.type} / ${m.variant}: ${m.hits}/${m.matured} · ${String.format(Locale.US,"%.1f%%",m.hitRatePct)} observed · conservative ${String.format(Locale.US,"%.1f%%",m.wilsonLowerPct)} · ${m.independentDays} days · ${if(m.researchFreezeEligible)"RESEARCH FREEZE ELIGIBLE" else "LEARNING"}",
                    style=MaterialTheme.typography.bodySmall)
            }
            if(r.largeWatchLearning.isEmpty()) Text("No matured forward-outcome observations yet; data collection starts as each next EOD becomes available.",
                style=MaterialTheme.typography.bodySmall)
        }
        item {
            Text("These are research hypotheses, not broker instructions or verified profitable trading rules. No independent BUY, SELL, short or overnight-exit automation is enabled.",
                style=MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun LargeCapRow(r: LargeCapEquityObservation, label: String, eodDate: String = "", evidence: LargeWatchEvidence? = null) {
    var evidenceExpanded by rememberSaveable(r.symbol,label) { mutableStateOf(false) }
    Card(shape=RoundedCornerShape(12.dp)) {
        Column(Modifier.fillMaxWidth().padding(12.dp),verticalArrangement=Arrangement.spacedBy(3.dp)) {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                Text(r.symbol,fontWeight=FontWeight.Bold)
                Text(label,style=MaterialTheme.typography.labelSmall,
                    color=MaterialTheme.colorScheme.primary)
            }
            Text("${r.sector} · EOD ${price(r.close)}",style=MaterialTheme.typography.bodySmall)
            Text("5-day ${pc(r.fiveDayPct)} · 20-day ${pc(r.twentyDayPct)} · ${if(r.comparisonBasis=="SECTOR_PEERS")"sector peers" else "broad peers"} ${pc(r.sectorFiveDayPct)}",
                style=MaterialTheme.typography.bodySmall)
            Text("5-day relative strength ${pc(r.relativeFiveDayPct)} · ${if(r.comparisonBasis=="SECTOR_PEERS")"sector" else "broad peer"} breadth ${pc(r.sectorBreadthPct)}",
                style=MaterialTheme.typography.bodySmall)
            Text("EMA20 ${price(r.ema20)} · EMA50 ${price(r.ema50)} · volume acceleration ${r.volumeAcceleration?.let { String.format(Locale.US,"%.2fx",it) } ?: "unknown"}",
                style=MaterialTheme.typography.labelSmall)
            if(r.intradayPct!=null) Text("Intraday pulse ${pc(r.intradayPct)} · vs ${if(r.comparisonBasis=="SECTOR_PEERS")"sector" else "broad"} peers ${pc(r.intradayRelativePct)}",
                style=MaterialTheme.typography.bodySmall)
            if(evidence!=null) {
                val sectorDaily=evidence.features["sector_eod_move_pct"]
                val niftyDaily=evidence.features["nifty_eod_move_pct"]
                val ratio=evidence.features["stock_pe"]
                Text("NIFTY EOD ${evidence.niftyRegime} ${pc(niftyDaily)} · NSE sector ${evidence.sectorIndex ?: "not mapped"}: ${evidence.sectorRegime} ${pc(sectorDaily)}",
                    style=MaterialTheme.typography.bodySmall)
                Text("Stock P/E ${ratio?.let{String.format(Locale.US,"%.2fx",it)} ?: "unavailable"} · Stock P/B unverified · ${evidence.features.size} tracked daily measures",
                    style=MaterialTheme.typography.bodySmall)
                Text("Sector index data: ${evidence.marketDate.ifBlank{"not yet imported"}} · no inferred live Nifty quote",
                    style=MaterialTheme.typography.labelSmall)
                OutlinedButton(onClick={ evidenceExpanded=!evidenceExpanded },modifier=Modifier.fillMaxWidth()) {
                    Text(if(evidenceExpanded)"Hide daily evidence" else "Show all ${evidence.features.size} daily measures")
                }
                if(evidenceExpanded){
                    evidence.features.forEach { (metric,value) ->
                        Text("${metric.replace('_',' ')}: ${value?.takeIf{it.isFinite()}?.let { String.format(Locale.US,"%.3f",it) } ?: "MISSING"}",
                            style=MaterialTheme.typography.labelSmall)
                    }
                    Text("Feature EOD: ${evidence.eodDate}; market index EOD: ${evidence.marketDate.ifBlank{"unavailable"}}. Stock P/B is missing until a sourced fundamentals feed exists. Snapshot only; no live execution authority.",
                        style=MaterialTheme.typography.labelSmall)
                }
            }
            if(r.swingResearch && label == "SWING SHADOW") {
                val zone = SwingPullbackResearchPolicy.evaluate(r.close,r.ema20,r.ema50)
                if(zone != null) {
                    Text("Next-session pullback reference ${price(zone.preferred)} · watch zone ${price(zone.lower)}–${price(zone.upper)}",
                        fontWeight=FontWeight.Bold,style=MaterialTheme.typography.bodyMedium)
                    Text("Based on ${eodDate.ifBlank { "latest saved EOD" }} close; ${String.format(Locale.US,"%.2f%%",zone.closeExtensionPct)} below EOD close. ${if(zone.distantPullback)"EXTENDED — wait for a pullback; do not chase." else "Wait for a fresh price/volume confirmation."}",
                        style=MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
internal fun SixMonthLowResearchScreen(state: TraderUiState, onScan: (Int) -> Unit) {
    val research=state.equityResearch
    var selectedMonths by rememberSaveable { mutableStateOf(3) }
    var search by rememberSaveable { mutableStateOf("") }
    var capFilter by rememberSaveable { mutableStateOf("ALL") }
    val isThree=selectedMonths==3
    val historyReady=if(isThree)research.threeMonthHistoryComplete else research.sixMonthHistoryComplete
    val result=if(isThree)research.adaptiveLowThreeRows else research.adaptiveLowSixRows
    val scan=if(isThree)research.adaptiveLowThreeRun else research.adaptiveLowSixRun
    val learning=if(isThree)research.adaptiveLowThreeLearning else research.adaptiveLowSixLearning
    val scanFresh=historyReady && scan!=null && scan.day==research.latestDay
    // Closest confirmed approaches first. No "hot" API request or dynamic
    // eligibility change occurs on this UI thread.
    fun countLabel(category:String):String = if(!scanFresh) "—" else (result.count{it.marketCapCategory==category}).toString()
    val counts=remember(result,scanFresh) {
        if(scanFresh) result.groupingBy{it.marketCapCategory}.eachCount() else emptyMap()
    }
    val visible=remember(result,scanFresh,search,capFilter) {
        if(!scanFresh) emptyList() else result.filter {
            (capFilter=="ALL" || it.marketCapCategory==capFilter) &&
                (search.isBlank() || it.symbol.contains(search.trim(),true) ||
                it.companyName.contains(search.trim(),true) ||
                (it.sector?.contains(search.trim(),true)==true))
        }.sortedWith(
            compareBy<com.multify.traderpro.engine.AdaptiveLowCandidate> {
                if(it.phase=="IN_ENTRY_ZONE")0 else 1
            }.thenBy{it.distanceAboveSupportPct}
                .thenByDescending{it.reactions.size}
                .thenByDescending{it.patternScore}.thenBy{it.symbol}
        )
    }
    LazyColumn(
        modifier=Modifier.fillMaxSize().testTag("six-month-low-screen"),
        contentPadding=PaddingValues(14.dp),
        verticalArrangement=Arrangement.spacedBy(10.dp)
    ) {
        item {
            ResearchNotice(
                "ADAPTIVE NSE SUPPORT RETESTS",
                "Research-only · repeated rebound lows, approaching from ABOVE within strictly 5% · no automatic BUY"
            )
            Text(
                "Find previous support levels that produced at least TWO confirmed +5% rebounds within ten subsequent EOD sessions and are now being revisited during a decline.",
                style=MaterialTheme.typography.bodyMedium
            )
            Text(
                "This is NOT a list of stocks simply near a 3M/6M low. Raw NSE OHLC/volume history is scanned across all saved NSE EQ names after market hours.",
                style=MaterialTheme.typography.bodySmall
            )
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                FilterChip(selected=isThree,onClick={selectedMonths=3},
                    label={Text("3M")},modifier=Modifier.testTag("low-range-3m"))
                FilterChip(selected=!isThree,onClick={selectedMonths=6},
                    label={Text("6M")},modifier=Modifier.testTag("low-range-6m"))
            }
            Text(
                "Latest exchange EOD: ${research.latestDay.ifBlank{"not imported"}} · ${if(isThree)research.threeMonthCompletedDays else research.completedDays} archive sessions",
                style=MaterialTheme.typography.bodySmall
            )
            if(scanFresh) {
                Text(
                    "Completed full-NSE EOD scan: ${scan!!.allNseEquitySymbols} EQ symbols · ${scan.historicallyCompleteSymbols} symbols with adequate ${selectedMonths}M history · ${scan.qualifiedPatterns} confirmed near-support patterns",
                    style=MaterialTheme.typography.bodyMedium,fontWeight=FontWeight.SemiBold
                )
                Text("Cached analysis completed ${atIst(scan.completedAtMs)}; no Groww market-data use.",
                    style=MaterialTheme.typography.labelSmall)
            } else {
                Text(
                    if(!historyReady)
                        "HISTORY BUILDING — full ${selectedMonths}M evidence is not yet ready; no support-pattern claim will be made."
                    else "PENDING AFTER-HOURS FULL-NSE PATTERN SCAN — new TEST install has no precomputed local watchlist yet.",
                    color=MaterialTheme.colorScheme.error,
                    style=MaterialTheme.typography.bodySmall
                )
            }
            Text(
                "Last sourced Nifty EOD: ${research.niftyContextDay.ifBlank{"unknown"}} · ${research.niftyEodRegime} ${pc(research.niftyEodMovePct)}. Sector readings are attached only when an actual matching NSE index is available.",
                style=MaterialTheme.typography.bodySmall
            )
            Text(
                "NSE archives and optional legacy OHLC backfill run only during 18:30–08:00 IST. During market hours, Scan Now reads LOCAL results; Fixed and Working Range keep their Groww API bandwidth.",
                style=MaterialTheme.typography.labelSmall
            )
            Button(onClick={onScan(selectedMonths)},enabled=!state.researchScanning,
                modifier=Modifier.fillMaxWidth().testTag("six-month-low-scan-now")) {
                if(state.researchScanning) {
                    CircularProgressIndicator(Modifier.size(18.dp),
                        strokeWidth=2.dp,color=MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(8.dp))
                    Text("Recalculating…")
                } else Text("Refresh research / Scan Now")
            }
            if(research.lastError.isNotBlank())
                Text("Exchange archive status: ${research.lastError}",
                    style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.error)
            Text(
                "CAUTION: Prices are corporate-action UNADJUSTED unless verified. Review splits, dividends, actual liquidity, earnings and live price before placing any MANUAL staged entry. Do not automatically average down through invalidated support.",
                style=MaterialTheme.typography.labelSmall
            )
        }
        item {
            Text("Market cap · AMFI ${research.marketCapClassificationPeriod.ifBlank{"dataset unavailable"}} · ${research.marketCapClassificationCount} NSE symbols in asset",
                style=MaterialTheme.typography.bodySmall)
            if(research.marketCapClassificationCount==0)
                Text("Classification asset unavailable — no size is guessed.",color=MaterialTheme.colorScheme.error)
            Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                FilterChip(selected=capFilter=="ALL",onClick={capFilter="ALL"},label={Text("All ${if(scanFresh)result.size.toString() else "—"}")},modifier=Modifier.testTag("low-cap-all"))
                FilterChip(selected=capFilter=="LARGE_CAP",onClick={capFilter="LARGE_CAP"},label={Text("Large ${countLabel("LARGE_CAP")}")},modifier=Modifier.testTag("low-cap-large"))
            }
            Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                FilterChip(selected=capFilter=="MID_CAP",onClick={capFilter="MID_CAP"},label={Text("Mid ${countLabel("MID_CAP")}")},modifier=Modifier.testTag("low-cap-mid"))
                FilterChip(selected=capFilter=="SMALL_CAP",onClick={capFilter="SMALL_CAP"},label={Text("Small ${countLabel("SMALL_CAP")}")},modifier=Modifier.testTag("low-cap-small"))
            }
            FilterChip(selected=capFilter=="UNCLASSIFIED",onClick={capFilter="UNCLASSIFIED"},label={Text("Unclassified ${countLabel("UNCLASSIFIED")}")},modifier=Modifier.testTag("low-cap-unclassified"))
            val capAudit=if(isThree)research.lowCapAudit3 else research.lowCapAudit6
            var expandedAudit by rememberSaveable { mutableStateOf(false) }
            if(scanFresh && capFilter=="LARGE_CAP" && counts["LARGE_CAP"]==null) {
                Text("Large Cap: zero confirmed support-retest matches, not zero large-cap stocks.",fontWeight=FontWeight.SemiBold)
                Text("Requires two prior 5% rebounds within 10 sessions, rapid 2% rebounds, a repeated support cluster and a fresh decline to within 5% above support.",style=MaterialTheme.typography.bodySmall)
            }
            if(scanFresh && capAudit.isNotBlank()) {
                Text(capAudit.substringBefore("\nFirst rejection:"),style=MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick={expandedAudit=!expandedAudit}){Text(if(expandedAudit)"Hide rejection details" else "Why stocks failed · view rejection details")}
                if(expandedAudit)Text(capAudit.substringAfter("\nFirst rejection:",capAudit),style=MaterialTheme.typography.bodySmall)
            } else Text("Market-cap rejection audit pending after-hours scan. Missing data is not zero matches.",style=MaterialTheme.typography.bodySmall)
            Text("Price evidence date: ${research.latestDay.ifBlank{"unknown"}} · calculation time is not a newer market close.",style=MaterialTheme.typography.labelSmall)
            Text("Approaching historical rebound zones · ${visible.size}",
                style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
            OutlinedTextField(value=search,onValueChange={search=it},singleLine=true,
                label={Text("Filter ticker, company or mapped sector")},
                modifier=Modifier.fillMaxWidth())
            if(visible.isEmpty()) Text(
                if(scanFresh && capFilter=="LARGE_CAP" && counts["LARGE_CAP"]==null)
                    "No qualifying large-cap support retests. See the rejection audit above."
                else if(scanFresh && (capFilter!="ALL" || search.isNotBlank()))
                    "No candidates match the selected filters. Choose All or clear the search."
                else if(scanFresh)
                    "No NSE EQ shares meet the full repeated-rebound, falling-toward-support and STRICTLY under-5% criteria today. An empty list is a valid outcome."
                else "No eligible list until the local after-hours scan completes.",
                style=MaterialTheme.typography.bodySmall
            )
        }
        items(visible,key={"${selectedMonths}M_${it.symbol}"}) { c ->
            AdaptiveLowOpportunityRow(c)
        }
        item {
            Text("Adaptive research audit · ${selectedMonths}M",
                style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
            Text(
                "Each independent observation is followed for ten FULL future EOD sessions. A +5% later EOD close is only a research rebound, not a fill or executable P&L. Unmatured observations remain unresolved.",
                style=MaterialTheme.typography.bodySmall
            )
            if(learning.isEmpty()) Text("No matured prospective rebound observations yet.",
                style=MaterialTheme.typography.bodySmall)
            learning.take(8).forEach{m->
                Text(
                    "${m.variant.replace('_',' ')} · ${m.successes}/${m.matured} observed (${String.format(Locale.US,"%.1f%%",m.observedPct)}) · ${m.independentDays} independent dates · lower bound ${String.format(Locale.US,"%.1f%%",m.wilsonLowerPct)} · ${if(m.researchFreezeEligible)"SHADOW FREEZE REVIEW" else "LEARNING"}",
                    style=MaterialTheme.typography.labelSmall
                )
            }
            Text(
                "Research freeze review requires 100+ matured calls, 20+ independent days, >=90% observed outcomes, >=85% conservative confidence and a valid chronological holdout. Nothing here changes Fixed, Working Range, Alpha, Beta or broker orders.",
                style=MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
private fun AdaptiveLowOpportunityRow(c: com.multify.traderpro.engine.AdaptiveLowCandidate) {
    var expanded by rememberSaveable(c.symbol,c.months,c.asOfDay) { mutableStateOf(false) }
    Card(shape=RoundedCornerShape(12.dp)) {
        Column(Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement=Arrangement.spacedBy(5.dp)) {
            Text(c.symbol,fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleMedium)
            if(c.companyName.isNotBlank() && !c.companyName.equals(c.symbol,true))
                Text(c.companyName,style=MaterialTheme.typography.bodySmall)
            MarketCapBadge(c.marketCapCategory,null)
            Text("Latest candle: ${c.currentCandlePattern?.replace('_',' ') ?: "OHLC evidence unavailable"} · prior bullish shapes: ${c.reactions.count{com.multify.traderpro.engine.AdaptiveLowCandleEvidence.bullish(it.candlePattern)}}/${c.reactions.size}",
                style=MaterialTheme.typography.bodySmall)
            Text("Candle patterns are shadow evidence, not an entry trigger or a new veto.",style=MaterialTheme.typography.labelSmall)
            Text(
                "${if(c.phase=="IN_ENTRY_ZONE")"IN HISTORICAL WATCH ZONE" else "APPROACHING SUPPORT — NOT IN ENTRY ZONE"} · ${c.months}M",
                style=MaterialTheme.typography.labelMedium,
                color=if(c.phase=="IN_ENTRY_ZONE")MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text("Research entry reference ${price(c.preferredEntry)}",
                fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleMedium)
            Text("Historical support watch range ${price(c.entryLow)}–${price(c.entryHigh)}",
                style=MaterialTheme.typography.bodyMedium)
            Text(
                "Latest ${c.asOfDay} close ${price(c.latestClose)} · ${String.format(Locale.US,"%.2f%%",c.distanceAboveSupportPct)} above repeated support ${price(c.supportPrice)}",
                style=MaterialTheme.typography.bodySmall
            )
            Text(
                "${c.reactions.size} separate prior rebounds · median +${String.format(Locale.US,"%.2f%%",c.medianHistoricalReboundPct)} within ${String.format(Locale.US,"%.1f",c.medianSessionsToRebound)} sessions · recent descent ${String.format(Locale.US,"%.2f%%",c.approachDrawdown10Pct)} from 10-day peak",
                style=MaterialTheme.typography.bodySmall
            )
            Text(
                "Nifty ${c.niftyRegime} ${pc(c.niftyEodPct)} · ${c.sectorIndexName?:c.sector?:"Sector unclassified"} ${c.sectorRegime} ${pc(c.sectorEodPct)}",
                style=MaterialTheme.typography.bodySmall
            )
            Text(
                "NSE P/E ${c.stockPe?.let{String.format(Locale.US,"%.2fx",it)}?:"unavailable"} · P/B unavailable · revenue/profit history unavailable",
                style=MaterialTheme.typography.labelSmall
            )
            Text(
                "Historical breakdown reference ${price(c.historicalBreakReference)} — NOT a placed stop. No partial BUY or averaging orders are submitted by this tab.",
                style=MaterialTheme.typography.labelSmall
            )
            OutlinedButton(onClick={expanded=!expanded},modifier=Modifier.fillMaxWidth()) {
                Text(if(expanded)"Hide historical evidence" else "Show previous rebound dates & indicators")
            }
            if(expanded) {
                Text("EMA9 ${c.ema9?.let(::price)?:"—"} · EMA20 ${c.ema20?.let(::price)?:"—"} · EMA50 ${c.ema50?.let(::price)?:"—"}",
                    style=MaterialTheme.typography.bodySmall)
                Text(
                    "RSI14 ${c.rsi14?.let{String.format(Locale.US,"%.1f",it)}?:"—"} · ATR14 ${c.atr14?.let(::price)?:"missing OHLC"} · today's volume / 20D avg ${c.volumeTo20Day?.let{String.format(Locale.US,"%.2fx",it)}?:"—"}",
                    style=MaterialTheme.typography.bodySmall
                )
                Text("Recorded candle reversal evidence: ${c.candleCoverage} of ${c.reactions.size} prior lows have full OHLC. Blank older candles await bounded after-hours backfill.",
                    style=MaterialTheme.typography.labelSmall)
                c.reactions.forEach{touch->
                    Text(
                        "${touch.lowDay} · support ${price(touch.supportLow)} · subsequent max EOD close +${String.format(Locale.US,"%.2f%%",touch.reboundPctFromLow)} · first +5% close ${touch.sessionsToRebound} sessions later · candle ${touch.candlePattern?.replace('_',' ') ?: "unavailable"} · P/E at time unavailable · RSI ${touch.rsi14AtLow?.let{String.format(Locale.US,"%.1f",it)}?:"—"} · volume ${touch.volumeToPrior20?.let{String.format(Locale.US,"%.2fx",it)}?:"—"}",
                        style=MaterialTheme.typography.labelSmall
                    )
                }
                Text(
                    "Historical observations include no contemporaneous verified fundamentals at prior lows. EOD high/low order, intraday fill prices, split adjustments and next-session direction are not inferred.",
                    style=MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

@Composable
private fun MarketCapBadge(category: String, rank: Int?) {
    val scheme = MaterialTheme.colorScheme
    val (label, background, foreground) = when (category) {
        "LARGE_CAP" -> Triple("LARGE CAP", scheme.primaryContainer, scheme.onPrimaryContainer)
        "MID_CAP" -> Triple("MID CAP", scheme.secondaryContainer, scheme.onSecondaryContainer)
        "SMALL_CAP" -> Triple("SMALL CAP", scheme.tertiaryContainer, scheme.onTertiaryContainer)
        else -> Triple("UNCLASSIFIED", scheme.surfaceVariant, scheme.onSurfaceVariant)
    }
    Surface(
        shape = RoundedCornerShape(50),
        color = background,
        contentColor = foreground
    ) {
        Text(
            if (rank != null && category != "UNCLASSIFIED") "$label · #$rank" else label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun ResearchNotice(title: String, subtitle: String) {
    Card(colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Text(title,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
            Text(subtitle,style=MaterialTheme.typography.bodySmall)
        }
    }
}
