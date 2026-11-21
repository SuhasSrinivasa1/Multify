package com.multify.traderpro.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.multify.traderpro.data.network.EngineHealthDto
import com.multify.traderpro.data.network.FixedScanTrendStore
import com.multify.traderpro.data.network.ForecastDto
import com.multify.traderpro.data.network.PositionDto
import com.multify.traderpro.data.network.WorkingRangeRecommendationDto
import com.multify.traderpro.engine.GttProtectionReadinessPolicy
import com.multify.traderpro.engine.FixedBuyAvailabilityPolicy
import com.multify.traderpro.engine.NseEquitySessionCalendar
import java.time.Instant
import java.time.ZoneId
import com.multify.traderpro.engine.HardStopLossPolicy
import com.multify.traderpro.engine.SteppedTrailingPolicy
import com.multify.traderpro.engine.ManualShortIntradayPolicy
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal enum class LitePage { FIXED, WORKING_RANGE, MANUAL, AVERAGES, SETTINGS }
private enum class LiteTone { POSITIVE, WARNING, NEGATIVE, INFO, NEUTRAL }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FixedLiteApp(viewModel: TraderViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var page by rememberSaveable { mutableStateOf(LitePage.FIXED) }
    var confirmBuy by remember { mutableStateOf<String?>(null) }
    var confirmEnable by remember { mutableStateOf<String?>(null) }
    var confirmHaltReset by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) {
        if (it != null) viewModel.exportLogs(it)
    }

    LaunchedEffect(state.message, state.error) {
        (state.error ?: state.message)?.takeIf { it.isNotBlank() }?.let {
            snackbar.showSnackbar(it)
            viewModel.clearBanner()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            Column {
                CenterAlignedTopAppBar(
                    title = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Fixed Momentum Pro", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text(
                                when (page) {
                                    LitePage.FIXED -> "15-minute Fixed strategy"
                                    LitePage.WORKING_RANGE -> "Working Range pilot"
                                    LitePage.MANUAL -> "Intraday MIS BUY / SELL"
                                    LitePage.AVERAGES -> "Observed Fixed outcomes"
                                    LitePage.SETTINGS -> "Accounts and controls"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { page = LitePage.AVERAGES }) {
                            Icon(Icons.Default.Analytics, contentDescription = "Open Fixed averages")
                        }
                        IconButton(onClick = { page = LitePage.SETTINGS }) {
                            Icon(Icons.Default.Settings, contentDescription = "Open settings")
                        }
                        IconButton(onClick = { viewModel.refresh() }, enabled = !state.loading) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh dashboard")
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
                )
                AccountStatusStrip(state)
                if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        },
        bottomBar = { LiteBottomBar(page) { page = it } }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (page) {
                LitePage.FIXED -> LiteFixedScreen(state, viewModel::generateFixed) { confirmBuy = it }
                LitePage.WORKING_RANGE -> LiteRangeScreen(state)
                LitePage.MANUAL -> LiteManualScreen(state, viewModel)
                LitePage.AVERAGES -> LiteAveragesScreen(state)
                LitePage.SETTINGS -> LiteSettingsScreen(
                    state, viewModel,
                    onEnableRequested = { confirmEnable = it },
                    onResetRequested = { confirmHaltReset = true },
                    onExport = { export.launch("FixedMomentum-v9030-all-mis-diagnostics.zip") }
                )
            }
        }
    }

    confirmBuy?.let { symbol ->
        AlertDialog(
            onDismissRequest = { confirmBuy = null },
            icon = { Icon(Icons.Default.WarningAmber, contentDescription = null) },
            title = { Text("Confirm manual Alpha BUY") },
            text = { Text("Submit a guarded MIS intraday BUY for " + symbol + "? Fresh Alpha quote, funds, duplicate-order and execution checks still apply.") },
            confirmButton = {
                Button(onClick = {
                    confirmBuy = null
                    viewModel.executeFixed(symbol)
                }) { Text("Review and submit") }
            },
            dismissButton = { TextButton(onClick = { confirmBuy = null }) { Text("Cancel") } }
        )
    }

    confirmEnable?.let { source ->
        AlertDialog(
            onDismissRequest = { confirmEnable = null },
            icon = { Icon(Icons.Default.Lock, contentDescription = null) },
            title = { Text("Enable " + source + " Auto-Buy?") },
            text = {
                Text(
                    "This permits new eligible signals to submit REAL Alpha MIS intraday BUY orders. Old signals are not revived. Current inherited Fixed hard stop is -" +
                        HardStopLossPolicy.MAX_LOSS_PCT + "% from fill; trail arms at +" +
                        SteppedTrailingPolicy.ARM_TRIGGER_PCT + "% and initially protects +" +
                        SteppedTrailingPolicy.INITIAL_STOP_GAIN_PCT + "%."
                )
            },
            confirmButton = {
                Button(onClick = {
                    confirmEnable = null
                    if (source == "Fixed") viewModel.setFixedAutoBuy(true)
                    else viewModel.setWorkingRangeEnabled(true)
                }) { Text("Enable " + source) }
            },
            dismissButton = { TextButton(onClick = { confirmEnable = null }) { Text("Keep OFF") } }
        )
    }

    if (confirmHaltReset) {
        AlertDialog(
            onDismissRequest = { confirmHaltReset = false },
            icon = { Icon(Icons.Default.WarningAmber, contentDescription = null) },
            title = { Text("Review safety halt") },
            text = { Text("Reconcile every open position, broker fill and active protective GTT directly with Groww before resetting. Resetting the halt does not enable Auto-Buy.") },
            confirmButton = {
                Button(onClick = {
                    confirmHaltReset = false
                    viewModel.resetHalt()
                }) { Text("Reset halt") }
            },
            dismissButton = { TextButton(onClick = { confirmHaltReset = false }) { Text("Cancel") } }
        )
    }
}

