package com.multify.traderpro.ui.screens

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.multify.traderpro.BuildConfig
import com.multify.traderpro.data.local.SignalEventEntity
import com.multify.traderpro.data.network.DashboardDto
import com.multify.traderpro.data.network.AdaptiveWaveDto
import com.multify.traderpro.data.network.PositionDto
import com.multify.traderpro.data.network.WaveSignalDto
import com.multify.traderpro.data.network.WaveStatDto
import com.multify.traderpro.engine.MultifyReverseEngineering
import com.multify.traderpro.ui.components.KeyValueRow
import com.multify.traderpro.ui.components.MetricCard
import com.multify.traderpro.ui.components.SectionTitle
import com.multify.traderpro.ui.components.StatusPill
import com.multify.traderpro.ui.components.StatusTone
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class Destination(val label: String, val icon: ImageVector) {
    Execution("Execution", Icons.Default.Dashboard),
    Forecast("Forecast", Icons.Default.Analytics),
    Configuration("Configuration", Icons.Default.Bolt),
    Settings("Settings", Icons.Default.Settings)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TraderApp(viewModel: TraderViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var selected by rememberSaveable { mutableIntStateOf(0) }
    val snackbar = remember { SnackbarHostState() }
    var showLiveConfirm by remember { mutableStateOf(false) }
    var showFastTrackConfirm by remember { mutableStateOf(false) }
    var showResetConfirm by remember { mutableStateOf(false) }
    val logExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) viewModel.exportLogs(uri)
    }

    LaunchedEffect(state.message, state.error) {
        val text = state.error ?: state.message
        if (!text.isNullOrBlank()) {
            snackbar.showSnackbar(text)
            viewModel.clearBanner()
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.statusBars,
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Multify Trader Pro", fontWeight = FontWeight.SemiBold)
                        Text(
                            "Execution · Forecast · Settings",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar(windowInsets = WindowInsets.navigationBars) {
                Destination.entries.forEachIndexed { index, destination ->
                    NavigationBarItem(
                        selected = selected == index,
                        onClick = { selected = index },
                        icon = { Icon(destination.icon, contentDescription = destination.label) },
                        label = { Text(destination.label) },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (Destination.entries[selected]) {
                Destination.Execution -> DashboardScreen(
                    state = state,
                    onRefresh = { viewModel.refresh() },
                    onLiveRequested = {
                        if (state.settings.liveExecutionEffective) viewModel.setLiveExecution(false) else showLiveConfirm = true
                    },
                    onResetHalt = { showResetConfirm = true }
                )
                Destination.Forecast -> ForecastScreen(state = state, onGenerate = viewModel::generateForecasts, onResearch = viewModel::runAfterHoursResearch)
                Destination.Configuration -> ConfigurationScreen(
                    state = state,
                    onBudgetChanged = viewModel::setIntradayBudget,
                    onExecutionMode = viewModel::setExecutionMode,
                    onWaveCount = viewModel::setWaveCount,
                    onWaveRiskChanged = viewModel::setWaveRiskSettings,
                    onSubmitManualSignal = viewModel::submitManualSignal
                )
                Destination.Settings -> SystemScreen(
                    state = state,
                    onSave = viewModel::saveBrokerSettings,
                    onAuthenticate = viewModel::authenticate,
                    onLiveRequested = {
                        if (state.settings.liveExecutionEffective) viewModel.setLiveExecution(false) else showLiveConfirm = true
                    },
                    onResetHalt = { showResetConfirm = true },
                    onExportLogs = { logExportLauncher.launch(defaultLogFileName()) },
                    onFirstWaveMode = viewModel::setFirstWaveMode,
                    onActiveWaveCount = viewModel::setActiveWaveCount
                )
            }
            if (state.loading) {
                LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
            }
        }
    }

    if (showLiveConfirm) {
        AlertDialog(
            onDismissRequest = { showLiveConfirm = false },
            icon = { Icon(Icons.Default.Shield, contentDescription = null) },
            title = { Text("Enable LIVE buy & sell?") },
            text = {
                Text(
                    "Eligible Multify NSE CASH signals can place real Groww MIS orders from this phone. " +
                        "Peak notional is capped by your selected intraday budget (${money(state.settings.dailyBudgetRupees.toDouble())}). " +
                        "Only app-owned orders count toward Multify P&L; your other Groww trades are excluded. " +
                        "₹5,000 is a milestone, not a ceiling. The engine becomes defensive near -₹1,500 and starts emergency risk reduction before the -₹2,500 hard cap."
                )
            },
            confirmButton = {
                Button(onClick = {
                    showLiveConfirm = false
                    viewModel.setLiveExecution(true)
                }) { Text("Enable live") }
            },
            dismissButton = { TextButton(onClick = { showLiveConfirm = false }) { Text("Cancel") } }
        )
    }

    if (showFastTrackConfirm) {
        AlertDialog(
            onDismissRequest = { showFastTrackConfirm = false },
            icon = { Icon(Icons.Default.Bolt, contentDescription = null) },
            title = { Text("Enable Manual auto buy & sell?") },
            text = {
                Text(
                    "Paid Multify Equity BUY notifications can place real Groww CNC delivery buys up to ${money(state.settings.fastTrackBudgetRupees.toDouble())}. " +
                        "The matching Book Profit notification sells only the quantity bought by this app. If enabled, a separate protected MIS short overlay may follow the sell. Free notifications are ignored."
                )
            },
            confirmButton = {
                Button(onClick = {
                    showFastTrackConfirm = false
                    viewModel.setFastTrackExecution(true)
                }) { Text("Enable Manual") }
            },
            dismissButton = { TextButton(onClick = { showFastTrackConfirm = false }) { Text("Cancel") } }
        )
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Reset safety halt?") },
            text = { Text("Only reset after you have checked the broker position and confirmed that every open quantity is protected or flat. The system will remain disarmed after reset.") },
            confirmButton = {
                Button(onClick = {
                    showResetConfirm = false
                    viewModel.resetHalt()
                }) { Text("Reset halt") }
            },
            dismissButton = { TextButton(onClick = { showResetConfirm = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun DashboardScreen(
    state: TraderUiState,
    onRefresh: () -> Unit,
    onLiveRequested: () -> Unit,
    onResetHalt: () -> Unit
) {
    val context = LocalContext.current
    val dashboard = state.dashboard
    val notificationAccess = notificationAccessEnabled(context)
    val live = state.settings.liveExecutionEffective && dashboard?.halted != true
    val configured = state.credentialsConfigured && state.settings.dailyBudgetRupees >= 10_000L

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp, 14.dp, 16.dp, 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            HeroStatusCard(
                dashboard = dashboard,
                live = live,
                configured = configured,
                budget = state.settings.dailyBudgetRupees.toDouble(),
                authenticated = state.settings.brokerAuthenticated,
                staticIpMatched = state.settings.staticIpMatched,
                onLiveRequested = onLiveRequested,
                onResetHalt = onResetHalt
            )
        }
        if (!notificationAccess) {
            item {
                ActionBanner(
                    title = "Notification access required",
                    body = "Enable notification access so Multify releases, book-profit alerts and safety events can be captured immediately.",
                    icon = Icons.Default.Warning,
                    action = "Open settings",
                    onClick = { openNotificationAccessSettings(context) },
                    negative = true
                )
            }
        }
        if (!state.settings.brokerAuthenticated) {
            item {
                ActionBanner(
                    title = if (state.credentialsConfigured) "Groww authentication required" else "Groww credentials required",
                    body = if (state.credentialsConfigured)
                        "Open System and tap Refresh & Authenticate. Live execution remains blocked until the Groww session and static IP are verified."
                    else "Save your Groww TOTP token, TOTP secret, static IP and daily budget in System.",
                    icon = Icons.Default.CloudDone,
                    action = "Refresh",
                    onClick = onRefresh
                )
            }
        }
        if (dashboard != null) {
            item { SectionTitle("Today's execution audit", "Shadow always mirrors the selected fixed capital and wave depth. App P&L contains only Multify Trader Pro-owned positions.") }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    MetricCard(
                        label = "Realised P&L",
                        value = money(dashboard.summary.realisedPnl),
                        supporting = "₹5,000 milestone · no upside cap",
                        modifier = Modifier.weight(1f),
                        accent = pnlColor(dashboard.summary.realisedPnl)
                    )
                    MetricCard(
                        label = "Unrealised",
                        value = money(dashboard.summary.unrealisedPnl),
                        supporting = "${money(dashboard.summary.grossExposure)} exposure",
                        modifier = Modifier.weight(1f),
                        accent = pnlColor(dashboard.summary.unrealisedPnl)
                    )
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    MetricCard(
                        label = "Daily budget",
                        value = money(state.settings.dailyBudgetRupees.toDouble()),
                        supporting = "peak notional cap",
                        modifier = Modifier.weight(1f)
                    )
                    MetricCard(
                        label = "Market",
                        value = dashboard.marketSession,
                        supporting = if (state.settings.staticIpMatched) "Static IP verified" else "Static IP not verified",
                        modifier = Modifier.weight(1f),
                        accent = if (dashboard.marketSession == "OPEN") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    MetricCard(
                        label = "Risk mode",
                        value = dashboard.risk.riskMode,
                        supporting = "soft -₹1,500 · hard -₹2,500",
                        modifier = Modifier.weight(1f),
                        accent = if (dashboard.risk.riskMode == "NORMAL" || dashboard.risk.riskMode == "MILESTONE+") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                    MetricCard(
                        label = "Shadow qualification",
                        value = "${dashboard.shadowQualificationDays}/5 days",
                        supporting = "₹5,000 net/day on fixed ₹2L",
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            if (dashboard.fastTrackSummary.trades > 0 || state.settings.fastTrackEffective) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .25f)),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Column(Modifier.padding(15.dp)) {
                            Text("Manual / Fast Track", fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(6.dp))
                            KeyValueRow("App-only realised", money(dashboard.fastTrackSummary.realisedPnl), pnlColor(dashboard.fastTrackSummary.realisedPnl))
                            KeyValueRow("App-only unrealised", money(dashboard.fastTrackSummary.unrealisedPnl), pnlColor(dashboard.fastTrackSummary.unrealisedPnl))
                            KeyValueRow("Budget", money(state.settings.fastTrackBudgetRupees.toDouble()))
                        }
                    }
                }
            }

            item { SectionTitle("Active positions", "These are app-owned positions only. If the broker net quantity diverges because of manual trading in the same MIS symbol, the engine halts rather than touching your external position.") }
            if (dashboard.positions.isEmpty()) {
                item { EmptyState("No open intraday positions", "No app-owned position is open. Baseline Shadow still records every paid Multify BUY; LIVE orders require explicit daily arming.") }
            } else {
                items(dashboard.positions, key = { it.symbol }) { PositionCard(it) }
            }

            item { SectionTitle("AUTO adaptive direction", "At each eligible 2% Wave 2+ checkpoint, LONG, SHORT and HOLD compete on after-cost expected value. Only one real direction can be active.") }
            if (dashboard.adaptiveWaves.isEmpty()) {
                item { EmptyState("Waiting for Wave 2+", "A 2% displacement makes ₹${state.settings.waveCapitalRupees} eligible; it does not automatically create an order.") }
            } else {
                items(dashboard.adaptiveWaves.take(12), key = { "${it.symbol}-${it.waveNumber}" }) { AdaptiveWaveCard(it) }
            }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha=.45f)), shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("Execution health", fontWeight = FontWeight.SemiBold)
                        KeyValueRow("Listener", dashboard.health.listener)
                        KeyValueRow("Broker", dashboard.health.broker)
                        KeyValueRow("Market data", dashboard.health.marketData)
                        KeyValueRow("Symbol master", dashboard.health.symbolMaster)
                        KeyValueRow("Execution service", dashboard.health.foregroundService)
                        KeyValueRow("Reconnect count", dashboard.health.reconnectCount.toString())
                        KeyValueRow("Last event latency", if (dashboard.health.lastEventProcessingLatencyMs > 0) "${dashboard.health.lastEventProcessingLatencyMs} ms" else "No event yet")
                        KeyValueRow("Market-data age", if (dashboard.health.marketDataAgeMs < Long.MAX_VALUE / 2) "${dashboard.health.marketDataAgeMs} ms" else "No fresh quote")
                    }
                }
            }

            item { SectionTitle("10-wave LONG / SHORT averages", "Wave 1 LONG is the rolling last-30-trading-day Multify average. Waves 2–10 learn independently. “Learning” means the row exists but there is not enough evidence yet.") }
            item { RollingLearningSummary(dashboard.learning) }
            item { WaveAveragesTable(dashboard.waveStats, dashboard.learning) }

            item { SectionTitle("Recent engine decisions", "Every action is auditable. Wave pivots learn independently while fixed-capital execution never averages down or adds notional after entry.") }
            if (dashboard.recentDecisions.isEmpty()) {
                item { EmptyState("No decisions yet", "Captured Multify signals will appear here after local analysis.") }
            } else {
                items(dashboard.recentDecisions.take(8)) { d ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.30f)),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Column(Modifier.padding(15.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(d.symbol ?: "System", fontWeight = FontWeight.SemiBold)
                                StatusPill(d.action.replace('_', ' '), decisionTone(d.action))
                            }
                            Spacer(Modifier.height(7.dp))
                            Text(d.reason, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (!d.strategy.isNullOrBlank()) {
                                Spacer(Modifier.height(7.dp))
                                Text("Strategy · ${d.strategy}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroStatusCard(
    dashboard: DashboardDto?,
    live: Boolean,
    configured: Boolean,
    budget: Double,
    authenticated: Boolean,
    staticIpMatched: Boolean,
    onLiveRequested: () -> Unit,
    onResetHalt: () -> Unit
) {
    val halted = dashboard?.halted == true
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Direct Groww execution", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        when {
                            halted -> "Safety halt — review required"
                            live -> "LIVE buy & sell enabled"
                            authenticated -> "Continuous shadow trading · live off"
                            configured -> "Saved · authentication required"
                            else -> "Setup required"
                        },
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                Icon(
                    when {
                        halted -> Icons.Default.Error
                        live -> Icons.Default.Bolt
                        else -> Icons.Default.PauseCircle
                    },
                    contentDescription = null,
                    tint = when {
                        halted -> MaterialTheme.colorScheme.error
                        live -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(34.dp)
                )
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusPill(if (live) "LIVE" else "PAPER", if (live) StatusTone.Warning else StatusTone.Info)
                StatusPill(if (authenticated) "GROWW AUTH" else "AUTH REQUIRED", if (authenticated) StatusTone.Positive else StatusTone.Warning)
                StatusPill(if (staticIpMatched) "STATIC IP OK" else "IP CHECK", if (staticIpMatched) StatusTone.Positive else StatusTone.Warning)
            }
            Spacer(Modifier.height(10.dp))
            KeyValueRow("Intraday budget", money(budget))
            KeyValueRow("Profit milestone", "₹5,000 · runners allowed")
            KeyValueRow("Daily loss bands", "-₹1,500 defensive · -₹2,500 hard")
            dashboard?.risk?.let { KeyValueRow("Risk mode", it.riskMode) }
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = if (halted) onResetHalt else onLiveRequested,
                enabled = configured && (halted || live || (authenticated && staticIpMatched)),
                colors = if (live || halted) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error) else ButtonDefaults.buttonColors(),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Icon(if (halted) Icons.Default.Warning else if (live) Icons.Default.PauseCircle else Icons.Default.Shield, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text(if (halted) "Reset safety halt" else if (live) "Disable live execution" else "Enable live buy & sell")
            }
        }
    }
}

@Composable
private fun ActionBanner(
    title: String,
    body: String,
    icon: ImageVector,
    action: String,
    onClick: () -> Unit,
    negative: Boolean = false
) {
    val accent = if (negative) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary
    Card(
        colors = CardDefaults.cardColors(containerColor = accent.copy(alpha = 0.08f)),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.30f)),
        shape = RoundedCornerShape(18.dp)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = accent, modifier = Modifier.size(26.dp))
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(3.dp))
                Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = onClick) { Text(action) }
        }
    }
}

