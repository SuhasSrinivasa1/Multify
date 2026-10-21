package com.multify.traderpro.ui.screens

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
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
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.multify.traderpro.BuildConfig
import com.multify.traderpro.data.network.DashboardDto
import com.multify.traderpro.data.network.ExecutionAverageDto
import com.multify.traderpro.data.network.ForecastDto
import com.multify.traderpro.data.network.PositionDto
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

private enum class Destination(val label: String, val subtitle: String, val icon: ImageVector) {
    Execution("Execution", "ARM status and app-owned CNC holdings", Icons.Default.Dashboard),
    Averages("Averages", "30-day LONG averages", Icons.Default.Analytics),
    Forecast("Forecast", "Confirmed PRIMARY LONG recommendations · ≥1% target", Icons.Default.Science),
    Settings("Settings", "Broker, ARM, holding budget and device controls", Icons.Default.Settings)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TraderApp(viewModel: TraderViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var selected by rememberSaveable { mutableIntStateOf(0) }
    val snackbar = remember { SnackbarHostState() }
    var showArmConfirm by remember { mutableStateOf(false) }
    var showResetConfirm by remember { mutableStateOf(false) }
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri -> if (uri != null) viewModel.exportLogs(uri) }

    val restoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) viewModel.restoreLearning(uri) }

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
                        Text(Destination.entries[selected].label, fontWeight = FontWeight.SemiBold)
                        Text(
                            Destination.entries[selected].subtitle,
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
                        label = { Text(destination.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.primaryContainer)
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (Destination.entries[selected]) {
                Destination.Execution -> ExecutionScreen(
                    state = state,
                    onRefresh = viewModel::refresh,
                    onArmRequested = {
                        if (state.settings.armEffective) viewModel.setArm(false) else showArmConfirm = true
                    },
                    onResetHalt = { showResetConfirm = true }
                )
                Destination.Averages -> LongAveragesScreen(state)
                Destination.Forecast -> ForecastScreen(
                    state = state,
                    onGenerate = viewModel::generateForecasts,
                    onResearch = viewModel::runAfterHoursResearch,
                    onBuy = viewModel::executeForecast
                )
                Destination.Settings -> SettingsScreen(
                    state = state,
                    onSave = viewModel::saveBrokerSettings,
                    onAuthenticate = viewModel::authenticate,
                    onArmRequested = {
                        if (state.settings.armEffective) viewModel.setArm(false) else showArmConfirm = true
                    },
                    onBudgetChanged = viewModel::setHoldingBudget,
                    onForecastAutoBuyChanged = viewModel::setForecastAutoBuy,
                    onSubmitManualSignal = viewModel::submitManualSignal,
                    onResetHalt = { showResetConfirm = true },
                    onExportLogs = { exportLauncher.launch(defaultLogFileName()) },
                    onRestoreLearning = { restoreLauncher.launch(arrayOf("application/zip", "application/octet-stream")) }
                )
            }
            if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
        }
    }

    if (showArmConfirm) {
        AlertDialog(
            onDismissRequest = { showArmConfirm = false },
            icon = { Icon(Icons.Default.Shield, contentDescription = null) },
            title = { Text("ARM Multify LONG holdings?") },
            text = {
                Text(
                    "When ARM is on, a valid Multify NSE CASH call can place a real Groww CNC BUY using up to " +
                        "${money(state.settings.holdingBudgetRupees.toDouble())} for that call. Positions remain holdings; " +
                        "there is no end-of-day flatten. DDPI and static-IP verification are required so the app can later sell only its own holding."
                )
            },
            confirmButton = {
                Button(onClick = {
                    showArmConfirm = false
                    viewModel.setArm(true)
                }) { Text("ARM for today") }
            },
            dismissButton = { TextButton(onClick = { showArmConfirm = false }) { Text("Cancel") } }
        )
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("Reset safety halt?") },
            text = { Text("Resetting the halt does not ARM trading. Review Groww first, then ARM separately.") },
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
private fun ExecutionScreen(
    state: TraderUiState,
    onRefresh: () -> Unit,
    onArmRequested: () -> Unit,
    onResetHalt: () -> Unit
) {
    val context = LocalContext.current
    val dashboard = state.dashboard
    val notificationAccess = notificationAccessEnabled(context)
    val armed = state.settings.armEffective && dashboard?.halted != true

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp, 14.dp, 16.dp, 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            ArmStatusCard(
                dashboard = dashboard,
                armed = armed,
                budget = state.settings.holdingBudgetRupees.toDouble(),
                ddpi = state.settings.brokerDdpiEnabled,
                staticIp = state.settings.staticIpMatched,
                onArmRequested = onArmRequested,
                onResetHalt = onResetHalt
            )
        }
        if (!notificationAccess) {
            item {
                ActionCard(
                    title = "Notification access required",
                    body = "Enable notification access so Multify calls and Book Profit alerts are captured immediately.",
                    button = "Open settings",
                    negative = true
                ) { openNotificationAccessSettings(context) }
            }
        }
        if (!state.settings.brokerAuthenticated) {
            item {
                ActionCard(
                    title = "Groww authentication required",
                    body = "Authenticate Groww before ARM can buy CNC holdings.",
                    button = "Refresh"
                ) { onRefresh() }
            }
        }
        if (dashboard != null) {
            item { SectionTitle("Holdings overview", "Current app-owned LONG CNC holdings.") }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    MetricCard(
                        label = "Unrealised",
                        value = money(dashboard.summary.unrealisedPnl),
                        supporting = "current app-owned holdings only",
                        modifier = Modifier.weight(1f),
                        accent = pnlColor(dashboard.summary.unrealisedPnl)
                    )
                    MetricCard(
                        label = "Exposure",
                        value = money(dashboard.summary.grossExposure),
                        supporting = "${dashboard.summary.openPositions} open holdings",
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    MetricCard(
                        label = "Holding budget",
                        value = money(state.settings.holdingBudgetRupees.toDouble()),
                        supporting = "maximum per new app order",
                        modifier = Modifier.weight(1f)
                    )
                    MetricCard(
                        label = "Market",
                        value = dashboard.marketSession,
                        supporting = "CNC holdings persist overnight",
                        modifier = Modifier.weight(1f),
                        accent = if (dashboard.marketSession == "OPEN") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            item { SectionTitle("Execution speed", "Rolling average from the latest 30 successful LONG CNC fills.") }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    MetricCard(
                        label = "Avg LONG Buy",
                        value = executionTime(dashboard.execution.buyLong.averageFillMs, dashboard.execution.buyLong.sampleCount),
                        supporting = executionSupport(dashboard.execution.buyLong),
                        modifier = Modifier.weight(1f)
                    )
                    MetricCard(
                        label = "Avg LONG Sell",
                        value = executionTime(dashboard.execution.sellLong.averageFillMs, dashboard.execution.sellLong.sampleCount),
                        supporting = executionSupport(dashboard.execution.sellLong),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item { SectionTitle("Active holdings", "All positions shown here are app-owned LONG CNC holdings. There is no automatic end-of-day exit.") }
            if (dashboard.positions.isEmpty()) {
                item { EmptyState("No app-owned holdings", "ARM will buy the next eligible Multify call. Forecast candidates can also be bought manually while ARM is enabled.") }
            } else {
                items(dashboard.positions, key = { "${it.symbol}-${it.averagePrice}" }) { HoldingCard(it) }
            }

            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .45f)),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("Execution health", fontWeight = FontWeight.SemiBold)
                        KeyValueRow("ARM", if (dashboard.armed) "ON" else "OFF")
                        KeyValueRow("DDPI", if (state.settings.brokerDdpiEnabled) "Enabled" else "Required")
                        KeyValueRow("Static IP", if (state.settings.staticIpMatched) "Verified" else "Not verified")
                        KeyValueRow("Foreground engine", dashboard.health.foregroundService)
                        KeyValueRow("Notification listener", dashboard.health.listener)
                        KeyValueRow("Broker", dashboard.health.broker)
                        KeyValueRow(
                            "App order dispatch",
                            if (dashboard.health.lastOrderDispatchPrepMicros > 0L)
                                String.format(Locale.US, "%.1f ms · target <100 ms", dashboard.health.lastOrderDispatchPrepMicros / 1000.0)
                            else "Waiting for first order"
                        )
                        KeyValueRow(
                            "Broker ACK",
                            if (dashboard.health.lastBrokerAckLatencyMs > 0L)
                                dashboard.health.lastBrokerAckLatencyMs.toString() + " ms · external"
                            else "Waiting for first order"
                        )
                        KeyValueRow("Market data", dashboard.health.marketData)
                        KeyValueRow("Symbol master", dashboard.health.symbolMaster)
                    }
                }
            }

            item { SectionTitle("Recent decisions", "Only LONG holding actions and system events are generated by this version.") }
            if (dashboard.recentDecisions.isEmpty()) {
                item { EmptyState("No decisions yet", "Captured Multify calls will appear here after processing.") }
            } else {
                items(dashboard.recentDecisions.take(8)) { d ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .24f)),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(d.symbol ?: "System", fontWeight = FontWeight.SemiBold)
                                StatusPill(d.action.replace('_', ' '), decisionTone(d.action))
                            }
                            Text(d.reason, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (!d.strategy.isNullOrBlank()) Text(d.strategy, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ArmStatusCard(
    dashboard: DashboardDto?,
    armed: Boolean,
    budget: Double,
    ddpi: Boolean,
    staticIp: Boolean,
    onArmRequested: () -> Unit,
    onResetHalt: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (armed) MaterialTheme.colorScheme.primaryContainer.copy(alpha = .45f)
            else MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .25f)),
        shape = RoundedCornerShape(22.dp)
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Multify ARM", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (armed) "Ready to BUY LONG CNC holdings from Multify calls"
                        else "Disarmed · calls are captured but no real BUY is sent",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = armed, onCheckedChange = { onArmRequested() })
            }
            KeyValueRow("Per-call budget", money(budget))
            KeyValueRow("Product", "CNC · holdings")
            KeyValueRow("DDPI", if (ddpi) "Enabled" else "Required")
            KeyValueRow("Static IP", if (staticIp) "Verified" else "Required")
            if (dashboard?.halted == true) {
                Button(
                    onClick = onResetHalt,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Reset safety halt") }
            }
        }
    }
}