@Composable
internal fun LiteBottomBar(page: LitePage, onNavigate: (LitePage) -> Unit) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
        NavigationBarItem(
            selected = page == LitePage.FIXED,
            onClick = { onNavigate(LitePage.FIXED) },
            icon = { Icon(Icons.Default.Shield, contentDescription = null) },
            label = { Text("Fixed") }
        )
        NavigationBarItem(
            selected = page == LitePage.WORKING_RANGE,
            onClick = { onNavigate(LitePage.WORKING_RANGE) },
            icon = { Icon(Icons.Default.Timeline, contentDescription = null) },
            label = { Text("Working Range") }
        )
        NavigationBarItem(
            selected = page == LitePage.MANUAL,
            onClick = { onNavigate(LitePage.MANUAL) },
            icon = { Icon(Icons.Default.TrendingUp, contentDescription = null) },
            label = { Text("Manual") }
        )
    }
}

@Composable
private fun AccountStatusStrip(state: TraderUiState) {
    val alphaReady = state.settings.brokerAuthenticated && state.settings.staticIpMatched
    val alphaStatus = when {
        alphaReady -> "CONNECTED"
        state.credentialsConfigured -> "AUTH REQUIRED"
        else -> "SETUP REQUIRED"
    }
    val betaStatus = when {
        state.betaAuthenticated -> "READ-ONLY READY"
        state.betaConfigured -> "AUTH REQUIRED"
        else -> "OPTIONAL"
    }
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AccountPill("ALPHA", alphaStatus, if (alphaReady) LiteTone.POSITIVE else LiteTone.WARNING)
        AccountPill("BETA", betaStatus, if (state.betaAuthenticated) LiteTone.INFO else LiteTone.NEUTRAL)
        if (state.settings.safetyHalt || state.dashboard?.halted == true) {
            AccountPill("TRADING", "SAFETY HALT", LiteTone.NEGATIVE)
        }
    }
}