@Composable
private fun PositionCard(p: PositionDto) {
    val sideTone = if (p.side.uppercase() == "LONG") StatusTone.Positive else StatusTone.Warning
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.30f)),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(p.symbol, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("${p.quantity} shares · avg ${money(p.averagePrice)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                StatusPill(p.side.uppercase(), sideTone)
            }
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("P&L", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(money(p.pnl), fontWeight = FontWeight.Bold, color = pnlColor(p.pnl))
            }
            HorizontalDivider(Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f))
            p.ltp?.let { KeyValueRow("LTP", money(it)) }
            p.targetPrice?.let { KeyValueRow("Target", money(it), MaterialTheme.colorScheme.primary) }
            p.stopPrice?.let { KeyValueRow("Stop", money(it), MaterialTheme.colorScheme.error) }
            p.strategy?.let { KeyValueRow("Strategy", it) }
        }
    }
}

@Composable
private fun AdaptiveWaveCard(w: AdaptiveWaveDto) {
    val tone = when (w.decision) {
        "LONG", "SHORT" -> StatusTone.Positive
        else -> StatusTone.Warning
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .28f)),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("WAVE ${w.waveNumber} · ${w.symbol}", fontWeight = FontWeight.SemiBold)
                    Text("Stock move ${String.format(Locale.US, "%.2f%%", w.triggerPct)} · ${w.regime.replace('_',' ')}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                StatusPill(w.decision, tone)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricCard("LONG", String.format(Locale.US, "%.0f%%", w.longProbability * 100.0), "EV ${money(w.evLongRupees)}", Modifier.weight(1f))
                MetricCard("SHORT", String.format(Locale.US, "%.0f%%", w.shortProbability * 100.0), "EV ${money(w.evShortRupees)}", Modifier.weight(1f))
            }
            KeyValueRow("Confidence", String.format(Locale.US, "%.0f%%", w.confidence * 100.0))
            KeyValueRow("Wave capital", money(w.waveCapitalRupees))
            KeyValueRow("Campaign used", money(w.campaignCapitalUsed))
            KeyValueRow("Campaign remaining", money(w.campaignCapitalRemaining))
            KeyValueRow("Real order", if (w.actualOrderSubmitted) "SUBMITTED" else "NO")
            Text(w.reasons.ifBlank { "No positive feature summary available" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 4, overflow = TextOverflow.Ellipsis)
            Text("Alternative: ${w.alternativeRejected}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun RollingLearningSummary(learning: com.multify.traderpro.data.network.LearningStatsDto) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .26f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .20f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("Rolling 30-session LONG baseline", fontWeight = FontWeight.SemiBold)
            KeyValueRow(
                "Wave 1 LONG average",
                if (learning.rollingCalls > 0) String.format(Locale.US, "%.2f%%", learning.longAveragePct) else "Learning"
            )
            KeyValueRow("Wave 1 sample count", learning.rollingCalls.toString())
            KeyValueRow("Mean", if (learning.rollingCalls > 0) String.format(Locale.US, "%.2f%%", learning.longAveragePct) else "Learning")
            KeyValueRow("Median", String.format(Locale.US, "%.2f%%", learning.longMedianPct))
            KeyValueRow("Trimmed mean", String.format(Locale.US, "%.2f%%", learning.longTrimmedMeanPct))
            KeyValueRow("EWMA", String.format(Locale.US, "%.2f%%", learning.longEwmaPct))
            KeyValueRow("P25 / P75", String.format(Locale.US, "%.2f%% / %.2f%%", learning.longP25Pct, learning.longP75Pct))
            KeyValueRow("Calls / trading days", "${learning.rollingCalls} / ${learning.rollingTradingDays}")
        }
    }
}

