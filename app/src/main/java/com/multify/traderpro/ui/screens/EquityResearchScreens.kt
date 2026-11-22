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
import com.multify.traderpro.engine.LargeCapEquityObservation
import com.multify.traderpro.engine.SixMonthLowObservation
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private fun pc(v: Double?): String = v?.takeIf { it.isFinite() }
    ?.let { String.format(Locale.US,"%+.2f%%",it) } ?: "—"
private fun price(v: Double): String = String.format(Locale.US,"₹%,.2f",v)
private val india=ZoneId.of("Asia/Kolkata")
private fun atIst(ms: Long): String =
    if(ms<=0L) "Not yet fetched" else Instant.ofEpochMilli(ms).atZone(india)
        .format(DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm 'IST'"))

@Composable
internal fun LargeCapResearchScreen(state: TraderUiState, onRefresh: () -> Unit) {
    val r=state.equityResearch
    val intraday=r.largeCaps.filter { it.intradayResearch }
    val swing=r.largeCaps.filter { it.swingResearch }
    val others=r.largeCaps.filterNot { it.intradayResearch || it.swingResearch }
    LazyColumn(
        modifier=Modifier.fillMaxSize(),
        contentPadding=PaddingValues(14.dp),
        verticalArrangement=Arrangement.spacedBy(10.dp)
    ) {
        item {
            ResearchNotice("LARGE-CAP NSE EQUITY", "Research only · cash shares · Fixed is unchanged")
            Spacer(Modifier.height(8.dp))
            Text("Intraday momentum + multi-day swing opportunities",style=MaterialTheme.typography.titleMedium)
            Text("Universe: ${r.memberSource} · ${r.largeCaps.size} stocks with usable EOD history",
                style=MaterialTheme.typography.bodySmall)
            Text("Daily prices: ${r.latestDay.ifBlank { "not loaded" }} · ${r.completedDays} available sessions",
                style=MaterialTheme.typography.bodySmall)
            Text("Intraday pulse: ${if(r.currentPulseAtMs>0)atIst(r.currentPulseAtMs) else "No fresh, complete Fixed sweep"}",
                style=MaterialTheme.typography.bodySmall)
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
        items(intraday.take(30), key={ "intraday_${it.symbol}" }) { row ->
            LargeCapRow(row,"INTRADAY SHADOW")
        }
        item {
            Text("Swing watch · ${swing.size}",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
            if(swing.isEmpty()) Text(
                if(r.completedDays<60) "Awaiting daily NSE archive backfill; swing evidence is incomplete."
                else "No confirmed research-only trend candidates from available EOD history.",
                style=MaterialTheme.typography.bodySmall
            )
        }
        items(swing.take(40),key={"swing_${it.symbol}"}) { row ->
            LargeCapRow(row,"SWING SHADOW")
        }
        item {
            Text("Other monitored large caps · ${others.size}",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
            Text("No entry signal; displayed to avoid selection-only reporting.",
                style=MaterialTheme.typography.labelSmall)
        }
        items(others.take(50),key={"monitor_${it.symbol}"}) { row ->
            LargeCapRow(row,"MONITOR")
        }
        item {
            Text("These are research hypotheses, not broker instructions or verified profitable trading rules. No independent BUY, SELL, short or overnight-exit automation is enabled.",
                style=MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun LargeCapRow(r: LargeCapEquityObservation, label: String) {
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
        }
    }
}

@Composable
internal fun SixMonthLowResearchScreen(state: TraderUiState, onScan: () -> Unit) {
    val r=state.equityResearch
    var search by rememberSaveable { mutableStateOf("") }
    val filtered=remember(r.lowRows,search) {
        r.lowRows.filter { search.isBlank() || it.symbol.contains(search.trim(),true) }
    }
    LazyColumn(
        modifier=Modifier.fillMaxSize(),
        contentPadding=PaddingValues(14.dp),
        verticalArrangement=Arrangement.spacedBy(9.dp)
    ) {
        item {
            ResearchNotice("NSE SIX-MONTH-LOW SCREENER","0%–10% above the lowest daily low · EQ cash shares")
            Spacer(Modifier.height(8.dp))
            Text("Latest exchange day: ${r.latestDay.ifBlank { "not yet loaded" }}",
                style=MaterialTheme.typography.titleSmall)
            Text("Archive window: ${r.firstDay.ifBlank { "backfill pending" }} to ${r.latestDay.ifBlank { "pending" }}",
                style=MaterialTheme.typography.bodySmall)
            Text("Available session files: ${r.completedDays} · latest file equity rows: ${r.universeCount}",
                style=MaterialTheme.typography.bodySmall)
            if(!r.sixMonthHistoryComplete) {
                Text("INCOMPLETE SIX-MONTH HISTORY — results remain withheld until at least 105 valid NSE sessions spanning roughly six months are present. Scan Now can continue the permitted backfill.",
                    color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodyMedium)
            }
            if(r.lastError.isNotBlank()) {
                Text("Data source status: ${r.lastError}",color=MaterialTheme.colorScheme.error,
                    style=MaterialTheme.typography.bodySmall)
            }
            Text("Latest archive attempt: ${atIst(r.refreshAtMs)}",
                style=MaterialTheme.typography.labelSmall)
            Text("Official NSE cash EOD archives only; zero Groww calls. WorkManager is best-effort on Android. The app never downloads this history during NSE trading hours.",
                style=MaterialTheme.typography.labelSmall)
            Button(
                onClick = onScan,
                enabled = !state.researchScanning,
                modifier = Modifier.fillMaxWidth().testTag("six-month-low-scan-now"),
                contentPadding = PaddingValues(vertical = 14.dp)
            ) {
                if (state.researchScanning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("Scanning…")
                } else {
                    Text("Scan Now")
                }
            }
            Text(
                "Runs against the saved archive immediately. During 18:30–08:00 IST it also imports up to 25 missing official NSE session files per tap; during 08:00–18:30 IST it stays local-only. Zero Groww calls.",
                style = MaterialTheme.typography.labelSmall
            )
            Text("Unadjusted exchange lows: splits, bonuses and other corporate actions require verification before acting. A low is not itself a BUY recommendation.",
                style=MaterialTheme.typography.labelSmall)
        }
        item {
            Text("Near six-month low · ${filtered.size} of ${r.lowRows.size}",
                style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
            OutlinedTextField(value=search,onValueChange={ search=it },label={ Text("Filter NSE ticker") },
                singleLine=true,modifier=Modifier.fillMaxWidth())
            if(filtered.isEmpty()) Text(
                if(!r.sixMonthHistoryComplete) "No six-month-low claims until the historic archive is complete."
                else "No matching stocks within 0%–10% of their six-month low.",
                style=MaterialTheme.typography.bodySmall)
        }
        items(filtered,key={it.symbol}) { row -> LowRow(row) }
    }
}

@Composable
private fun LowRow(r: SixMonthLowObservation) {
    Card(shape=RoundedCornerShape(12.dp)) {
        Column(Modifier.fillMaxWidth().padding(12.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                Text(r.symbol,fontWeight=FontWeight.Bold)
                Text(String.format(Locale.US,"%.2f%% above low",r.distancePct),
                    style=MaterialTheme.typography.titleSmall)
            }
            Text("Close ${price(r.close)} · six-month low ${price(r.sixMonthLow)}",
                style=MaterialTheme.typography.bodySmall)
            Text("As of ${r.latestDate} · ${r.tradingDays} observed sessions · NSE EQ",
                style=MaterialTheme.typography.labelSmall)
        }
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
