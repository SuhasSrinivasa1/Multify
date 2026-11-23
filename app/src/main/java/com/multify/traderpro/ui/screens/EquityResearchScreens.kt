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
import com.multify.traderpro.data.repository.EquityLowObservation
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
            Text("Swing: EOD trend-qualified stocks for the NEXT session, with a pullback price reference. Intraday: fresh market-sweep strength DURING the session. Other: monitored universe, NOT entry signals.",style=MaterialTheme.typography.bodySmall)
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
            LargeCapRow(row,"SWING SHADOW",r.latestDay)
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
private fun LargeCapRow(r: LargeCapEquityObservation, label: String, eodDate: String = "") {
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
    val r = state.equityResearch
    var selectedMonths by rememberSaveable { mutableStateOf(3) }
    var search by rememberSaveable { mutableStateOf("") }
    val isThreeMonth = selectedMonths == 3
    val rows = if (isThreeMonth) r.threeMonthLowRows else r.sixMonthLowRows
    val ready = if (isThreeMonth) r.threeMonthHistoryComplete else r.sixMonthHistoryComplete
    val sessionCount = if (isThreeMonth) r.threeMonthCompletedDays else r.completedDays
    val firstDay = if (isThreeMonth) r.threeMonthFirstDay else r.firstDay
    // NSE positive P/E is official and dated; missing P/E ranks last, never zero.
    // Small caps require proven WHOLE-industry peer coverage, not just a partial index.
    val capEligibleRows = remember(rows) {
        rows.filter { it.marketCapCategory == "LARGE_CAP" ||
            it.marketCapCategory == "MID_CAP" ||
            (it.marketCapCategory == "SMALL_CAP" && it.verifiedIndustryPeMinimum) }
            .sortedWith(compareBy<EquityLowObservation> { it.peRatio ?: Double.POSITIVE_INFINITY }
                .thenBy { kotlin.math.abs(it.distancePct) }.thenBy { it.symbol })
    }
    val filtered = remember(capEligibleRows, search) {
        capEligibleRows.filter { search.isBlank() || it.symbol.contains(search.trim(), true) }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("six-month-low-screen"),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        item {
            ResearchNotice("NSE LOWS SCREENER", "Latest EOD close vs previous 3M/6M low · all breakdowns and up to 10% above included · EQ cash shares")
            Spacer(Modifier.height(6.dp))
            Text(
                "AMFI size classification: ${r.marketCapClassificationPeriod.ifBlank { "unavailable" }} · Large/Mid ranked by lowest verified NSE P/E first (missing P/E last). Small caps shown ONLY if proven lowest P/E in their full industry.",
                style = MaterialTheme.typography.labelSmall
            )
            Text(
                "NSE stock P/E report: ${r.officialPeDate.ifBlank { "unavailable" }} · ${r.officialPeSymbols} positive-P/E symbols · industry mapping ${r.industryClassifiedSymbols} stocks.",
                style = MaterialTheme.typography.labelSmall
            )
            if (r.officialPeDate != r.latestDay || !r.fullIndustryPeCoverage) {
                Text(
                    (if (r.officialPeDate != r.latestDay) "No matching-date NSE P/E report: values withheld until valid data arrives. " else "") +
                        (if (!r.fullIndustryPeCoverage) "Small caps withheld: the available industry map is incomplete and cannot prove a market-wide minimum." else ""),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                if (r.marketCapClassificationCount > 0)
                    "${r.marketCapClassificationCount} NSE symbols mapped from the official AMFI list."
                else "Official AMFI classification asset unavailable; no market-cap categories are guessed.",
                style = MaterialTheme.typography.labelSmall
            )
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = isThreeMonth,
                    onClick = { selectedMonths = 3 },
                    label = { Text("3M") },
                    modifier = Modifier.testTag("low-range-3m")
                )
                FilterChip(
                    selected = !isThreeMonth,
                    onClick = { selectedMonths = 6 },
                    label = { Text("6M") },
                    modifier = Modifier.testTag("low-range-6m")
                )
            }
            Text("Latest exchange day: ${r.latestDay.ifBlank { "not yet loaded" }}",
                style = MaterialTheme.typography.titleSmall)
            Text("Selected archive window: ${firstDay.ifBlank { "coverage pending" }} to ${r.latestDay.ifBlank { "pending" }}",
                style = MaterialTheme.typography.bodySmall)
            Text("Validated sessions: $sessionCount · latest file equity rows: ${r.universeCount}",
                style = MaterialTheme.typography.bodySmall)
            Text("3M coverage: ${r.threeMonthCompletedDays} sessions · ${if (r.threeMonthHistoryComplete) "READY" else "building"}",
                style = MaterialTheme.typography.labelSmall,
                color = if (r.threeMonthHistoryComplete) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            Text("6M backfill: ${r.completedDays} sessions · ${if (r.sixMonthHistoryComplete) "READY" else "in progress"}",
                style = MaterialTheme.typography.labelSmall,
                color = if (r.sixMonthHistoryComplete) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            if (!ready && isThreeMonth) {
                Text("INCOMPLETE THREE-MONTH HISTORY — results remain withheld until at least 55 valid NSE sessions spanning roughly 2.5 months are present.",
                    color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            } else if (!ready) {
                Text("INCOMPLETE SIX-MONTH HISTORY — results remain withheld until at least 105 valid NSE sessions spanning roughly six months are present. Scan Now can continue the permitted after-hours backfill.",
                    color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            if (r.lastError.isNotBlank()) {
                Text("Data source status: ${r.lastError}", color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall)
            }
            Text("Latest archive attempt: ${atIst(r.refreshAtMs)}",
                style = MaterialTheme.typography.labelSmall)
            Text("Official NSE cash EOD archives only; zero Groww calls. WorkManager continues best-effort six-month backfill after-hours. The app never downloads this history during NSE trading hours.",
                style = MaterialTheme.typography.labelSmall)
            Button(
                onClick = { onScan(selectedMonths) },
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
                if (isThreeMonth)
                    "3M Scan Now recalculates the saved archive and, during 18:30–08:00 IST, imports up to 25 missing official NSE sessions to build 3M coverage and advance the 6M backfill. During 08:00–18:30 IST it stays local-only. Zero Groww calls."
                else
                    "6M Scan Now recalculates the saved archive and, during 18:30–08:00 IST, imports up to 25 missing official NSE session files. During 08:00–18:30 IST it stays local-only. Zero Groww calls.",
                style = MaterialTheme.typography.labelSmall
            )
            Text("Unadjusted exchange lows: splits, bonuses and other corporate actions require verification before acting. A low is not itself a BUY recommendation.",
                style = MaterialTheme.typography.labelSmall)
        }
        item {
            Text("At/below or up to 10% above previous ${selectedMonths}M low · ${filtered.size} of ${capEligibleRows.size} · lowest P/E first",
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            OutlinedTextField(value = search, onValueChange = { search = it },
                label = { Text("Filter NSE ticker") }, singleLine = true,
                modifier = Modifier.fillMaxWidth())
            if (filtered.isEmpty()) Text(
                when {
                    !ready && isThreeMonth -> "No three-month-low claims until the minimum validated history is available."
                    !ready -> "No six-month-low claims until the historic archive is complete."
                    else -> "No eligible stocks at/below or up to 10% above their previous ${selectedMonths}-month low."
                },
                style = MaterialTheme.typography.bodySmall)
        }
        items(filtered, key = { "${selectedMonths}m_${it.symbol}" }) { row -> LowRow(row) }
    }
}

@Composable
private fun LowRow(r: EquityLowObservation) {
    Card(shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(r.symbol, fontWeight = FontWeight.Bold)
                    MarketCapBadge(r.marketCapCategory, r.marketCapRank)
                }
                Text(lowDistanceLabel(r.distancePct, r.lookbackMonths),
                    style = MaterialTheme.typography.titleSmall,
                    color = if (r.distancePct < -0.000001) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            }
            Text("Latest close ${price(r.close)} · previous ${r.lookbackMonths}M low ${price(r.periodLow)}",
                style = MaterialTheme.typography.bodySmall)
            Text("NSE P/E ${r.peRatio?.let { String.format(Locale.US,"%.2fx",it) } ?: "unavailable"} · ${r.industry?.let { "Industry $it" } ?: "industry unverified"}" +
                if (r.verifiedIndustryPeMinimum) " · verified lowest industry P/E" else "",
                style = MaterialTheme.typography.bodySmall)
            Text("Reference low date ${r.periodLowDate} · previous 1M low ${price(r.oneMonthLow)} (${oneMonthDistanceLabel(r.oneMonthDistancePct)})",
                style = MaterialTheme.typography.bodySmall)
            Text("As of ${r.latestDate} · ${r.tradingDays} prior symbol sessions · NSE EQ",
                style = MaterialTheme.typography.labelSmall)
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