@Composable
private fun WaveAveragesTable(
    rows: List<WaveStatDto>,
    learning: com.multify.traderpro.data.network.LearningStatsDto
) {
    val byWave = rows.associateBy { it.wave }
    val displayRows = (1..10).map { wave -> byWave[wave] ?: WaveStatDto(wave = wave) }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .24f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Wave", modifier = Modifier.weight(.62f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Text("LONG avg", modifier = Modifier.weight(1.08f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Text("N", modifier = Modifier.weight(.42f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Text("SHORT avg", modifier = Modifier.weight(1.13f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Text("N", modifier = Modifier.weight(.42f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .20f))
            displayRows.forEachIndexed { index, w ->
                val longAvg = when {
                    w.wave == 1 && learning.rollingCalls <= 0 -> "Learning"
                    w.averageUpPct != null -> String.format(Locale.US, "%.2f%%", w.averageUpPct)
                    else -> "Learning"
                }
                val shortAvg = w.averageDownPct?.let { String.format(Locale.US, "%.2f%%", it) } ?: "Learning"
                val longN = if (w.wave == 1) learning.rollingCalls else w.upSamples
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(w.wave.toString(), modifier = Modifier.weight(.62f), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                    Text(longAvg, modifier = Modifier.weight(1.08f), style = MaterialTheme.typography.bodySmall)
                    Text(if (longN > 0) longN.toString() else "—", modifier = Modifier.weight(.42f), style = MaterialTheme.typography.bodySmall)
                    Text(shortAvg, modifier = Modifier.weight(1.13f), style = MaterialTheme.typography.bodySmall)
                    Text(if (w.downSamples > 0) w.downSamples.toString() else "—", modifier = Modifier.weight(.42f), style = MaterialTheme.typography.bodySmall)
                }
                if (index != displayRows.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .10f))
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .20f))
            Text(
                "LONG avg = average upside move. Wave 1 LONG = rolling last 30 Multify recommendation trading days (entry→target/history + live). SHORT avg = average downside move observed for that wave, not realised broker P&L. Waves with no evidence remain visible as Learning. N = observations.",
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun WaveSignalCard(w: WaveSignalDto) {
    val green = w.greenSide.uppercase()
    val modeTone = if (w.executionMode == "AUTO_ELIGIBLE") StatusTone.Positive else StatusTone.Info
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .28f)),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("${w.symbol} · next Wave ${w.nextWave}", fontWeight = FontWeight.SemiBold)
                    Text("${String.format(Locale.US, "%.0f", w.triggerDistancePct)}% anchor checkpoint · ${w.focusState.replace('_', ' ')}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                StatusPill(w.executionMode.replace('_', ' '), modeTone)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                StatusPill("LONG ${String.format(Locale.US, "%.0f%%", w.longProbability * 100.0)}", if (green == "LONG") StatusTone.Positive else StatusTone.Neutral)
                StatusPill("SHORT ${String.format(Locale.US, "%.0f%%", w.shortProbability * 100.0)}", if (green == "SHORT") StatusTone.Positive else StatusTone.Neutral)
                if (green == "HOLD") StatusPill("HOLD", StatusTone.Warning)
            }
            KeyValueRow("Wave 1 mode", w.firstWaveMode)
            KeyValueRow("Rolling long average", String.format(Locale.US, "%.2f%%", w.learnedLongAveragePct))
            KeyValueRow("Rolling short average", String.format(Locale.US, "%.0f%% of prior long move", w.learnedShortRetracementPct))
            Text(w.reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun SignalsScreen(events: List<SignalEventEntity>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp, 14.dp, 16.dp, 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { SectionTitle("Signal journal", "Raw Multify notifications are parsed, persisted and analyzed locally. Real Groww orders are possible only when LIVE buy & sell is enabled.") }
        if (events.isEmpty()) {
            item { EmptyState("No captured signals", "When Multify posts an equity intraday notification, it will appear here.") }
        } else {
            items(events, key = { it.id }) { event -> SignalEventCard(event) }
        }
    }
}

