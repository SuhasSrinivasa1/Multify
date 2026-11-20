package com.multify.traderpro.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.multify.traderpro.engine.HardStopLossPolicy
import java.util.Locale

/** Dedicated UI entrypoint for the isolated Fixed/Working Range application.
 * No Forecast, Multify, Univest, notification listener or external-source trading controls.
 * Broker safety and Fixed's locked strategy remain in the shared back end.
 */
private enum class LitePage { FIXED, WORKING_RANGE, AVERAGES, SETTINGS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FixedLiteApp(viewModel: TraderViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var page by rememberSaveable { mutableStateOf(LitePage.FIXED) }
    var confirmBuySymbol by remember { mutableStateOf<String?>(null) }
    var confirmEnable by remember { mutableStateOf<String?>(null) }
    var confirmHaltReset by remember { mutableStateOf(false) }
    val snack = remember { SnackbarHostState() }
    val exportLogs = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri -> if (uri != null) viewModel.exportLogs(uri) }
    LaunchedEffect(state.message, state.error) {
        (state.error ?: state.message)?.takeIf { it.isNotBlank() }?.let {
            snack.showSnackbar(it)
            viewModel.clearBanner()
        }
    }
    Scaffold(
        snackbarHost = { SnackbarHost(snack) },
        topBar = {
            Column {
                CenterAlignedTopAppBar(
                    title = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Fixed Pro Lite", fontWeight = FontWeight.Bold)
                            Text(
                                when (page) {
                                    LitePage.FIXED -> "Frozen 15m Fixed"
                                    LitePage.WORKING_RANGE -> "Independent Working Range"
                                    LitePage.AVERAGES -> "Fixed outcomes only"
                                    LitePage.SETTINGS -> "Broker accounts and safety"
                                },
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { page = LitePage.AVERAGES }) {
                            Icon(Icons.Default.Analytics, contentDescription = "Averages")
                        }
                        IconButton(onClick = { page = LitePage.SETTINGS }) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                        IconButton(onClick = { viewModel.refresh() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                        }
                    }
                )
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        if (state.settings.brokerAuthenticated && state.settings.staticIpMatched)
                            "ALPHA · AUTH + IP OK" else "ALPHA · VERIFY",
                        style = MaterialTheme.typography.labelSmall
                    )
                    Text(
                        if (state.betaAuthenticated) "BETA · READ-ONLY READY"
                        else "BETA · VERIFY",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = page == LitePage.FIXED,
                    onClick = { page = LitePage.FIXED },
                    icon = { Icon(Icons.Default.Shield, contentDescription = "Fixed") },
                    label = { Text("Fixed") }
                )
                NavigationBarItem(
                    selected = page == LitePage.WORKING_RANGE,
                    onClick = { page = LitePage.WORKING_RANGE },
                    icon = { Icon(Icons.Default.Timeline, contentDescription = "Working Range") },
                    label = { Text("Working Range") }
                )
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (page) {
                LitePage.FIXED -> LiteFixedScreen(
                    state, viewModel::generateFixed,
                    onBuy = { confirmBuySymbol = it }
                )
                LitePage.WORKING_RANGE -> LiteRangeScreen(state)
                LitePage.AVERAGES -> LiteAveragesScreen(state)
                LitePage.SETTINGS -> LiteSettingsScreen(
                    state, viewModel,
                    onEnableRequested = { confirmEnable = it },
                    onResetRequested = { confirmHaltReset = true },
                    onExport = { exportLogs.launch("FixedLite-diagnostics.zip") }
                )
            }
            if (state.loading) {
                LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
            }
        }
    }
    confirmBuySymbol?.let { symbol ->
        AlertDialog(
            onDismissRequest = { confirmBuySymbol = null },
            title = { Text("Real Alpha CNC BUY?") },
            text = { Text("Execute a new Fixed BUY for $symbol only after the original fresh-quote, cash, no-duplicate and GTT safety checks. This can risk real money. No stale signal will be revived.") },
            confirmButton = {
                Button(onClick = {
                    confirmBuySymbol = null
                    viewModel.executeFixed(symbol)
                }) { Text("Submit guarded BUY") }
            },
            dismissButton = { TextButton(onClick = { confirmBuySymbol = null }) { Text("Cancel") } }
        )
    }
    confirmEnable?.let { source ->
        AlertDialog(
            onDismissRequest = { confirmEnable = null },
            title = { Text("Enable real $source Auto-Buy?") },
            text = {
                Text("New signals may place REAL Alpha CNC BUY orders, subject to all existing broker safety checks. This copy starts with a fresh local position database. Never run a second trading app against the same holdings. The inherited hard stop is -${HardStopLossPolicy.MAX_LOSS_PCT}% (earlier -2% requirements differ). Verify protection in Groww.")
            },
            confirmButton = {
                Button(onClick = {
                    confirmEnable = null
                    if (source == "Fixed") viewModel.setFixedAutoBuy(true)
                    else viewModel.setWorkingRangeEnabled(true)
                }) { Text("Enable $source") }
            },
            dismissButton = { TextButton(onClick = { confirmEnable = null }) { Text("Cancel") } }
        )
    }
    if (confirmHaltReset) AlertDialog(
        onDismissRequest = { confirmHaltReset = false },
        title = { Text("Reset safety halt?") },
        text = { Text("First reconcile broker positions, fills and protective GTTs. Resetting the halt does not enable trading.") },
        confirmButton = {
            Button(onClick = {
                confirmHaltReset = false
                viewModel.resetHalt()
            }) { Text("Reset halt") }
        },
        dismissButton = { TextButton(onClick = { confirmHaltReset = false }) { Text("Cancel") } }
    )
}