@Composable
private fun HoldingCard(p: PositionDto) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha=.22f)),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(p.symbol, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                StatusPill("LONG · CNC", StatusTone.Positive)
            }
            KeyValueRow("Quantity", p.quantity.toString())
            KeyValueRow("Average", money(p.averagePrice))
            KeyValueRow("LTP", p.ltp?.let(::money) ?: "—")
            KeyValueRow("Unrealised", money(p.pnl), pnlColor(p.pnl))
            KeyValueRow("Trail ladder", "arm +0.55% → protect +0.50% · +0.10% steps")
            KeyValueRow(
                "Arm trigger",
                p.trailArmPct?.let { "+${String.format(Locale.US, "%.2f", it)}%" } ?: "+0.55%"
            )
            p.targetPrice?.let { target ->
                KeyValueRow(
                    "Target / reference",
                    if (p.averagePrice > 0.0) {
                        val pct = (target / p.averagePrice - 1.0) * 100.0
                        "+${String.format(Locale.US, "%.2f", pct)}% · ${money(target)}"
                    } else money(target)
                )
            }
            when (p.smartOrderMode) {
                "RECOVERY_GTT" -> {
                    KeyValueRow("Exit mode", "₹100-net recovery GTT")
                    KeyValueRow("GTT trigger", p.smartOrderTriggerPrice?.let(::money) ?: "Pending")
                    KeyValueRow("GTT direction", p.smartOrderDirection ?: "—")
                    KeyValueRow("GTT status", p.smartOrderStatus ?: "Pending")
                    KeyValueRow("Downside sell", "Disabled")
                }
                else -> if (p.trailArmed) {
                    KeyValueRow("Exit mode", if (p.smartOrderId != null) "Server GTT mirrored trail" else "Local trailing fallback")
                    KeyValueRow("High water", p.highWaterPrice?.let(::money) ?: "—")
                    KeyValueRow("Current trail stop", p.stopPrice?.let(::money) ?: "—")
                    val protectedPct = p.stopPrice?.takeIf { p.averagePrice > 0.0 }?.let { (it / p.averagePrice - 1.0) * 100.0 }
                    KeyValueRow("Protected gain", protectedPct?.let { "+${String.format(Locale.US, "%.2f", it)}%" } ?: "—")
                    KeyValueRow("Trail gap", p.trailDistance?.let(::money) ?: "—")
                    KeyValueRow("Last ratchet trigger", p.trailingLastRatchetPrice?.let(::money) ?: "—")
                    KeyValueRow("GTT status", p.smartOrderStatus ?: if (p.smartOrderId != null) "ACTIVE" else "Local fallback")
                } else {
                    KeyValueRow("Exit mode", "Waiting for +0.55% arm")
                    KeyValueRow("Downside sell", "Disabled · hold through declines")
                }
            }
            if (!p.strategy.isNullOrBlank()) {
                Text(p.strategy, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun LongAveragesScreen(state: TraderUiState) {
    val learning = state.dashboard?.learning ?: com.multify.traderpro.data.network.LearningStatsDto()
    val forecastLearning = state.dashboard?.forecastLearning ?: com.multify.traderpro.data.network.ForecastLearningStatsDto()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp, 14.dp, 16.dp, 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            SectionTitle(
                "Multify forecasted upside",
                "Rolling 30 recommendation trading days using the maximum price reached from Multify entry until its Book Profit/close signal."
            )
        }
        item {
            MetricCard(
                label = "Multify MFE Avg Upside %",
                value = String.format(Locale.US, "%.2f%%", learning.longAveragePct),
                supporting = buildString {
                    append("${learning.rollingCalls} MFE-observed calls across ${learning.rollingTradingDays} trading days")
                    if (learning.mfeBackfillPendingCalls > 0) append(" · ${learning.mfeBackfillPendingCalls} historical calls backfilling")
                },
                modifier = Modifier.fillMaxWidth(),
                accent = MaterialTheme.colorScheme.primary
            )
        }
        item {
            SectionTitle(
                "Forecast engine",
                "Targets have a hard 1.00% minimum. Forecast learning/ranking examples use prior positive-close days only; flat and negative closes remain diagnostic data."
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                MetricCard(
                    label = "Today's Avg Target",
                    value = if (forecastLearning.todayTargetAveragePct > 0.0)
                        String.format(Locale.US, "%.2f%%", forecastLearning.todayTargetAveragePct)
                    else "≥1.00%",
                    supporting = "hard floor ${String.format(Locale.US, "%.2f%%", forecastLearning.targetFloorPct)}",
                    modifier = Modifier.weight(1f),
                    accent = MaterialTheme.colorScheme.primary
                )
                MetricCard(
                    label = "Observed Forecast MFE",
                    value = if (forecastLearning.completedForecasts > 0)
                        String.format(Locale.US, "%.2f%%", forecastLearning.longAveragePct)
                    else "Learning",
                    supporting = if (forecastLearning.completedForecasts > 0)
                        "${forecastLearning.completedForecasts} completed forecasts"
                    else "full-session tracking",
                    modifier = Modifier.weight(1f)
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                MetricCard(
                    label = "Reached +0.55%",
                    value = if (forecastLearning.evaluatedCompletedForecasts > 0)
                        String.format(Locale.US, "%.1f%%", forecastLearning.trailArmHitRatePct)
                    else "Learning",
                    supporting = "trail-arm opportunity rate · all completed forecasts",
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    label = "Reached +1.00%",
                    value = if (forecastLearning.evaluatedCompletedForecasts > 0)
                        String.format(Locale.US, "%.1f%%", forecastLearning.onePctHitRatePct)
                    else "Learning",
                    supporting = "all completed forecasts",
                    modifier = Modifier.weight(1f)
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                MetricCard(
                    label = "Full target hit",
                    value = if (forecastLearning.evaluatedCompletedForecasts > 0)
                        String.format(Locale.US, "%.1f%%", forecastLearning.targetHitRatePct)
                    else "Learning",
                    supporting = "${forecastLearning.evaluatedCompletedForecasts} completed evaluations",
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    label = "MFE distribution",
                    value = if (forecastLearning.evaluatedCompletedForecasts > 0)
                        String.format(Locale.US, "%.2f%% median", forecastLearning.mfeMedianPct)
                    else "Learning",
                    supporting = String.format(Locale.US, "P25 %.2f%% · P75 %.2f%%", forecastLearning.mfeP25Pct, forecastLearning.mfeP75Pct),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun ForecastScreen(
    state: TraderUiState,
    onGenerate: () -> Unit,
    onResearch: () -> Unit,
    onBuy: (String) -> Unit
) {
    val dashboard = state.dashboard
    val rows = dashboard?.forecasts.orEmpty().sortedBy { it.rank }
    val learning = dashboard?.learning ?: com.multify.traderpro.data.network.LearningStatsDto()
    val forecastLearning = dashboard?.forecastLearning ?: com.multify.traderpro.data.network.ForecastLearningStatsDto()
    val report = dashboard?.research ?: com.multify.traderpro.data.network.ResearchDto()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp, 14.dp, 16.dp, 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            SectionTitle(
                "LONG holding forecast",
                "No daily numeric recommendation cap. Every newly confirmed PRIMARY candidate can be published; PRIMARY candidates must persist across scans before publication, while weaker SECONDARY/RESEARCH rows remain in the learning ledger."
            )
        }
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha=.18f)),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Forecast engine · ACTIVE", fontWeight = FontWeight.SemiBold)
                    Text(
                        if (state.settings.armEffective && state.settings.forecastAutoBuyEnabled)
                            "Learning and forecasting are running. ARM and Forecast Auto-buy are ON; new confirmed PRIMARY rows create fixed-entry UP-trigger GTT buys automatically."
                        else if (state.settings.armEffective)
                            "Learning and forecasting are running. ARM is ON; forecast recommendations remain manual unless Forecast Auto-buy is enabled in Settings."
                        else
                            "Learning and forecasting continue normally while ARM is OFF. Trading is disabled; research and recommendations are not.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        item {
            val health = dashboard?.health ?: com.multify.traderpro.data.network.EngineHealthDto()
            val ageText = if (health.discoveryBaselineAgeMs == Long.MAX_VALUE) "—"
                else if (health.discoveryBaselineAgeMs < 60_000L) "<1 min"
                else "${health.discoveryBaselineAgeMs / 60_000L} min"
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = when (health.discovery) {
                        "HEALTHY" -> MaterialTheme.colorScheme.primaryContainer.copy(alpha=.18f)
                        "WARMING", "WAITING" -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha=.22f)
                        else -> MaterialTheme.colorScheme.errorContainer.copy(alpha=.22f)
                    }
                ),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("Discovery health · ${health.discovery}", fontWeight = FontWeight.SemiBold)
                    KeyValueRow("NSE coverage", String.format(Locale.US, "%.1f%%", health.discoveryCoveragePct))
                    KeyValueRow("Baseline", if (health.discoveryBaselineReady) "Ready" else "Warming")
                    KeyValueRow("Baseline age", ageText)
                    KeyValueRow("Failed batches", health.discoveryBatchErrors.toString())
                    Text(
                        if (health.discoveryBaselineReady)
                            "Production publication can use the persisted broad-market baseline."
                        else
                            "Research continues, but production recommendations are suppressed until broad-market discovery is healthy.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        item {
            MetricCard(
                label = "Today's Forecast Avg Upside",
                value = if (forecastLearning.todayTargetAveragePct > 0.0)
                    String.format(Locale.US, "%.2f%%", forecastLearning.todayTargetAveragePct)
                else "≥1.00%",
                supporting = "hard ≥1% target · Multify MFE benchmark ${String.format(Locale.US, "%.2f%%", learning.longAveragePct)} · reverse-engineering sessions ${forecastLearning.researchSessionsCaptured}/${forecastLearning.researchSessionGoal}",
                modifier = Modifier.fillMaxWidth(),
                accent = MaterialTheme.colorScheme.primary
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = onGenerate, modifier = Modifier.weight(1f)) { Text("Refresh scanner") }
                OutlinedButton(onClick = onResearch, modifier = Modifier.weight(1f)) { Text("Run research") }
            }
        }
        item { ForecastTable(rows, onBuy, state.settings.armEffective) }
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("Multify DNA", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "The reverse-engineering ledger captures 40 observable factors grouped into three fixed families: Momentum/Trend, Participation/Liquidity and Structure/Context. Market regime changes family weights. Only prior positive-close outcomes become forecast ranking examples; negative/flat outcomes remain diagnostics.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    KeyValueRow("Factor families", "3")
                    KeyValueRow("Observable factors", "40")
                    KeyValueRow("Positive reference cases", MultifyReverseEngineering.positiveCases.size.toString())
                }
            }
        }
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha=.20f)),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("After-market LONG research", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(report.title, fontWeight = FontWeight.Medium)
                    Text(
                        report.summary.ifBlank { "The first report is generated after market close or when you tap Run research." },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ForecastTable(rows: List<ForecastDto>, onBuy: (String) -> Unit, armed: Boolean) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha=.24f)),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Row(Modifier.fillMaxWidth().padding(bottom = 7.dp)) {
                Text("#", Modifier.weight(.28f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Text("Stock", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Text("Entry", Modifier.weight(.82f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Text("Target", Modifier.weight(.82f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Text("Tier", Modifier.weight(.85f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha=.18f))
            if (rows.isEmpty()) {
                Text(
                    "Scanner is active. No PRIMARY candidate currently meets production criteria; research candidates are still being captured in the background.",
                    modifier = Modifier.padding(vertical = 16.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                rows.forEachIndexed { index, f ->
                    Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(f.rank.toString(), Modifier.weight(.28f), style = MaterialTheme.typography.bodySmall)
                        Text(f.symbol, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                        Text(money(f.entryPrice), Modifier.weight(.82f), style = MaterialTheme.typography.bodySmall)
                        Text(money(f.targetPrice), Modifier.weight(.82f), style = MaterialTheme.typography.bodySmall)
                        Text(f.qualityTier, Modifier.weight(.85f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                    }
                    Text(
                        String.format(
                            Locale.US,
                            "%.0f%% confidence · score %.2f · %s · %s",
                            f.confidence * 100.0, f.score, f.strategy, f.marketRegime
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        f.reason,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    Button(
                        onClick = { onBuy(f.symbol) },
                        enabled = armed && f.status == "ACTIVE" && f.executionEligible,
                        modifier = Modifier.fillMaxWidth().padding(top = 7.dp)
                    ) {
                        Text(
                            when {
                                !f.executionEligible -> f.qualityTier + " · observation only"
                                !armed -> "ARM to BUY " + f.symbol
                                else -> "BUY " + f.symbol + " · CNC"
                            }
                        )
                    }
                    if (index != rows.lastIndex) HorizontalDivider(
                        modifier = Modifier.padding(top = 10.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha=.10f)
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    state: TraderUiState,
    onSave: (String, String, String, String, String) -> Unit,
    onAuthenticate: () -> Unit,
    onArmRequested: () -> Unit,
    onBudgetChanged: (Long) -> Unit,
    onForecastAutoBuyChanged: (Boolean) -> Unit,
    onSubmitManualSignal: (String, String, String) -> Unit,
    onResetHalt: () -> Unit,
    onExportLogs: () -> Unit,
    onRestoreLearning: () -> Unit
) {
    val context = LocalContext.current
    var apiKey by remember { mutableStateOf("") }
    var totpSecret by remember { mutableStateOf("") }
    var staticIp by remember(state.settings.expectedStaticIp) { mutableStateOf(state.settings.expectedStaticIp) }
    var packageFilter by remember(state.settings.packageFilter) { mutableStateOf(state.settings.packageFilter) }
    var budget by remember(state.settings.holdingBudgetRupees) { mutableStateOf(state.settings.holdingBudgetRupees.toFloat()) }
    var manualSymbol by remember { mutableStateOf("") }
    var manualPrice by remember { mutableStateOf("") }
    val notificationAccess = notificationAccessEnabled(context)
    val batteryUnrestricted = batteryOptimizationIgnored(context)
    val armed = state.settings.armEffective
    val canArm = state.settings.brokerAuthenticated && state.settings.staticIpMatched && state.settings.brokerDdpiEnabled && !state.settings.safetyHalt

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp, 14.dp, 16.dp, 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { SectionTitle("Broker connection", "Groww authentication and the fixed network identity used by ARM.") }
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha=.24f)),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(apiKey, { apiKey = it }, Modifier.fillMaxWidth(), label = { Text("Groww TOTP token") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
                    OutlinedTextField(totpSecret, { totpSecret = it }, Modifier.fillMaxWidth(), label = { Text("Groww TOTP secret") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
                    OutlinedTextField(staticIp, { staticIp = it.trim() }, Modifier.fillMaxWidth(), label = { Text("Groww-whitelisted static IP") }, singleLine = true)
                    OutlinedTextField(packageFilter, { packageFilter = it }, Modifier.fillMaxWidth(), label = { Text("Multify package filter (blank = official app only)") }, singleLine = true)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = { onSave(apiKey, totpSecret, staticIp, packageFilter, budget.toLong().toString()) },
                            modifier = Modifier.weight(1f)
                        ) { Text("Save") }
                        Button(onClick = onAuthenticate, enabled = state.credentialsConfigured, modifier = Modifier.weight(1f)) { Text("Authenticate") }
                    }
                    KeyValueRow("Groww", if (state.settings.brokerAuthenticated) "Authenticated" else "Not authenticated")
                    KeyValueRow("DDPI", if (state.settings.brokerDdpiEnabled) "Enabled" else "Required for ARM")
                    KeyValueRow("Static IP", if (state.settings.staticIpMatched) "Verified" else "Not verified")
                }
            }
        }

        item { SectionTitle("ARM", "There is one trading mode only: LONG CNC holdings.") }
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha=.22f)),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("ARM Multify calls", fontWeight = FontWeight.SemiBold)
                            Text(
                                "When armed, the next eligible Multify call places a CNC BUY. Holdings can remain overnight.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = armed, onCheckedChange = { onArmRequested() }, enabled = armed || canArm)
                    }
                    Text("Per-call holding budget · " + money(budget.toDouble()))
                    Slider(
                        value = budget,
                        onValueChange = { budget = (it / 10_000f).toInt().coerceIn(1,20) * 10_000f },
                        onValueChangeFinished = { onBudgetChanged(budget.toLong()) },
                        valueRange = 10_000f..200_000f,
                        steps = 18
                    )
                    HorizontalDivider()
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Auto-buy forecast recommendations", fontWeight = FontWeight.SemiBold)
                            Text(
                                "When enabled, every new confirmed PRIMARY forecast creates a Groww CNC GTT at the published entry price with trigger direction UP. When that fixed trigger is crossed upward, Groww sends a MARKET BUY. The published target price stays fixed. ARM must also be ON.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = state.settings.forecastAutoBuyEnabled,
                            onCheckedChange = onForecastAutoBuyChanged
                        )
                    }
                    KeyValueRow(
                        "Forecast auto-buy",
                        when {
                            state.settings.forecastAutoBuyEnabled && armed -> "ACTIVE · ARM ON"
                            state.settings.forecastAutoBuyEnabled -> "Enabled · waiting for ARM"
                            else -> "OFF · manual BUY only"
                        }
                    )
                    Text(
                        "Entry control: fixed published GTT trigger with direction UP. A stock falling through the entry does not trigger the BUY; the trigger must be crossed upward. The broker fill can differ slightly because the child order is MARKET. The published target reference is never recalculated.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (state.settings.safetyHalt) {
                        Button(
                            onClick = onResetHalt,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) { Text("Reset safety halt") }
                    }
                }
            }
        }

        item { SectionTitle("Manual Multify fallback", "Use only if a Multify notification was missed by Android.") }
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha=.24f)),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        manualSymbol,
                        { manualSymbol = it.uppercase(Locale.US).filter { ch -> ch.isLetterOrDigit() || ch in "&._-" } },
                        Modifier.fillMaxWidth(),
                        label = { Text("NSE symbol") },
                        singleLine = true
                    )
                    OutlinedTextField(manualPrice, { manualPrice = it }, Modifier.fillMaxWidth(), label = { Text("Observed price (optional)") }, singleLine = true)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { onSubmitManualSignal(manualSymbol, "BUY", manualPrice) },
                            enabled = manualSymbol.isNotBlank(),
                            modifier = Modifier.weight(1f)
                        ) { Text("BUY CALL") }
                        OutlinedButton(
                            onClick = { onSubmitManualSignal(manualSymbol, "BOOK_PROFIT", manualPrice) },
                            enabled = manualSymbol.isNotBlank(),
                            modifier = Modifier.weight(1f)
                        ) { Text("BOOK PROFIT") }
                    }
                }
            }
        }

        item { SectionTitle("Device & diagnostics", "Background access keeps Multify capture and holding monitoring alive.") }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    KeyValueRow("Notification access", if (notificationAccess) "Enabled" else "Required")
                    KeyValueRow("Background battery", if (batteryUnrestricted) "Unrestricted" else "Optimized")
                    OutlinedButton(onClick = { openNotificationAccessSettings(context) }, modifier = Modifier.fillMaxWidth()) { Text("Notification access") }
                    Button(onClick = { requestUnrestrictedBattery(context) }, modifier = Modifier.fillMaxWidth()) { Text("Allow unrestricted background") }
                    HorizontalDivider()
                    KeyValueRow("Version", BuildConfig.VERSION_NAME)
                    KeyValueRow("Build", BuildConfig.VERSION_CODE.toString())
                    KeyValueRow("Package", BuildConfig.APPLICATION_ID)
                    Button(onClick = onExportLogs, modifier = Modifier.fillMaxWidth()) { Text("Export long-only logs") }
                    OutlinedButton(onClick = onRestoreLearning, modifier = Modifier.fillMaxWidth()) {
                        Text("Restore learning from exported ZIP")
                    }
                    Text(
                        "Restore imports learning only. It never restores holdings, orders, Groww credentials, ARM state or broker settings.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionCard(
    title: String,
    body: String,
    button: String,
    negative: Boolean = false,
    onClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (negative) MaterialTheme.colorScheme.errorContainer.copy(alpha=.35f)
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha=.45f)
        ),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Warning, contentDescription = null)
                Spacer(Modifier.padding(4.dp))
                Text(title, fontWeight = FontWeight.SemiBold)
            }
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Text(button) }
        }
    }
}