@Composable
private fun SignalEventCard(event: SignalEventEntity) {
    val tone = when (event.forwardingState) {
        "DELIVERED" -> StatusTone.Positive
        "SENDING" -> StatusTone.Info
        "RETRY" -> StatusTone.Warning
        else -> StatusTone.Neutral
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.28f)),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(Modifier.padding(15.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.SignalCellularAlt, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(19.dp))
                    Spacer(Modifier.size(8.dp))
                    Text(event.symbol ?: event.signalType.replace('_', ' '), fontWeight = FontWeight.SemiBold)
                }
                StatusPill(event.forwardingState, tone)
            }
            Spacer(Modifier.height(8.dp))
            Text(event.summary, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                "${formatTime(event.receivedAtMs)} · ${event.appLabel}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!event.backendAction.isNullOrBlank()) {
                Spacer(Modifier.height(9.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.20f))
                Spacer(Modifier.height(9.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Analytics, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(7.dp))
                    Text(event.backendAction.replace('_', ' '), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                }
                if (!event.backendReason.isNullOrBlank()) {
                    Text(event.backendReason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
            }
            if (!event.lastError.isNullOrBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(event.lastError, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun StrategiesScreen(state: TraderUiState) {
    val learning = state.dashboard?.learning ?: com.multify.traderpro.data.network.LearningStatsDto()
    val insights = state.dashboard?.strategyInsights.orEmpty()
    val liveFamilies = listOf(
        "Multify event prior + anchored VWAP",
        "VWAP continuation, reclaim and mean reversion",
        "EMA 9/20 trend separation + price-structure slope",
        "MACD momentum confirmation",
        "1m / 5m / 15m opening structure and breakout / breakdown",
        "Donchian breakout / breakdown",
        "ATR expansion + volatility-adjusted risk",
        "Bollinger compression → volume breakout",
        "Relative-volume price impulse",
        "Order-book / total buy-vs-sell imbalance when Groww supplies depth",
        "Failed breakout / failed breakdown liquidity sweeps",
        "RSI trend, exhaustion and chase filters",
        "Candlestick confirmation: engulfing, pin bars, marubozu",
        "52-week / market-cap context and day momentum",
        "Dynamic regime switch: trend / compression / mean reversion / mixed"
    )
    val controls = listOf(
        "Deterministic Multify Follow shadow opens immediately on every eligible paid Equity call",
        "Multify-only fill ledger — unrelated Groww trades excluded",
        "Shadow capital fixed at ₹2,00,000; live capital selectable ₹10k–₹2L",
        "Rolling 30-trading-day learned long target; uploaded history seeds the model",
        "Post-sell short retracement starts at 100%, never exceeds 100%, and learns downward from reconstructed + live outcomes",
        "No uncontrolled averaging: a 2% Wave 2+ checkpoint may deploy only the configured tranche after LONG/SHORT/HOLD approval and only inside the campaign/loss caps",
        "₹5,000 is a milestone, never a profit ceiling",
        "-₹1,500 soft loss band; -₹2,500 hard cap with pre-emptive live reduction",
        "Broker-side OCO protection on every app MIS fill; external-position conflicts halt automation"
    )
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp, 14.dp, 16.dp, 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { SectionTitle("Dynamic strategy ensemble", "Every strategy vote is logged. The baseline Shadow follows Multify immediately; the independent ensemble runs alongside it so we can measure whether our intelligence adds value.") }
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .35f)),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("Rolling learning", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    KeyValueRow("30-day long target", String.format(Locale.US, "%.2f%%", learning.longAveragePct))
                    KeyValueRow("30-day median", String.format(Locale.US, "%.2f%%", learning.longMedianPct))
                    KeyValueRow("Uploaded 3-month seed", String.format(Locale.US, "%.2f%%", learning.seededThreeMonthAveragePct))
                    KeyValueRow("Short retracement target", String.format(Locale.US, "%.0f%% of prior long move", learning.shortRetracementPct))
                    KeyValueRow("Short observations", learning.shortObservedCalls.toString())
                    Text("The short learner remains at 100% until enough live post-sell observations exist. It is capped at 100% and can only adapt downward.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (insights.isNotEmpty()) {
            item { Text("Recent strategy evaluations", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
            items(insights.take(8)) { x ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha=.22f))) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${x.symbol} · ${x.side}", fontWeight = FontWeight.SemiBold)
                            Text(String.format(Locale.US, "%.0f%%", x.confidence * 100.0), color = MaterialTheme.colorScheme.primary)
                        }
                        Text("${x.strategy} · ${x.regime}", style = MaterialTheme.typography.bodyMedium)
                        Text("LTP ${money(x.ltp)} · RSI ${x.rsi?.let { String.format(Locale.US, "%.1f", it) } ?: "—"} · RVOL ${x.rvol?.let { String.format(Locale.US, "%.2f", it) } ?: "—"} · book ${x.orderBookImbalance?.let { String.format(Locale.US, "%+.2f", it) } ?: "—"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        item { StrategyGroup("Strategy families", liveFamilies, Icons.Default.Analytics) }
        item { StrategyGroup("Execution & risk controls", controls, Icons.Default.Shield) }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f)), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Science, null, tint = MaterialTheme.colorScheme.secondary); Spacer(Modifier.size(9.dp)); Text("Shadow qualification", fontWeight = FontWeight.SemiBold) }
                    Spacer(Modifier.height(8.dp))
                    Text("Shadow always uses ₹2,00,000. Each eligible Multify call now opens a virtual Multify Follow campaign immediately, with high-resolution event sampling and complete strategy attribution. Five ₹5,000+ net sessions remain a milestone, not a guarantee or a ceiling.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun ForecastScreen(state: TraderUiState, onGenerate: () -> Unit, onResearch: () -> Unit) {
    val dashboard = state.dashboard
    val rows = dashboard?.forecasts.orEmpty()
    val learning = dashboard?.learning ?: com.multify.traderpro.data.network.LearningStatsDto()
    val report = dashboard?.research ?: com.multify.traderpro.data.network.ResearchDto()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp, 14.dp, 16.dp, 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { SectionTitle("Forecast · five daily candidates", "Five timestamped candidates are frozen from the rolling Multify-like universe. Matching Multify later is evaluation data, not an input to rewrite the forecast.") }
        item {
            val learned = state.dashboard?.learning ?: com.multify.traderpro.data.network.LearningStatsDto()
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha=.28f)), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("Current learned targets", fontWeight = FontWeight.SemiBold)
                    KeyValueRow("Long mean", String.format(Locale.US, "%.2f%%", learned.longAveragePct))
                    KeyValueRow("Long median", String.format(Locale.US, "%.2f%%", learned.longMedianPct))
                    KeyValueRow("Long trimmed mean", String.format(Locale.US, "%.2f%%", learned.longTrimmedMeanPct))
                    KeyValueRow("Long EWMA", String.format(Locale.US, "%.2f%%", learned.longEwmaPct))
                    KeyValueRow("Long P25 / P75", String.format(Locale.US, "%.2f%% / %.2f%%", learned.longP25Pct, learned.longP75Pct))
                    KeyValueRow("Short move capture", String.format(Locale.US, "%.0f%% of prior long move", learned.shortRetracementPct))
                    Text("Long target rolls over the latest 30 recommendation trading days using Multify entry-to-target %. Short learning uses reconstructed history when Groww data is available plus live post-sell observations; 100% is the hard maximum.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = onGenerate, modifier = Modifier.weight(1f)) { Text("Generate 5") }
                OutlinedButton(onClick = onResearch, modifier = Modifier.weight(1f)) { Text("Run replay") }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha=.45f)), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Learning window", fontWeight = FontWeight.SemiBold)
                    KeyValueRow("Trading days", learning.rollingTradingDays.toString())
                    KeyValueRow("Calls", learning.rollingCalls.toString())
                    KeyValueRow("Long target", String.format(Locale.US, "%.2f%%", learning.longAveragePct))
                    KeyValueRow("Short retracement", String.format(Locale.US, "%.0f%%", learning.shortRetracementPct))
                }
            }
        }
        item { MultifyDnaResearch() }
        if (rows.isEmpty()) {
            item { EmptyState("No forecast yet", "Authenticate Groww and tap Generate 5. During market hours the listener also creates the day's five candidates automatically.") }
        } else {
            items(rows.sortedBy { it.rank }) { f ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha=.24f)), shape = RoundedCornerShape(20.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("#${f.rank}  ${f.symbol}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            StatusPill(f.bias, if (f.bias == "LONG") StatusTone.Positive else StatusTone.Warning)
                        }
                        Text(String.format(Locale.US, "Confidence %.0f%% · score %.2f", f.confidence * 100.0, f.score), style = MaterialTheme.typography.bodyMedium)
                        Text(f.reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (f.multifyMatched) Text(if (f.multifyDirectionMatched) "✓ Multify later matched symbol + direction" else "• Multify later matched symbol", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha=.30f)), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("After-market research", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(report.title, fontWeight = FontWeight.Medium)
                    Text(report.summary.ifBlank { "The first report is generated after market close or when you tap Run replay." }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun MultifyDnaResearch() {
    val study = MultifyReverseEngineering
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle(
            "Multify DNA · positive-close research",
            "20 profitable Multify BUY examples whose NSE sessions also closed positive. Fundamentals are research priors only; no hindsight enters live execution."
        )
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha=.22f)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha=.22f)),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("What the winners suggest", fontWeight = FontWeight.SemiBold)
                study.hypotheses.forEach { h ->
                    Text("• $h", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha=.22f)),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(Modifier.fillMaxWidth().padding(12.dp)) {
                Row(Modifier.fillMaxWidth().padding(bottom = 7.dp)) {
                    Text("Stock / day", Modifier.weight(1.0f), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Text("Close", Modifier.weight(.62f), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Text("Fundamental snapshot", Modifier.weight(2.15f), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha=.20f))
                study.positiveCases.forEachIndexed { index, x ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1.0f)) {
                            Text(x.symbol, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                            Text(x.callDate.substring(5), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(String.format(Locale.US, "%+.2f%%", x.sessionCloseChangePct), Modifier.weight(.62f), style = MaterialTheme.typography.bodySmall, color = pnlColor(x.sessionCloseChangePct))
                        Column(Modifier.weight(2.15f)) {
                            Text(x.snapshot, style = MaterialTheme.typography.bodySmall)
                            Text(x.fundamentalFact, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    if (index != study.positiveCases.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha=.08f))
                }
                Text(
                    "Filter: positive Multify result + positive full-session close. Public fundamental snapshots are current/recent and are used only to form hypotheses; future live weights require both winners and losers plus walk-forward validation.",
                    modifier = Modifier.padding(top = 9.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun StrategyGroup(title: String, entries: List<String>, icon: ImageVector) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.28f)),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.size(9.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(12.dp))
            entries.forEachIndexed { index, entry ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
                    Box(
                        Modifier.padding(top = 7.dp).size(6.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(50))
                    )
                    Spacer(Modifier.size(10.dp))
                    Text(entry, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                }
                if (index != entries.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.13f))
            }
        }
    }
}

@Composable
private fun ConfigurationScreen(
    state: TraderUiState,
    onBudgetChanged: (Long) -> Unit,
    onExecutionMode: (String) -> Unit,
    onWaveCount: (Int) -> Unit,
    onWaveRiskChanged: (Double, Long, Int, Long, Long, Long) -> Unit,
    onSubmitManualSignal: (String, String, String) -> Unit
) {
    var budget by remember(state.settings.dailyBudgetRupees) { mutableStateOf(state.settings.dailyBudgetRupees.toFloat()) }
    var waveSpacing by remember(state.settings.waveSpacingBps) { mutableStateOf(state.settings.waveSpacingPercent.toFloat()) }
    var waveCapital by remember(state.settings.waveCapitalRupees) { mutableStateOf(state.settings.waveCapitalRupees.toFloat()) }
    var campaignCap by remember(state.settings.maxCampaignCapitalRupees) { mutableStateOf(state.settings.maxCampaignCapitalRupees.toFloat()) }
    var dailyLoss by remember(state.settings.maxDailyLossRupees) { mutableStateOf(state.settings.maxDailyLossRupees.toFloat()) }
    var stockLoss by remember(state.settings.maxSingleStockLossRupees) { mutableStateOf(state.settings.maxSingleStockLossRupees.toFloat()) }
    var symbol by remember { mutableStateOf("") }
    var observedPrice by remember { mutableStateOf("") }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp, 14.dp, 16.dp, 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { SectionTitle("Execution control", "Fast deterministic Multify follower. Forecasting and research never sit on the execution fast lane.") }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .28f)), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Execution mode", fontWeight = FontWeight.SemiBold)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("AUTO" to "AUTO", "LONG ONLY" to "LONG_ONLY", "SHORT ONLY" to "SHORT_ONLY").forEach { (label, mode) ->
                            if (state.settings.executionMode == mode) Button(onClick = { onExecutionMode(mode) }, modifier = Modifier.weight(1f)) { Text(label) }
                            else OutlinedButton(onClick = { onExecutionMode(mode) }, modifier = Modifier.weight(1f)) { Text(label) }
                        }
                    }
                    Text("Waves: ${state.settings.waveCount} / 20", fontWeight = FontWeight.SemiBold)
                    Slider(value = state.settings.waveCount.toFloat(), onValueChange = { onWaveCount(it.toInt().coerceIn(1, 20)) }, valueRange = 1f..20f, steps = 18)
                    Text("A wave is an eligibility checkpoint, not an automatic order. AUTO chooses LONG, SHORT or HOLD from net expected value and current trajectory.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .18f))
                    Text("Wave spacing · ${String.format(Locale.US, "%.1f%%", waveSpacing)}")
                    Slider(value = waveSpacing, onValueChange = { waveSpacing = (it * 2f).toInt() / 2f }, valueRange = .5f..5f, steps = 8)
                    Text("Eligible wave capital · ${money(waveCapital.toDouble())}")
                    Slider(value = waveCapital, onValueChange = { waveCapital = (it / 1_000f).toInt().coerceIn(1, 20) * 1_000f }, valueRange = 1_000f..20_000f, steps = 18)
                    Text("Maximum campaign capital · ${money(campaignCap.toDouble())}")
                    Slider(value = campaignCap, onValueChange = { campaignCap = (it / 10_000f).toInt().coerceIn(1, 50) * 10_000f }, valueRange = 10_000f..500_000f, steps = 48)
                    Text("Maximum daily loss · ${money(dailyLoss.toDouble())}")
                    Slider(value = dailyLoss, onValueChange = { dailyLoss = (it / 500f).toInt().coerceIn(1, 20) * 500f }, valueRange = 500f..10_000f, steps = 18)
                    Text("Maximum single-stock loss · ${money(stockLoss.toDouble())}")
                    Slider(value = stockLoss, onValueChange = { stockLoss = (it / 250f).toInt().coerceIn(1, 20) * 250f }, valueRange = 250f..5_000f, steps = 18)
                    Button(
                        onClick = {
                            onBudgetChanged(budget.toLong())
                            onWaveRiskChanged(waveSpacing.toDouble(), waveCapital.toLong(), state.settings.waveCount, campaignCap.toLong(), dailyLoss.toLong(), stockLoss.toLong())
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Save adaptive risk settings") }
                    Text("Default Wave 2+ capital is ₹5,000. The cap is enforced before every order, so repeated waves cannot silently expand the campaign beyond the configured maximum.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item { SectionTitle("Manual signal", "Fallback only when the paid Equity notification is missed. It enters the same Shadow/decision pipeline.") }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .28f)), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(value = symbol, onValueChange = { symbol = it.uppercase(Locale.US).filter { ch -> ch.isLetterOrDigit() || ch in "&._-" } }, modifier = Modifier.fillMaxWidth(), label = { Text("NSE symbol") }, placeholder = { Text("e.g. TIMEX") }, singleLine = true)
                    OutlinedTextField(value = observedPrice, onValueChange = { observedPrice = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Observed price (optional)") }, supportingText = { Text("Audit reference only; Groww quote drives analysis.") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = { onSubmitManualSignal(symbol, "BUY", observedPrice) }, enabled = symbol.isNotBlank(), modifier = Modifier.weight(1f)) { Text("BUY") }
                        OutlinedButton(onClick = { onSubmitManualSignal(symbol, "BOOK_PROFIT", observedPrice) }, enabled = symbol.isNotBlank(), modifier = Modifier.weight(1f)) { Text("BOOK PROFIT") }
                    }
                    Text("Process through normal pipeline", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun ManualScreen(
    state: TraderUiState,
    onBudgetChanged: (Long) -> Unit,
    onFastTrackRequested: () -> Unit,
    onPostSellShortChanged: (Boolean) -> Unit
) {
    var budget by remember(state.settings.fastTrackBudgetRupees) { mutableStateOf(state.settings.fastTrackBudgetRupees.toFloat()) }
    val enabled = state.settings.fastTrackEffective
    val canEnable = state.settings.brokerAuthenticated && state.settings.staticIpMatched && state.settings.brokerDdpiEnabled && !state.settings.safetyHalt
    val summary = state.dashboard?.fastTrackSummary ?: com.multify.traderpro.data.network.DaySummaryDto()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp, 14.dp, 16.dp, 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { SectionTitle("Manual · notification auto buy/sell", "A separate, deliberately simple Multify-following engine. Paid Equity notifications only; Free, F&O and commodity alerts are ignored.") }
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .28f)),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Notification Fast Track", fontWeight = FontWeight.SemiBold)
                            Text(
                                if (enabled) "Real Groww CNC notification-following is enabled for today" else "OFF · no Manual orders will be sent",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = enabled, onCheckedChange = { onFastTrackRequested() }, enabled = enabled || canEnable)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .18f))
                    Text("Manual budget", style = MaterialTheme.typography.labelLarge)
                    Text(money(budget.toDouble()), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Slider(
                        value = budget,
                        onValueChange = { budget = (it / 10_000f).toInt().coerceIn(1, 20) * 10_000f },
                        onValueChangeFinished = { onBudgetChanged(budget.toLong()) },
                        valueRange = 10_000f..200_000f,
                        steps = 18
                    )
                    Text("₹10,000 to ₹2,00,000 initial budget. Wave 2+ can deploy only the separately configured tranche after LONG/SHORT/HOLD approval, and cumulative capital can never exceed the hard campaign cap.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .18f))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Post-sell MIS short overlay", fontWeight = FontWeight.SemiBold)
                            Text("When a profitable long closes, the optional MIS short initially targets 100% of the preceding long price move. The rolling 30-day learner may reduce that fraction, but never above 100%. A 0.35% protective stop remains.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = state.settings.postSellShortEnabled, onCheckedChange = onPostSellShortChanged)
                    }
                }
            }
        }
        item {
            val learned = state.dashboard?.learning ?: com.multify.traderpro.data.network.LearningStatsDto()
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha=.28f)), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("Current learned targets", fontWeight = FontWeight.SemiBold)
                    KeyValueRow("Long profit target", String.format(Locale.US, "%.2f%%", learned.longAveragePct))
                    KeyValueRow("Short move capture", String.format(Locale.US, "%.0f%% of prior long move", learned.shortRetracementPct))
                    Text("The long reference rolls over the latest 30 trading days. The short reference starts at 100%, never exceeds 100%, and adapts from live post-sell observations.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                MetricCard("Realised", money(summary.realisedPnl), "Manual app-only", Modifier.weight(1f), pnlColor(summary.realisedPnl))
                MetricCard("Unrealised", money(summary.unrealisedPnl), "CNC + overlay", Modifier.weight(1f), pnlColor(summary.unrealisedPnl))
            }
        }
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .45f)),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("Exact lifecycle", fontWeight = FontWeight.SemiBold)
                    Text("1. Paid Multify Equity call → immediate CNC delivery campaign; the rolling 30-trading-day long target is used instead of blindly waiting forever.", style = MaterialTheme.typography.bodyMedium)
                    Text("2. At each eligible 2% checkpoint, reevaluate LONG vs SHORT vs HOLD. An approved same-side wave may add only the configured ₹5,000 tranche within the hard campaign cap. An opposite side must flatten/reconcile first; ambiguous evidence creates NO TRADE.", style = MaterialTheme.typography.bodyMedium)
                    Text("3. A green Multify Book Profit or learned long target closes only this app-owned CNC quantity. Personal holdings are never included.", style = MaterialTheme.typography.bodyMedium)
                    Text("4. If enabled and the long was profitable, open an MIS short. Cover distance starts at 100% of the preceding long move and adapts downward from rolling 30-day live observations.", style = MaterialTheme.typography.bodyMedium)
                    Text("5. Any broker/app MIS quantity mismatch is treated as an external-position conflict and blocks further automated orders.", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        if (!state.settings.brokerDdpiEnabled) {
            item { ActionBanner("DDPI required for unattended delivery exits", "Groww reported DDPI as disabled. Manual notification auto buy/sell remains blocked because a CNC sell may otherwise require separate authorization.", Icons.Default.Warning, "System", onClick = {}, negative = true) }
        }
    }
}