@Composable
private fun AccountPill(label: String, status: String, tone: LiteTone) {
    val colors = toneColors(tone)
    Surface(
        color = colors.first,
        contentColor = colors.second,
        shape = CircleShape,
        border = BorderStroke(1.dp, colors.second.copy(alpha = 0.16f))
    ) {
        Row(
            Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(colors.second))
            Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            Text(status, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun toneColors(tone: LiteTone): Pair<Color, Color> = when (tone) {
    LiteTone.POSITIVE -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
    LiteTone.WARNING -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
    LiteTone.NEGATIVE -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
    LiteTone.INFO -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
    LiteTone.NEUTRAL -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
private fun StatusPill(text: String, tone: LiteTone = LiteTone.NEUTRAL) {
    val colors = toneColors(tone)
    Surface(color = colors.first, contentColor = colors.second, shape = CircleShape) {
        Text(
            text.uppercase(Locale.US),
            Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

@Composable
private fun LiteCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        if (!subtitle.isNullOrBlank()) {
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun MetricTile(
    label: String,
    value: String,
    supporting: String? = null,
    modifier: Modifier = Modifier,
    tone: LiteTone = LiteTone.NEUTRAL
) {
    LiteCard(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = when (tone) {
                LiteTone.POSITIVE -> MaterialTheme.colorScheme.primary
                LiteTone.NEGATIVE -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.onSurface
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (!supporting.isNullOrBlank()) {
            Text(supporting, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Respect Android font scaling and compact devices rather than compressing prices. */
@Composable
private fun MetricPair(left: @Composable () -> Unit, right: @Composable () -> Unit) {
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth < 336.dp || fontScale >= 1.3f) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                left()
                right()
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) { left() }
                Box(Modifier.weight(1f)) { right() }
            }
        }
    }
}

/** Status chips wrap naturally under accessibility font and display-size settings. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipGroup(content: @Composable RowScope.() -> Unit) {
    // A horizontally scrollable chip group keeps individual pill text unbroken.
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content)
}


@Composable
internal fun LiteFixedScreen(
    state: TraderUiState,
    onScan: () -> Unit,
    nowMs: Long = System.currentTimeMillis(),
    onBuy: (String) -> Unit
) {
    val dashboard = state.dashboard
    val localContext = LocalContext.current
    val recentScans = remember(dashboard?.asOf) {
        FixedScanTrendStore.read(localContext)
            .filter { it.result == "COMPLETED" }.takeLast(30)
    }
    val scanMedianMs = FixedScanTrendStore.percentile(recentScans.map { it.elapsedMs }, .50)
    val scanP90Ms = FixedScanTrendStore.percentile(recentScans.map { it.elapsedMs }, .90)
    val health = dashboard?.fixedHealth ?: EngineHealthDto()
    val recommendations = dashboard?.fixedForecasts.orEmpty()
        .sortedWith(compareBy<ForecastDto> { it.rank }.thenByDescending { it.generatedAtMs })
    val positions = dashboard?.positions.orEmpty().filter { it.quantity > 0 }
    val discoveryTone = when (health.discovery.uppercase(Locale.US)) {
        "READY", "HEALTHY", "COMPLETE" -> LiteTone.POSITIVE
        "DEGRADED", "ERROR", "FAILED" -> LiteTone.NEGATIVE
        "WARMING", "WAITING" -> LiteTone.WARNING
        else -> LiteTone.NEUTRAL
    }
    // Device time zones are not NSE time zones. Match the existing IST session calendar.
    val marketOpen = NseEquitySessionCalendar.isFixedScanWindow(
        Instant.ofEpochMilli(nowMs).atZone(ZoneId.of("Asia/Kolkata"))
    )
    val discoveryLabel = when {
        !state.settings.fixedScannerEnabled -> "PAUSED"
        !marketOpen -> "MARKET CLOSED"
        !state.settings.brokerAuthenticated -> "AUTH REQUIRED"
        health.discoveryBaselineReady -> health.discovery.ifBlank { "READY" }
        else -> health.discovery.ifBlank { "WAITING" }
    }

    LazyColumn(
        Modifier.fillMaxSize().testTag("fixed-screen"),
        contentPadding = PaddingValues(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "fixed-overview") {
            LiteCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("FIXED ENGINE", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                        Text("Frozen 15m strategy", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    }
                    StatusPill("v7.4.1", LiteTone.INFO)
                }
                Text("40-factor model · completed 15-minute candles", style = MaterialTheme.typography.bodyMedium)
                ChipGroup {
                    StatusPill(if (state.settings.fixedScannerEnabled) "Scanner ON" else "Scanner PAUSED", if (state.settings.fixedScannerEnabled) LiteTone.POSITIVE else LiteTone.WARNING)
                    StatusPill(if (state.settings.fixedAutoBuyEnabled) "Auto-Buy ON" else "Auto-Buy OFF", if (state.settings.fixedAutoBuyEnabled) LiteTone.WARNING else LiteTone.NEUTRAL)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Market", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(if (marketOpen) prettyState(dashboard?.marketSession ?: "OPEN") else "Outside NSE trading hours", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Discovery", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    StatusPill(discoveryLabel,
                        if (!marketOpen || !state.settings.fixedScannerEnabled) LiteTone.NEUTRAL else discoveryTone)
                }
                if (health.discoveryBaselineReady) {
                    LinearProgressIndicator(
                        progress = { (health.discoveryCoveragePct / 100.0).toFloat().coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        fmt(health.discoveryCoveragePct) + "% universe coverage · " + health.discoveryBatchErrors + " batch errors",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        when {
                            !marketOpen -> "Live discovery starts during the NSE session. Coverage is not measured while closed."
                            !state.settings.fixedScannerEnabled -> "Scanning paused. Broker-side SELL/GTT monitoring remains separate."
                            !state.settings.brokerAuthenticated -> "Authenticate Alpha before checking market coverage."
                            else -> "Waiting for a complete market sweep. No coverage percentage is inferred."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Market data", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(ageLabel(health.marketDataAgeMs), style = MaterialTheme.typography.labelMedium)
                }
                Button(
                    onClick = onScan,
                    enabled = state.settings.fixedScannerEnabled && marketOpen && !state.loading,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                ) {
                    if (state.loading) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(if (state.loading) "Refreshing…" else "Run Fixed scan")
                }
                Text(
                    if (marketOpen) "Full-market scan respects the broker pacing and single-worker gates."
                    else "Available in NSE market hours. Outside-session scans are not queued.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item(key = "fixed-scan-performance") {
            LiteCard {
                SectionTitle("Scan performance", "Last 30 completed · local logs only")
                MetricPair(
                    { MetricTile("Median scan",
                        scanMedianMs?.let { fmt(it / 1000.0) + " s" } ?: "—",
                        recentScans.size.toString() + " completed") },
                    { MetricTile("P90 scan",
                        scanP90Ms?.let { fmt(it / 1000.0) + " s" } ?: "—",
                        "Actual elapsed · not a target") }
                )
            }
        }

        item(key = "fixed-metrics") {
            MetricPair(
                { MetricTile("Recommendations", recommendations.size.toString(), "Published today", tone = if (recommendations.isNotEmpty()) LiteTone.INFO else LiteTone.NEUTRAL) },
                { MetricTile("Open longs", positions.size.toString(), "App-owned MIS or legacy CNC", tone = if (positions.isEmpty()) LiteTone.NEUTRAL else LiteTone.INFO) }
            )
        }

        item(key = "fixed-protection") {
            LiteCard {
                SectionTitle("Position protection", "Legacy CNC GTT + new MIS SELL stops · independent of scanner")
                StatusPill(
                    if (dashboard?.halted == true || state.settings.safetyHalt) "SAFETY HALT · NEW BUYS BLOCKED" else "PROTECTION MONITOR INDEPENDENT",
                    if (dashboard?.halted == true || state.settings.safetyHalt) LiteTone.NEGATIVE else LiteTone.POSITIVE
                )
                if (positions.isEmpty()) {
                    Text("No app-owned open holdings in this installation.", style = MaterialTheme.typography.bodyMedium)
                } else {
                    positions.forEachIndexed { index, position ->
                        PositionRow(position)
                        if (index != positions.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }

        item(key = "fixed-opening-watch") {
            val pulses = dashboard?.fixedPulseResearch.orEmpty()
            LiteCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    SectionTitle("Opening momentum watch", "Read-only · existing NSE scans")
                    StatusPill("NO BUY", LiteTone.NEUTRAL)
                }
                Text(
                    "From 09:15 IST: two complete market sweeps are needed for an LTP movement sample. " +
                        "Official Fixed still needs completed current-session 15m candles, usually available after 09:30.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (pulses.isEmpty()) {
                    Text("No confirmed two-sweep price change yet.", style = MaterialTheme.typography.bodyMedium)
                } else pulses.take(6).forEach { pulse ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(pulse.symbol, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                        Text("+" + fmt(pulse.pulsePct) + "% sampled", style = MaterialTheme.typography.labelMedium)
                    }
                }
                Text("Client receipt times are not exchange tick timestamps. Zero extra discovery requests.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        item(key = "fixed-recommendations-title") {
            SectionTitle("Fixed recommendations", recommendations.size.toString() + " published · original rank order")
        }
        if (recommendations.isEmpty()) {
            item(key = "fixed-empty") {
                EmptyStateCard(
                    "No published recommendations",
                    if (!state.settings.brokerAuthenticated) "Connect Alpha and verify the registered static IP to enable market data."
                    else "No qualifying PRIMARY has been published yet. WAITING is not a failed scan."
                ) { Icon(Icons.Default.TrendingUp, contentDescription = null) }
            }
        } else {
            items(recommendations, key = { "fixed-" + it.symbol + "-" + it.generatedAtMs }) { row ->
                FixedRecommendationCard(
                    row, onBuy, nowMs,
                    state.settings.brokerAuthenticated && state.settings.staticIpMatched &&
                        !state.settings.safetyHalt && dashboard?.halted != true
                )
            }
        }
    }
}

@Composable
private fun PositionRow(position: PositionDto) {
    val protected = if (position.product == "MIS")
        com.multify.traderpro.engine.MisIntradayLongPolicy.verified(
            position.smartOrderMode, position.smartOrderId, position.smartOrderStatus
        ) else GttProtectionReadinessPolicy.verified(
            position.smartOrderMode, position.smartOrderId, position.smartOrderStatus
        )
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(position.symbol, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            StatusPill(if (protected) (if (position.product == "MIS") "MIS SL-M VERIFIED" else "GTT VERIFIED") else "CHECK BROKER STOP", if (protected) LiteTone.POSITIVE else LiteTone.NEGATIVE)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(position.quantity.toString() + " shares · avg " + rupees(position.averagePrice), style = MaterialTheme.typography.bodySmall)
            Text("LTP " + rupees(position.ltp), style = MaterialTheme.typography.bodySmall)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Stop " + rupees(position.smartOrderTriggerPrice ?: position.stopPrice), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(if (position.trailArmed) "TRAIL ARMED" else "HARD STOP", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun FixedRecommendationCard(
    row: ForecastDto, onBuy: (String) -> Unit, nowMs: Long, brokerReady: Boolean
) {
    val tierTone = when (row.qualityTier.uppercase(Locale.US)) {
        "PRIMARY" -> LiteTone.POSITIVE
        "SECONDARY" -> LiteTone.INFO
        else -> LiteTone.WARNING
    }
    val drift = row.fixedFirstPrimaryPrice?.takeIf { it > 0.0 }?.let { (row.entryPrice / it - 1.0) * 100.0 }
    LiteCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(row.symbol, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Rank #" + row.rank + " · " + row.status, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                StatusPill(row.qualityTier, tierTone)
                StatusPill(if (row.executionEligible) "ACTIONABLE" else "RESEARCH ONLY", if (row.executionEligible) LiteTone.POSITIVE else LiteTone.WARNING)
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        MetricPair(
            { MetricTile("Reference", rupees(row.entryPrice), "Original Fixed entry") },
            { MetricTile("Fixed target", rupees(row.targetPrice), "+" + pct(row.targetPct), tone = LiteTone.POSITIVE) }
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Score " + fmt(row.score) + " · confidence " + fmt(row.confidence * 100.0) + "%", style = MaterialTheme.typography.labelSmall)
            Text("MFE " + pct(row.maxFavourablePct) + " · MAE " + pct(row.maxAdversePct), style = MaterialTheme.typography.labelSmall)
        }
        if (drift != null) {
            Text(
                "First PRIMARY " + rupees(row.fixedFirstPrimaryPrice) + " · entry drift " + pct(drift),
                style = MaterialTheme.typography.bodySmall,
                color = if (drift > 0.75) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text("Scan " + timeLabel(row.generatedAtMs) + " · published " + timeLabel(row.fixedActualPublishedAtMs ?: 0L), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(row.reason, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
        if (row.executionEligible) {
            val allowedNow = FixedBuyAvailabilityPolicy.canBuy(
                nowMs, brokerReady, row.status, row.executionEligible
            )
            OutlinedButton(
                onClick = { onBuy(row.symbol) },
                enabled = allowedNow,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            ) { Text(if (allowedNow) "Manual BUY · Alpha" else "BUY unavailable · verify market / Alpha") }
        } else {
            Text("No manual order permission for this observation.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun LiteManualScreen(state: TraderUiState, viewModel: TraderViewModel) {
    var query by rememberSaveable { mutableStateOf("") }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var manualSide by rememberSaveable { mutableStateOf("BUY") }
    var actionsExpanded by remember { mutableStateOf(false) }
    var shortConfirmSymbol by remember { mutableStateOf<String?>(null) }
    val shortState = viewModel.manualShortStatus()
    val master = viewModel.manualMasterStatus()
    val ready = viewModel.manualMasterReady()
    // All matches are read from a locally indexed weekly NSE master. Typing
    // never submits a Groww quote or another network request.
    val matches = if (ready) viewModel.manualStockMatches(query) else emptyList()
    val activeSelection = selected?.let { id -> matches.firstOrNull { it.symbol == id } }
    val authenticated = state.settings.brokerAuthenticated &&
        state.settings.staticIpMatched
    val istNow = java.time.ZonedDateTime.now(ZoneId.of("Asia/Kolkata"))
    val marketOpen = com.multify.traderpro.engine.MisIntradayLongPolicy.mayEnter(istNow)
    val shortWindow = ManualShortIntradayPolicy.mayOpen(istNow)
    Column(
        Modifier.fillMaxSize().testTag("manual-screen").padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        LiteCard {
            SectionTitle("Manual", "Alpha · NSE CASH · operator-directed trades")
            Text("All new trades use Alpha MIS intraday positions: BUY opens a long; SELL opens a short. Both use the same existing Alpha budget; no delivery holdings are created.",
                style = MaterialTheme.typography.bodySmall)
            Box {
                OutlinedButton(
                    onClick = { actionsExpanded = true },
                    modifier = Modifier.fillMaxWidth().testTag("manual-action-dropdown")
                ) { Text(if (manualSide == "BUY") "Manual BUY · MIS long ▾" else "Manual SELL · MIS short ▾") }
                DropdownMenu(expanded = actionsExpanded, onDismissRequest = { actionsExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text("Manual BUY · MIS long position") },
                        onClick = { manualSide = "BUY"; selected = null; actionsExpanded = false }
                    )
                    DropdownMenuItem(
                        text = { Text("Manual SELL · MIS short position") },
                        onClick = { manualSide = "SELL"; selected = null; actionsExpanded = false }
                    )
                }
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it; selected = null },
                label = { Text("Search NSE stock or company (e.g. Tata)") },
                placeholder = { Text("Type company name or symbol") },
                modifier = Modifier.fillMaxWidth().testTag("manual-stock-search"),
                singleLine = true
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text("${master.count} NSE symbols · ${master.source.ifBlank { "Not synced" }}",
                        style = MaterialTheme.typography.labelSmall)
                    Text(
                        if (ready) "Local list ready · weekly refresh"
                        else "List missing or older than 7 days · sync required",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (ready) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error
                    )
                }
                TextButton(onClick = viewModel::syncManualNseStocks) {
                    Text("Sync now")
                }
            }
            if (master.lastError.isNotBlank()) {
                Text("Last sync: " + master.lastError,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelSmall)
            }
        }
        if (shortState != null && shortState.unresolved()) {
            LiteCard {
                SectionTitle("Alpha intraday short · " + shortState.symbol, shortState.state)
                Text("MIS SHORT " + shortState.quantity + " shares · entry " +
                    rupees(shortState.entryPrice) + " · BUY stop " + rupees(shortState.stopTrigger),
                    style = MaterialTheme.typography.bodySmall)
                Text("Broker stop order " + shortState.protectionOrderId.ifBlank { "NOT VERIFIED" } +
                    " · cover is a BUY, not a holdings SELL.", style = MaterialTheme.typography.bodySmall)
                if (!shortState.brokerStopVerified()) {
                    Text("UNPROTECTED / UNRESOLVED · Safety halt applies. Open Groww Positions and Orders to reconcile immediately.",
                        color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (selected != null && activeSelection != null) {
            LiteCard {
                Text("Selected: " + activeSelection.companyName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold)
                Text(activeSelection.symbol + if (manualSide == "BUY")
                    " · NSE CASH · Alpha MIS LONG" else " · NSE CASH · Alpha MIS SHORT",
                    style = MaterialTheme.typography.bodySmall)
                Text(if (manualSide == "BUY")
                    "Fixed exit thresholds unchanged: -1% initial broker SELL SL-M, trail arms at +0.55%, protects +0.50%, and ratchets +0.10%. All MIS positions exit intraday."
                else
                    "Reversed Fixed exit: +1% initial broker BUY SL-M, arms after -0.55% share move, then locks +0.50% short gain and lowers the BUY stop in +0.10% steps. Intraday only; controlled square-off from 14:55 IST.",
                    style = MaterialTheme.typography.bodySmall)
                Text(if (manualSide == "BUY")
                    "Real MIS BUY uses Alpha funds, position, pending SELL stop and intraday square-off checks."
                else
                    "Real MIS short SELL opens a negative Alpha intraday position, not a delivery holding. Requires fresh broker checks, a pending BUY stop and intraday square-off.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(
                    onClick = {
                        val symbol = activeSelection.symbol
                        selected = null
                        if (manualSide == "BUY") viewModel.executeManualNse(symbol)
                        else shortConfirmSymbol = symbol
                    },
                    enabled = ready && authenticated &&
                        (if (manualSide == "BUY") marketOpen else shortWindow) &&
                        !state.settings.safetyHalt && state.dashboard?.halted != true &&
                        shortState?.unresolved() != true,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                        .testTag(if (manualSide == "BUY") "manual-buy-alpha" else "manual-sell-alpha")
                ) {
                    Text((if (manualSide == "BUY") "BUY " else "SELL SHORT ") +
                        activeSelection.symbol + " · Alpha")
                }
                if (!(if (manualSide == "BUY") marketOpen else shortWindow) || !authenticated) {
                    Text("Requires authenticated Alpha, verified static IP and NSE MIS entry hours" +
                        (" (all MIS entries until 14:30 IST)."),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error)
                }
            }
        }
        if (query.isBlank()) {
            Text("Enter a company name to see all locally indexed NSE matches.",
                style = MaterialTheme.typography.bodyMedium)
        } else if (!ready) {
            Text("NSE master is unavailable. Sync before choosing a stock; no order is permitted from an incomplete list.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error)
        } else if (matches.isEmpty()) {
            Text("No matching NSE CASH equity in the weekly list.",
                style = MaterialTheme.typography.bodySmall)
        } else {
            Text("${matches.size} matching NSE listing(s) · select one",
                style = MaterialTheme.typography.labelMedium)
            LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f).testTag("manual-match-list")) {
                items(matches, key = { it.symbol }) { stock ->
                    Surface(
                        onClick = { selected = stock.symbol },
                        shape = RoundedCornerShape(10.dp),
                        color = if (selected == stock.symbol)
                            MaterialTheme.colorScheme.secondaryContainer
                        else MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                    ) {
                        ListItem(
                            headlineContent = {
                                Text(stock.companyName, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            },
                            supportingContent = { Text(stock.symbol + " · NSE") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
    shortConfirmSymbol?.let { symbol ->
        AlertDialog(
            onDismissRequest = { shortConfirmSymbol = null },
            icon = { Icon(Icons.Default.WarningAmber, contentDescription = null) },
            title = { Text("Open REAL Alpha MIS SHORT?") },
            text = { Text("SELL " + symbol + " opens a NEW INTRADAY SHORT. " +
                "It does not sell your CNC holdings. Your existing Alpha per-order budget applies. " +
                "A broker BUY stop starts 1% above the fill and trails downward on favourable moves. " +
                "The app attempts to BUY-to-cover by 14:55 IST; execution and stop fills are not guaranteed. " +
                "If a protective order cannot be verified, new trades halt and you must inspect Groww immediately.") },
            confirmButton = {
                Button(onClick = {
                    shortConfirmSymbol = null
                    viewModel.executeManualShort(symbol)
                }, modifier = Modifier.testTag("confirm-manual-short")) { Text("Confirm MIS SHORT") }
            },
            dismissButton = {
                TextButton(onClick = { shortConfirmSymbol = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
internal fun LiteRangeScreen(state: TraderUiState) {
    val rows = state.dashboard?.workingRangeRecommendations.orEmpty()
        .sortedWith(compareBy<WorkingRangeRecommendationDto> { it.state != "IN_RANGE" }.thenByDescending { it.confirmedAtMs })
    val confirmed = rows.count { it.state == "IN_RANGE" }
    LazyColumn(
        Modifier.fillMaxSize().testTag("working-range-screen"),
        contentPadding = PaddingValues(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "range-overview") {
            LiteCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("WORKING RANGE", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                        Text("Independent pilot", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    }
                    StatusPill("RESEARCH", LiteTone.INFO)
                }
                Text("Reuses completed Fixed 15-minute evaluations and quotes.", style = MaterialTheme.typography.bodyMedium)
                ChipGroup {
                    StatusPill("Two distinct scans", LiteTone.INFO)
                    StatusPill(if (state.settings.workingRangeEnabled) "Auto-Buy ON" else "Research only", if (state.settings.workingRangeEnabled) LiteTone.WARNING else LiteTone.NEUTRAL)
                }
                Text("Pilot results are not a validated win rate. Missing inputs remain missing.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item(key = "range-metrics") {
            MetricPair(
                { MetricTile("Candidates", rows.size.toString(), "Retained observations") },
                { MetricTile("Confirmed", confirmed.toString(), "IN_RANGE after distinct scans", tone = if (confirmed > 0) LiteTone.POSITIVE else LiteTone.NEUTRAL) }
            )
        }
        item(key = "range-title") {
            SectionTitle("Working Range observations", "Entry quality and sampled outcomes")
        }
        if (rows.isEmpty()) {
            item(key = "range-empty") {
                EmptyStateCard(
                    "No confirmed range candidates",
                    "A candidate needs two independent scan generations. Repeating the same scan cannot confirm it."
                ) { Icon(Icons.Default.Timeline, contentDescription = null) }
            }
        } else {
            items(rows, key = { "range-" + it.symbol + "-" + it.scanStartedAtMs }) { row ->
                WorkingRangeCard(row)
            }
        }
    }
}

@Composable
private fun WorkingRangeCard(row: WorkingRangeRecommendationDto) {
    val tone = when (row.state.uppercase(Locale.US)) {
        "IN_RANGE" -> LiteTone.POSITIVE
        "MOMENTUM_WEAKENING", "EXPIRED", "STOP_FIRST", "TARGET_HIT_SAMPLED" -> LiteTone.WARNING
        else -> LiteTone.INFO
    }
    LiteCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(row.symbol, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Age " + fmt(row.candidateAgeMinutes) + " min · " + row.autoBuyState, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            StatusPill(row.state, tone)
        }
        MetricPair(
            { MetricTile("First qualified", rupees(row.firstQualifiedPrice), "Immutable reference") },
            { MetricTile("Confirmed entry", rupees(row.entryPrice), "Target " + rupees(row.targetPrice), tone = LiteTone.INFO) }
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Target +" + pct(row.targetPct), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            Text("Drift " + pct(row.driftFromFirstQualifiedPct), style = MaterialTheme.typography.labelMedium)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("RSI " + num(row.rsi) + " · RVOL " + num(row.rvol), style = MaterialTheme.typography.bodySmall)
            Text("VWAP " + num(row.vwapExtensionAtr) + " ATR", style = MaterialTheme.typography.bodySmall)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("MFE " + pct(row.maxFavourablePct), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            Text("MAE " + pct(row.maxAdversePct), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
        }
        Text(
            "Quote " + prettyState(row.marketabilityState) + " · " + row.sampledOutcomeNote,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
        if (row.autoBuyOrderId != null || row.autoBuyQuantity > 0) {
            Text(
                "Broker order " + (row.autoBuyOrderId ?: row.autoBuyReference) + " · " + row.autoBuyQuantity +
                    " shares · fill " + rupees(row.autoBuyFillPrice),
                style = MaterialTheme.typography.labelSmall
            )
        }
        Text(row.reason, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun LiteAveragesScreen(state: TraderUiState) {
    val stats = state.dashboard?.fixedOutcomes
    val snapshot = state.dashboard?.fixedSnapshot
    val observed = stats?.observed ?: 0
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("averages-screen")
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        LiteCard {
            SectionTitle("Fixed performance", "Observed samples only · not broker P&L")
            Text(
                "Published " + (stats?.published ?: 0) + " · observed " + observed +
                    " · today " + (stats?.todayObserved ?: 0) + "/" + (stats?.todayPublished ?: 0),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "Sparse client-side LTP samples do not prove a continuous price path. Unresolved observations are not counted as losses.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        MetricPair(
            { MetricTile("Trail arm", pct(stats?.armHitRatePct), (stats?.armHits ?: 0).toString() + " / " + observed + " observed", tone = LiteTone.INFO) },
            { MetricTile("Full target", pct(stats?.fullTargetHitRatePct), (stats?.fullTargetHits ?: 0).toString() + " / " + observed + " observed", tone = LiteTone.POSITIVE) }
        )
        MetricPair(
            { MetricTile("Mean observed MFE", pct(stats?.rollingObservedAveragePct), "Rolling sampled average") },
            { MetricTile("Median MFE", pct(stats?.medianPct), "Observed distribution") }
        )
        LiteCard {
            SectionTitle("MFE distribution", "Observed sample percentiles")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                DistributionValue("P25", pct(stats?.p25Pct))
                DistributionValue("Median", pct(stats?.medianPct))
                DistributionValue("P75", pct(stats?.p75Pct))
            }
        }
        LiteCard {
            SectionTitle("Frozen Fixed reference", "Production strategy baseline")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Target reference", style = MaterialTheme.typography.bodyMedium)
                Text(pct(snapshot?.fixedTargetPct), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Canonical calls", style = MaterialTheme.typography.bodyMedium)
                Text((snapshot?.learningCalls ?: 107).toString(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Text("Working Range research cannot change Fixed scoring, ranking or targets.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DistributionValue(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
internal fun LiteSettingsScreen(
    state: TraderUiState,
    vm: TraderViewModel,
    onEnableRequested: (String) -> Unit,
    onResetRequested: () -> Unit,
    onExport: () -> Unit
) {
    var alphaKey by remember { mutableStateOf("") }
    var alphaTotp by remember { mutableStateOf("") }
    var betaKey by remember { mutableStateOf("") }
    var betaSecret by remember { mutableStateOf("") }
    var staticIp by remember(state.settings.expectedStaticIp) { mutableStateOf(state.settings.expectedStaticIp) }
    var budget by remember(state.settings.holdingBudgetRupees) { mutableStateOf(state.settings.holdingBudgetRupees.toString()) }
    var capital by remember(state.settings.maxTotalCapitalRupees) { mutableStateOf(state.settings.maxTotalCapitalRupees.toString()) }
    var holdings by remember(state.settings.maxConcurrentHoldings) { mutableStateOf(state.settings.maxConcurrentHoldings.toString()) }

    val positions = state.dashboard?.positions.orEmpty().filter { it.quantity > 0 }
    val allProtected = positions.all {
        if (it.product == "MIS") com.multify.traderpro.engine.MisIntradayLongPolicy.verified(
            it.smartOrderMode, it.smartOrderId, it.smartOrderStatus
        ) else GttProtectionReadinessPolicy.verified(it.smartOrderMode, it.smartOrderId, it.smartOrderStatus)
    }
    val alphaReady = state.settings.brokerAuthenticated && state.settings.staticIpMatched
    val canEnableAutoBuy = alphaReady && state.dashboard != null && !state.settings.safetyHalt && state.dashboard?.halted != true && allProtected

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("settings-screen")
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        LiteCard {
            SectionTitle("Engine controls", "Discovery and order permission are separate")
            SwitchRow(
                "Fixed + Working Range scanner",
                "OFF pauses research scans only. CNC GTT and MIS intraday stop/square-off monitoring continue.",
                state.settings.fixedScannerEnabled,
                vm::setFixedScannerEnabled,
                contentDescription = "Fixed market scanner"
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SwitchRow(
                "Fixed Auto-Buy",
                "New PRIMARY recommendations only · Alpha MIS intraday",
                state.settings.fixedAutoBuyEnabled,
                { if (it) onEnableRequested("Fixed") else vm.setFixedAutoBuy(false) },
                enabled = state.settings.fixedAutoBuyEnabled || canEnableAutoBuy,
                contentDescription = "Fixed Auto-Buy"
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SwitchRow(
                "Working Range Auto-Buy",
                "New two-scan confirmations only · separate pilot",
                state.settings.workingRangeEnabled,
                { if (it) onEnableRequested("Working Range") else vm.setWorkingRangeEnabled(false) },
                enabled = state.settings.workingRangeEnabled || canEnableAutoBuy,
                contentDescription = "Working Range Auto-Buy"
            )
            if (!canEnableAutoBuy) {
                Text(
                    "Auto-Buy enable requires authenticated Alpha, matching static IP, no safety halt and verified protection for every app-owned holding.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Text(
                "Inherited Fixed hard stop: -" + HardStopLossPolicy.MAX_LOSS_PCT +
                    "% from fill · trail arm +" + SteppedTrailingPolicy.ARM_TRIGGER_PCT +
                    "% · initial protected gain +" + SteppedTrailingPolicy.INITIAL_STOP_GAIN_PCT +
                    "% · ratchet " + SteppedTrailingPolicy.STEP_PCT + "%.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        LiteCard {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                SectionTitle("Alpha · execution account", "Groww credentials encrypted on device")
                StatusPill(if (alphaReady) "READY" else if (state.credentialsConfigured) "VERIFY" else "SETUP", if (alphaReady) LiteTone.POSITIVE else LiteTone.WARNING)
            }
            OutlinedTextField(
                alphaKey, { alphaKey = it },
                label = { Text("Alpha API key") },
                supportingText = { Text("Leave blank to keep the saved key") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                alphaTotp, { alphaTotp = it },
                label = { Text("Alpha TOTP secret") },
                supportingText = { Text("Leave blank to keep the saved secret") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                staticIp, { staticIp = it },
                label = { Text("Registered static public IP") },
                supportingText = { Text("Verified: " + state.settings.verifiedPublicIp.ifBlank { "Not verified" }) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                budget, { budget = it },
                label = { Text("Per-order budget (₹)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = {
                        vm.saveBrokerSettings(alphaKey, alphaTotp, staticIp, "", budget)
                        alphaKey = ""
                        alphaTotp = ""
                    },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                ) { Text("Save Alpha") }
                OutlinedButton(
                    onClick = vm::authenticate,
                    enabled = state.credentialsConfigured && !state.loading,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                ) { Text("Authenticate") }
            }
            Text(
                "Authentication: " + (if (state.settings.brokerAuthenticated) "verified" else "not verified") +
                    " · Static IP: " + (if (state.settings.staticIpMatched) "matched" else "not matched"),
                style = MaterialTheme.typography.labelMedium,
                color = if (alphaReady) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
        }

        LiteCard {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                SectionTitle("Beta · research account", "Read-only market data · never executes")
                StatusPill(if (state.betaAuthenticated) "READ-ONLY READY" else if (state.betaConfigured) "VERIFY" else "OPTIONAL", if (state.betaAuthenticated) LiteTone.INFO else LiteTone.NEUTRAL)
            }
            OutlinedTextField(
                betaKey, { betaKey = it },
                label = { Text("Beta API key") },
                supportingText = { Text("Must be different from Alpha") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                betaSecret, { betaSecret = it },
                label = { Text("Beta TOTP secret") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = {
                        vm.saveBetaSettings(betaKey, betaSecret)
                        betaKey = ""
                        betaSecret = ""
                    },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                ) { Text("Save Beta") }
                OutlinedButton(
                    onClick = vm::authenticateBeta,
                    enabled = state.betaConfigured && !state.loading,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                ) { Text("Authenticate") }
            }
            Text(
                "Beta market-data and authentication endpoints only. BUY, SELL, GTT and inventory endpoints are blocked.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        LiteCard {
            SectionTitle("Portfolio limits", "Execution guardrails")
            OutlinedTextField(
                capital, { capital = it },
                label = { Text("Maximum deployed capital (₹)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                holdings, { holdings = it },
                label = { Text("Maximum concurrent app-owned holdings") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = {
                    capital.toLongOrNull()?.let(vm::setMaxTotalCapital)
                    holdings.toIntOrNull()?.let(vm::setMaxConcurrentHoldings)
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            ) { Text("Save portfolio limits") }
        }

        LiteCard {
            SectionTitle("Safety and diagnostics", "Review before changing protection state")
            if (state.settings.safetyHalt || state.dashboard?.halted == true) {
                StatusPill("SAFETY HALT ACTIVE", LiteTone.NEGATIVE)
                OutlinedButton(onClick = onResetRequested, modifier = Modifier.fillMaxWidth()) {
                    Text("Review and reset safety halt")
                }
            } else {
                StatusPill("No local safety halt", LiteTone.POSITIVE)
            }
            Text(
                "Open-position monitoring is independent of the Fixed scanner. Never run two Auto-Buy app instances against the same Alpha account.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(onClick = onExport, modifier = Modifier.fillMaxWidth()) { Text("Export diagnostics ZIP") }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun SwitchRow(
    title: String,
    supporting: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    contentDescription: String
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp)
            .semantics { this.contentDescription = contentDescription },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(supporting, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

@Composable
private fun EmptyStateCard(title: String, message: String, icon: @Composable () -> Unit) {
    LiteCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(42.dp)
            ) {
                Box(contentAlignment = Alignment.Center) { icon() }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun prettyState(value: String): String =
    value.replace('_', ' ').lowercase(Locale.US).replaceFirstChar { it.uppercase(Locale.US) }

private fun ageLabel(ms: Long): String = when {
    ms == Long.MAX_VALUE || ms < 0L -> "Not available"
    ms < 1_000L -> "Just updated"
    ms < 60_000L -> (ms / 1_000L).toString() + "s ago"
    ms < 3_600_000L -> (ms / 60_000L).toString() + "m ago"
    else -> (ms / 3_600_000L).toString() + "h ago"
}

private fun timeLabel(ms: Long): String =
    if (ms <= 0L) "—" else SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(ms))

private fun rupees(value: Double?): String =
    value?.takeIf { it.isFinite() && it > 0.0 }?.let { "₹" + String.format(Locale.US, "%,.2f", it) } ?: "—"

private fun fmt(value: Double?): String =
    value?.takeIf { it.isFinite() }?.let { String.format(Locale.US, "%.2f", it) } ?: "—"

private fun pct(value: Double?): String =
    value?.takeIf { it.isFinite() }?.let { String.format(Locale.US, "%.2f%%", it) } ?: "—"

private fun num(value: Double?): String =
    value?.takeIf { it.isFinite() }?.let { String.format(Locale.US, "%.2f", it) } ?: "—"