@Composable
private fun LiteCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
private fun LiteFixedScreen(
    state: TraderUiState,
    onScan: () -> Unit,
    onBuy: (String) -> Unit
) {
    val dto = state.dashboard
    val fixed = dto?.fixedForecasts.orEmpty().sortedWith(
        compareBy<com.multify.traderpro.data.network.ForecastDto> { it.rank }.thenByDescending { it.generatedAtMs }
    )
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            LiteCard("Fixed · FROZEN v7.4.1") {
                Text("40 factors · 15-minute completed candles · original PRIMARY confirmation and Fixed target.")
                Text(
                    "Scanner: " + if (state.settings.fixedScannerEnabled) "ON" else "PAUSED",
                    fontWeight = FontWeight.Medium
                )
                Text("Real Alpha Auto-Buy: " + if (state.settings.fixedAutoBuyEnabled) "ON" else "OFF")
                Text("Expected scan cadence ~90s · actual speed depends on broker API waits.")
                Text("Discovery: ${dto?.fixedHealth?.discovery ?: "—"} · NSE coverage ${pct(dto?.fixedHealth?.discoveryCoveragePct)}")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onScan, enabled = state.settings.fixedScannerEnabled) {
                        Text("Run Fixed scan")
                    }
                }
            }
        }
        item {
            LiteCard("Broker-owned position protection") {
                val positions = dto?.positions.orEmpty().filter { it.quantity > 0 }
                Text("${positions.size} app-owned open holdings · limit ${state.settings.maxConcurrentHoldings}")
                if (positions.isEmpty()) Text("No open app-owned holdings in this installation.")
                positions.forEach { p ->
                    Text("${p.symbol} · ${p.quantity} shares · GTT ${p.smartOrderStatus ?: "UNVERIFIED"}")
                }
                if (state.settings.safetyHalt || dto?.halted == true) {
                    Text("SAFETY HALT — check Groww and Settings.", color = MaterialTheme.colorScheme.error)
                }
            }
        }
        item {
            Text("Published Fixed recommendations (${fixed.size})", style = MaterialTheme.typography.titleMedium)
        }
        if (fixed.isEmpty()) {
            item { Text("No Fixed recommendation yet. Scans and account authentication must be healthy.") }
        }
        items(fixed) { row ->
            LiteCard("${row.symbol} · ${row.qualityTier} · ${row.status}") {
                Text("Reference ₹${money(row.entryPrice)}  ·  Target ₹${money(row.targetPrice)} (${pct(row.targetPct)})")
                Text("Score ${String.format(Locale.US, "%.3f", row.score)} · MFE ${pct(row.maxFavourablePct)} · MAE ${pct(row.maxAdversePct)}")
                Text(row.reason, style = MaterialTheme.typography.bodySmall, maxLines = 4, overflow = TextOverflow.Ellipsis)
                if (row.executionEligible && state.settings.fixedScannerEnabled) {
                    OutlinedButton(onClick = { onBuy(row.symbol) }) { Text("Guarded manual BUY · Alpha") }
                } else {
                    Text("No current executable BUY permission", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun LiteRangeScreen(state: TraderUiState) {
    val rows = state.dashboard?.workingRangeRecommendations.orEmpty()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            LiteCard("Working Range · Independent") {
                Text("Research uses the same existing 15-minute Fixed data; two independent matching scans are required.")
                Text("Research: " + if (state.settings.fixedScannerEnabled) "ON" else "PAUSED (Fixed scanner off)")
                Text("New Alpha Auto-Buy permission: " + if (state.settings.workingRangeEnabled) "ON" else "OFF · research only")
                Text("No separate full-market Groww discovery requests.")
            }
        }
        item { Text("Working Range observations (${rows.size})", style = MaterialTheme.typography.titleMedium) }
        if (rows.isEmpty()) item { Text("No Working Range candidate yet; research does not guarantee two-scan confirmation.") }
        items(rows) { row ->
            LiteCard("${row.symbol} · ${row.state}") {
                Text("First ₹${money(row.firstQualifiedPrice)} · confirmed/reference ₹${money(row.entryPrice)}")
                Text("Target ₹${money(row.targetPrice)} · drift ${pct(row.driftFromFirstQualifiedPct)}")
                Text("RSI ${pct(row.rsi)} · RVOL ${num(row.rvol)} · VWAP extension ${num(row.vwapExtensionAtr)} ATR")
                Text("MFE ${pct(row.maxFavourablePct)} · MAE ${pct(row.maxAdversePct)}")
                Text("Order: ${row.autoBuyState} · ${if (row.autoBuyEnabled) "event permission recorded" else "research only"}")
                Text(row.sampledOutcomeNote, style = MaterialTheme.typography.bodySmall)
                Text(row.reason, style = MaterialTheme.typography.bodySmall, maxLines = 4, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun LiteAveragesScreen(state: TraderUiState) {
    val fixed = state.dashboard?.fixedOutcomes
    val frozen = state.dashboard?.fixedSnapshot
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        LiteCard("Frozen Fixed reference") {
            Text("Original target ${pct(frozen?.fixedTargetPct)} · ${frozen?.learningCalls ?: 107} reference calls.")
            Text("Frozen reference does not learn from Working Range or change the 40-factor selection.")
        }
        LiteCard("Fixed · Observed outcomes") {
            Text("Published: ${fixed?.published ?: 0} · observed: ${fixed?.observed ?: 0}")
            Text("Today published: ${fixed?.todayPublished ?: 0} · observed: ${fixed?.todayObserved ?: 0}")
            Text("Trail-arm hits: ${fixed?.armHits ?: 0} · hit rate ${pct(fixed?.armHitRatePct)}")
            Text("Full target hits: ${fixed?.fullTargetHits ?: 0} · hit rate ${pct(fixed?.fullTargetHitRatePct)}")
            Text("Rolling observed average MFE: ${pct(fixed?.rollingObservedAveragePct)}")
            Text("Median observed MFE: ${pct(fixed?.medianPct)}")
            Text("These are sampled movements, not verified realized returns or continuous price paths.")
        }
        LiteCard("Working Range · Research") {
            val rows = state.dashboard?.workingRangeRecommendations.orEmpty()
            Text("${rows.size} retained Working Range rows in this installation.")
            Text("Research outcomes remain distinct from real broker P&L. No unsupported aggregate win rate is shown.")
        }
    }
}

@Composable
private fun LiteSettingsScreen(
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
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        LiteCard("Fixed scanner") {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Fixed market scanning", fontWeight = FontWeight.Medium)
                    Text("OFF pauses discovery and Working Range research; it never suspends GTT/SELL monitoring.", style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = state.settings.fixedScannerEnabled, onCheckedChange = vm::setFixedScannerEnabled)
            }
        }
        LiteCard("Execution permissions · Alpha only") {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Fixed Auto-Buy", fontWeight = FontWeight.Medium)
                    Text("Real CNC orders · never implied by scanner ON", style = MaterialTheme.typography.bodySmall)
                }
                Switch(
                    checked = state.settings.fixedAutoBuyEnabled,
                    onCheckedChange = { if (it) onEnableRequested("Fixed") else vm.setFixedAutoBuy(false) }
                )
            }
            HorizontalDivider()
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Working Range Auto-Buy", fontWeight = FontWeight.Medium)
                    Text("Only new post-activation two-scan confirmations", style = MaterialTheme.typography.bodySmall)
                }
                Switch(
                    checked = state.settings.workingRangeEnabled,
                    onCheckedChange = { if (it) onEnableRequested("Working Range") else vm.setWorkingRangeEnabled(false) }
                )
            }
            Text(
                "Inherited hard stop: -${HardStopLossPolicy.MAX_LOSS_PCT}%. Earlier -2% specification differs. Confirm actual broker protection before live use.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
            Text("No new BUY if app-owned protection is unverified. Beta cannot execute.", style = MaterialTheme.typography.bodySmall)
        }
        LiteCard("Alpha · trading and GTT protection") {
            Text("Credentials are entered only when changed; fields intentionally stay blank.")
            OutlinedTextField(alphaKey, { alphaKey = it }, label = { Text("Alpha Groww API key") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(alphaTotp, { alphaTotp = it }, label = { Text("Alpha TOTP secret") },
                visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(staticIp, { staticIp = it }, label = { Text("Registered static public IP") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(budget, { budget = it }, label = { Text("Per-order budget ₹") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.saveBrokerSettings(alphaKey, alphaTotp, staticIp, "", budget) }) { Text("Save Alpha") }
                OutlinedButton(onClick = vm::authenticate) { Text("Authenticate") }
            }
            Text("Verified IP: ${state.settings.verifiedPublicIp.ifBlank { "—" }} · DDPI: ${if (state.settings.brokerDdpiEnabled) "enabled" else "not verified"}", style = MaterialTheme.typography.bodySmall)
        }
        LiteCard("Beta · read-only research") {
            OutlinedTextField(betaKey, { betaKey = it }, label = { Text("Beta Groww API key") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(betaSecret, { betaSecret = it }, label = { Text("Beta TOTP secret") },
                visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.saveBetaSettings(betaKey, betaSecret) }) { Text("Save Beta") }
                OutlinedButton(onClick = vm::authenticateBeta) { Text("Authenticate") }
            }
            Text("Beta never sends BUY, SELL, GTT, or other trading orders.", style = MaterialTheme.typography.bodySmall)
        }
        LiteCard("Portfolio safeguards") {
            OutlinedTextField(capital, { capital = it }, label = { Text("Max total deployed ₹") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(holdings, { holdings = it }, label = { Text("Max app-owned concurrent holdings") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Button(onClick = {
                capital.toLongOrNull()?.let { vm.setMaxTotalCapital(it) }
                holdings.toIntOrNull()?.let { vm.setMaxConcurrentHoldings(it) }
            }) { Text("Save risk limits") }
            if (state.settings.safetyHalt) {
                Text("SAFETY HALT ACTIVE", color = MaterialTheme.colorScheme.error)
                OutlinedButton(onClick = onResetRequested) { Text("Review / reset safety halt") }
            }
            Text("Broker-side protection monitoring is independent of the Fixed scanner toggle.")
        }
        LiteCard("Diagnostics and deployment") {
            OutlinedButton(onClick = onExport) { Text("Export Fixed diagnostics ZIP") }
            Text("Separate package: never run two auto-trading app instances with the same Alpha account.", style = MaterialTheme.typography.bodySmall)
            Text("This installation has independent local order/holding records and cannot assume ownership of older app positions.", style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(12.dp))
    }
}

private fun pct(v: Double?): String = v?.let { String.format(Locale.US, "%.2f%%", it) } ?: "—"
private fun num(v: Double?): String = v?.let { String.format(Locale.US, "%.2f", it) } ?: "—"
private fun money(v: Double): String = String.format(Locale.US, "%.2f", v)