@Composable
private fun EmptyState(title: String, body: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .45f)),
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
    action.contains("BUY", true) || action.contains("BOUGHT", true) || action.contains("SOLD", true) -> StatusTone.Positive
    action.contains("WAIT", true) || action.contains("REQUIRED", true) -> StatusTone.Warning
    else -> StatusTone.Info
}

@Composable
private fun pnlColor(value: Double): Color = when {
    value > 0.0 -> MaterialTheme.colorScheme.primary
    value < 0.0 -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurface
}

private fun executionTime(ms: Double, sampleCount: Int): String = when {
    sampleCount <= 0 -> "Learning"
    ms < 1000.0 -> String.format(Locale.US, "%.0f ms", ms)
    else -> String.format(Locale.US, "%.2f s", ms / 1000.0)
}

private fun executionSupport(stats: ExecutionAverageDto): String {
    if (stats.sampleCount <= 0) return "waiting for first successful fill"
    val base = "submit → confirmed fill · ${stats.sampleCount} fills"
    return if (stats.dispatchSampleCount > 0) {
        "$base · app dispatch ${executionTime(stats.averageAppDispatchMs, stats.dispatchSampleCount)}"
    } else base
}

private fun money(value: Double): String = NumberFormat.getCurrencyInstance(Locale("en", "IN")).format(value)

private fun defaultLogFileName(): String {
    val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
    val safeVersion = BuildConfig.VERSION_NAME.replace(Regex("[^A-Za-z0-9._-]"), "_")
    return "MultifyTraderPro-logs-v$safeVersion-$stamp.zip"
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
        .recoverCatching {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
}