@Composable
private fun SystemScreen(
    state: TraderUiState,
    onSave: (String, String, String, String, String) -> Unit,
    onAuthenticate: () -> Unit,
    onLiveRequested: () -> Unit,
    onResetHalt: () -> Unit,
    onExportLogs: () -> Unit,
    onFirstWaveMode: (String) -> Unit,
    onActiveWaveCount: (Long) -> Unit
) {
    val context = LocalContext.current
    var apiKey by remember { mutableStateOf("") }
    var totpSecret by remember { mutableStateOf("") }
    var staticIp by remember(state.settings.expectedStaticIp) { mutableStateOf(state.settings.expectedStaticIp) }
    var packageFilter by remember(state.settings.packageFilter) { mutableStateOf(state.settings.packageFilter) }
    var budgetValue by remember(state.settings.dailyBudgetRupees) { mutableStateOf(state.settings.dailyBudgetRupees.toFloat()) }
    val notificationAccess = notificationAccessEnabled(context)
    val batteryUnrestricted = batteryOptimizationIgnored(context)
    val deviceName = "${Build.MANUFACTURER.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }} ${Build.MODEL}".trim()
    val isVivo = Build.MANUFACTURER.equals("vivo", ignoreCase = true)
    val live = state.settings.liveExecutionEffective
    val canEnableLive = state.settings.brokerAuthenticated && state.settings.staticIpMatched && !state.settings.safetyHalt && state.settings.dailyBudgetRupees >= 10_000L

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp, 14.dp, 16.dp, 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { SectionTitle("System & execution", "No custom backend URL is required. Enter the TOTP token and TOTP secret generated on Groww. The app creates the rotating code locally and authenticates directly with Groww.") }
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.28f)),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Lock, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.size(9.dp))
                        Column {
                            Text("Groww TOTP credentials", fontWeight = FontWeight.SemiBold)
                            Text("TOTP token + TOTP secret → access token", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Groww TOTP token") },
                        placeholder = { Text(if (state.credentialsConfigured) "Saved securely — enter only to replace" else "Paste Groww TOTP token") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = totpSecret,
                        onValueChange = { totpSecret = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Groww TOTP secret") },
                        placeholder = { Text(if (state.credentialsConfigured) "Saved securely — enter only to replace" else "Paste Groww TOTP secret") },
                        supportingText = { Text("Used only on-device to generate the current 6-digit TOTP code. The secret stays in Android Keystore and is never logged.") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = staticIp,
                        onValueChange = { staticIp = it.trim() },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Groww-whitelisted static IP") },
                        placeholder = { Text("203.0.113.10") },
                        supportingText = { Text("This is a verification value, not a server URL. Your phone's actual outbound IP must match it for live orders.") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii)
                    )
                    OutlinedTextField(
                        value = packageFilter,
                        onValueChange = { packageFilter = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Multify package filter (optional)") },
                        supportingText = { Text("Leave blank while discovering notifications; lock it down once the correct source package is confirmed.") },
                        singleLine = true
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = { onSave(apiKey, totpSecret, staticIp, packageFilter, budgetValue.toLong().toString()) },
                            modifier = Modifier.weight(1f)
                        ) { Text("Save") }
                        Button(
                            onClick = onAuthenticate,
                            enabled = state.credentialsConfigured && staticIp.isNotBlank(),
                            modifier = Modifier.weight(1f)
                        ) { Text("Refresh & Authenticate") }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .18f))
                    KeyValueRow("Credential vault", if (state.credentialsConfigured) "Configured" else "Not configured", if (state.credentialsConfigured) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                    KeyValueRow("Groww session", if (state.settings.brokerAuthenticated) "Authenticated" else "Not authenticated", if (state.settings.brokerAuthenticated) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                    if (state.settings.brokerUcc.isNotBlank()) KeyValueRow("UCC", state.settings.brokerUcc)
                    KeyValueRow("DDPI", if (state.settings.brokerDdpiEnabled) "Enabled" else "Not enabled", if (state.settings.brokerDdpiEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                    if (state.settings.verifiedPublicIp.isNotBlank()) KeyValueRow("Current outbound IP", state.settings.verifiedPublicIp, if (state.settings.staticIpMatched) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                    if (state.settings.accessTokenExpiry.isNotBlank()) KeyValueRow("Token expiry", state.settings.accessTokenExpiry)
                }
            }
        }
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.28f)),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Execution mode & wave depth", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("AUTO, LONG ONLY and SHORT ONLY share the same auditable Wave 2+ gates. In AUTO, a checkpoint may add the configured tranche only after after-cost direction approval; HOLD sends no order.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("AUTO", "LONG_ONLY", "SHORT_ONLY").forEach { mode ->
                            val selectedMode = state.settings.executionMode == mode
                            if (selectedMode) {
                                Button(onClick = { onFirstWaveMode(mode) }, modifier = Modifier.weight(1f)) { Text(mode) }
                            } else {
                                OutlinedButton(onClick = { onFirstWaveMode(mode) }, modifier = Modifier.weight(1f)) { Text(mode) }
                            }
                        }
                    }
                    Text("Active waves: ${state.settings.waveCount} / 20", fontWeight = FontWeight.SemiBold)
                    Slider(
                        value = state.settings.waveCount.toFloat(),
                        onValueChange = { onActiveWaveCount(it.toLong().coerceIn(1L, 20L)) },
                        valueRange = 1f..20f,
                        steps = 18
                    )
                    Text("Checkpoints beyond this number are prediction-only. Enabled Wave 2+ checkpoints may deploy only the configured tranche after LONG/SHORT/HOLD approval and remain bounded by campaign, daily-loss and single-stock-loss caps.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    KeyValueRow("Pivot confirmation", "0.40% reversal from extreme")
                    KeyValueRow("Wave capital action", "Configured tranche · only after AUTO approval")
                    KeyValueRow("Maximum waves", "20")
                    KeyValueRow("First-wave trailing stop", "Always on · AUTO / LONG / SHORT")
                }
            }
        }
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.28f)),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Fixed capital & live execution", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("Fixed intraday capital cap", style = MaterialTheme.typography.labelLarge)
                    Text(money(budgetValue.toDouble()), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Slider(
                        value = budgetValue,
                        onValueChange = { budgetValue = (it / 10_000f).toInt().coerceIn(1, 20) * 10_000f },
                        valueRange = 10_000f..200_000f,
                        steps = 18
                    )
                    Text("The initial live budget remains explicit. Wave 2+ uses its separately configured tranche and hard campaign cap; no checkpoint can silently expand capital beyond that cap.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    KeyValueRow("Profit milestone", "₹5,000 · no profit ceiling")
                    KeyValueRow("Soft loss band", "-₹1,500 · defensive mode")
                    KeyValueRow("Hard loss cap", "-₹2,500 · live pre-flatten starts earlier for slippage")
                    KeyValueRow("Live confidence gate", String.format(Locale.US, "%.0f%%", state.settings.minLiveConfidence * 100))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .18f))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Enable live buy & sell", fontWeight = FontWeight.SemiBold)
                            Text(
                                when {
                                    state.settings.safetyHalt -> "Blocked by safety halt"
                                    !state.settings.brokerAuthenticated -> "Authenticate Groww first"
                                    !state.settings.staticIpMatched -> "Current outbound IP must match the saved static IP"
                                    live -> "Real NSE CASH MIS orders are enabled for today"
                                    else -> "Paper analysis continues while live execution is off"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (state.settings.safetyHalt || (!canEnableLive && !live)) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = live,
                            onCheckedChange = { onLiveRequested() },
                            enabled = live || canEnableLive
                        )
                    }
                    if (state.settings.safetyHalt) {
                        Button(
                            onClick = onResetHalt,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Default.Warning, null)
                            Spacer(Modifier.size(8.dp))
                            Text("Reset safety halt after broker review")
                        }
                    }
                }
            }
        }
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.28f)),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Permissions & runtime", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(12.dp))
                    KeyValueRow("Device", deviceName)
                    KeyValueRow("Notification access", if (notificationAccess) "Enabled" else "Required", if (notificationAccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                    KeyValueRow("Background battery mode", if (batteryUnrestricted) "Unrestricted" else "Optimized", if (batteryUnrestricted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                    KeyValueRow("Intraday mode", if (live) "LIVE" else "SHADOW ₹2L")
                    KeyValueRow("Manual mode", if (state.settings.fastTrackEffective) "AUTO CNC" else "OFF")
                    KeyValueRow("Static IP", if (state.settings.staticIpMatched) "Verified" else "Not verified", if (state.settings.staticIpMatched) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                    if (isVivo) {
                        Text(
                            "Vivo/Funtouch OS can aggressively stop background apps. For reliable Multify notification capture, keep Notification Access enabled and set Multify Trader Pro to unrestricted background battery usage / allow background activity. The app also requests listener rebind if Vivo disconnects it.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(onClick = { openNotificationAccessSettings(context) }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.NotificationsActive, null)
                        Spacer(Modifier.size(8.dp))
                        Text("Open notification access")
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { requestUnrestrictedBattery(context) }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Bolt, null)
                        Spacer(Modifier.size(8.dp))
                        Text(if (batteryUnrestricted) "Background battery unrestricted" else "Allow unrestricted background")
                    }
                    if (isVivo) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = { openAppDetails(context) }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Settings, null)
                            Spacer(Modifier.size(8.dp))
                            Text("Open Vivo app settings / Auto-start")
                        }
                    }
                }
            }
        }
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.28f)),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Diagnostics & version", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    KeyValueRow("App version", BuildConfig.VERSION_NAME)
                    KeyValueRow("Build", BuildConfig.VERSION_CODE.toString())
                    KeyValueRow("Package", BuildConfig.APPLICATION_ID)
                    KeyValueRow("Compatibility", if (isVivo) "Vivo / Funtouch background hardened" else "Standard Android")
                    Text(
                        "The export includes every locally retained Multify signal and decision, Shadow and app-owned trade ledgers, rolling learning calls, 1s–5m price observations, every strategy snapshot/vote, Forecast history, after-market research reports, MFE/MAE, costs, P&L, risk/system events and NSE symbol-master status.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Groww TOTP credentials, access tokens, UCC and exact IP addresses are never included in the export.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(onClick = onExportLogs, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.FileDownload, null)
                        Spacer(Modifier.size(8.dp))
                        Text("Export complete logs")
                    }
                }
            }
        }
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.size(9.dp))
                        Text("Safety design", fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Groww credentials and the daily access token are encrypted with Android Keystore. Multify Trader Pro keeps a separate fill-level ledger for its own orders, so unrelated trades in the same Groww account do not count toward app P&L. Same-symbol MIS quantity conflicts halt the engine rather than touching manual positions. Live execution is off by default; every app MIS fill requires broker-side OCO protection.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyState(title: String, body: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(5.dp))
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun decisionTone(action: String): StatusTone = when {
    action.contains("HALT", true) -> StatusTone.Negative
    action.contains("BUY", true) || action.contains("SHORT", true) || action.contains("PROTECTED", true) -> StatusTone.Positive
    action.contains("WAIT", true) -> StatusTone.Warning
    else -> StatusTone.Info
}

@Composable
private fun pnlColor(value: Double): Color = when {
    value > 0.0 -> MaterialTheme.colorScheme.primary
    value < 0.0 -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurface
}

private fun money(value: Double): String = NumberFormat.getCurrencyInstance(Locale("en", "IN")).format(value)
private fun formatTime(ms: Long): String = SimpleDateFormat("dd MMM · HH:mm:ss", Locale.getDefault()).format(Date(ms))

private fun defaultLogFileName(): String {
    val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
    val safeVersion = BuildConfig.VERSION_NAME.replace(Regex("[^A-Za-z0-9._-]"), "_")
    return "MultifyTraderPro-logs-v${safeVersion}-${stamp}.zip"
}

private fun notificationAccessEnabled(context: Context): Boolean {
    val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners") ?: return false
    val component = ComponentName(context, com.multify.traderpro.service.MultifyNotificationListenerService::class.java)
    return flat.contains(component.flattenToString()) || flat.contains(context.packageName)
}

private fun openNotificationAccessSettings(context: Context) {
    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

private fun batteryOptimizationIgnored(context: Context): Boolean {
    val power = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    return power.isIgnoringBatteryOptimizations(context.packageName)
}

private fun requestUnrestrictedBattery(context: Context) {
    val packageUri = Uri.parse("package:${context.packageName}")
    val direct = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, packageUri)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    val fallback = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(direct) }
        .recoverCatching { context.startActivity(fallback) }
        .recoverCatching { openAppDetails(context) }
}

private fun openAppDetails(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}
