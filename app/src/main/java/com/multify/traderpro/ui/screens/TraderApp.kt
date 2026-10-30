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
import androidx.compose.material3.Checkbox
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
import kotlinx.coroutines.delay
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
import com.multify.traderpro.engine.FixedBuyAvailabilityPolicy
import com.multify.traderpro.data.network.DashboardDto
import com.multify.traderpro.data.network.ExecutionAverageDto
import com.multify.traderpro.engine.FixedFinalContract
import com.multify.traderpro.engine.FixedForecastQualityPolicy
import com.multify.traderpro.engine.ForecastBuyAvailabilityPolicy
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
    Forecast("Forecast", "Early-entry LONG recommendations · ≥1% target", Icons.Default.Science),
    Fixed("Fixed", "Frozen v7.4.1 recommendation engine", Icons.Default.Shield),
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
            Column {
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
                val alphaLabel = when {
                    state.settings.brokerAuthenticated && state.settings.staticIpMatched -> "ALPHA · AUTH OK · IP OK"
                    state.settings.brokerAuthenticated -> "ALPHA · AUTH OK · IP CHECK"
                    state.credentialsConfigured -> "ALPHA · AUTH REQUIRED"
                    else -> "ALPHA · NOT CONFIGURED"
                }
                val alphaTone = when {
                    state.settings.brokerAuthenticated && state.settings.staticIpMatched -> StatusTone.Positive
                    state.settings.brokerAuthenticated || state.credentialsConfigured -> StatusTone.Warning
                    else -> StatusTone.Negative
                }
                val betaLabel = when {
                    state.betaAuthenticated -> "BETA · AUTH OK · READ-ONLY"
                    state.betaConfigured -> "BETA · AUTH REQUIRED"
                    else -> "BETA · NOT CONFIGURED"
                }
                val betaTone = when {
                    state.betaAuthenticated -> StatusTone.Positive
                    state.betaConfigured -> StatusTone.Warning
                    else -> StatusTone.Neutral
                }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatusPill(alphaLabel, alphaTone, Modifier.weight(1f))
                    StatusPill(betaLabel, betaTone, Modifier.weight(1f))
                }
            }
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
                Destination.Fixed -> FixedScreen(
                    state = state,
                    onGenerate = viewModel::generateFixed,
                    onBuy = viewModel::executeFixed
                )
                Destination.Settings -> SettingsScreen(
                    state = state,
                    onSave = viewModel::saveBrokerSettings,
                    onSaveBeta = viewModel::saveBetaSettings,
                    onAuthenticateBeta = viewModel::authenticateBeta,
                    onAuthenticate = viewModel::authenticate,
                    onArmRequested = {
                        if (state.settings.armEffective) viewModel.setArm(false) else showArmConfirm = true
                    },
                    onBudgetChanged = viewModel::setHoldingBudget,
                    onForecastAutoBuyChanged = viewModel::setForecastAutoBuy,
                    onForecastScannerChanged = viewModel::setForecastScannerEnabled,
                    onFixedAutoBuyChanged = viewModel::setFixedAutoBuy,
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
    val dashboard = state.dashboard
    val learning = dashboard?.learning ?: com.multify.traderpro.data.network.LearningStatsDto()
    val fixedRows = dashboard?.fixedForecasts.orEmpty()
    val fixedSnapshot = dashboard?.fixedSnapshot ?: com.multify.traderpro.data.network.FixedSnapshotDto(
        frozenMultifyMfeAveragePct = FixedFinalContract.CANONICAL_MULTIFY_MFE_AVG_PCT,
        targetFloorPct = FixedForecastQualityPolicy.MIN_TARGET_PCT,
        fixedTargetPct = maxOf(
            FixedForecastQualityPolicy.MIN_TARGET_PCT,
            FixedFinalContract.CANONICAL_MULTIFY_MFE_AVG_PCT
        )
    )
    val fixedDailyAverage = dashboard?.fixedOutcomes?.todayTargetAveragePct

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp, 14.dp, 16.dp, 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            SectionTitle(
                "Production averages",
                "Local-only display. Opening this page does not request Groww live prices, margin, holdings or positions."
            )
        }
        item {
            MetricCard(
                label = "Multify 30-day Avg",
                value = String.format(Locale.US, "%.2f%%", learning.longAveragePct),
                supporting = "${learning.rollingCalls} MFE-observed calls across ${learning.rollingTradingDays} trading days",
                modifier = Modifier.fillMaxWidth(),
                accent = MaterialTheme.colorScheme.primary
            )
        }
        item {
            MetricCard(
                label = "Fixed Observed Avg / Stock · Today",
                value = dashboard?.fixedOutcomes?.todayObservedAveragePct?.let {
                    String.format(Locale.US, "%.2f%%", it)
                } ?: "Learning",
                supporting = "${dashboard?.fixedOutcomes?.todayObserved ?: 0}/${dashboard?.fixedOutcomes?.todayPublished ?: 0} Fixed stocks sampled after publication (MFE)",
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            MetricCard(
                label = "Fixed Target Avg / Stock · Today",
                value = fixedDailyAverage?.let { String.format(Locale.US, "%.2f%%", it) } ?: "—",
                supporting = if (fixedDailyAverage != null)
                    "${fixedRows.size} Fixed recommendation(s) today · frozen target calibration ${String.format(Locale.US, "%.2f%%", fixedSnapshot.fixedTargetPct)}"
                else
                    "Waiting for today’s Fixed recommendation · frozen target calibration ${String.format(Locale.US, "%.2f%%", fixedSnapshot.fixedTargetPct)}",
                modifier = Modifier.fillMaxWidth(),
                accent = MaterialTheme.colorScheme.secondary
            )
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
            KeyValueRow("Sell protection", "hard -2.00% · then +0.55% arm → +0.50% protect · +0.10% steps")
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
    val fixed = state.dashboard?.fixedOutcomes ?: com.multify.traderpro.data.network.FixedOutcomeStatsDto()
    val rows = state.dashboard?.fixedForecasts.orEmpty()
    fun percent(v: Double?): String = v?.let { String.format(Locale.US, "%.2f%%", it) } ?: "Learning"
    fun hitPercent(v: Double?): String = v?.let { String.format(Locale.US, "%.1f%%", it) } ?: "Learning"

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp, 14.dp, 16.dp, 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            SectionTitle(
                "Multify benchmark · unchanged",
                "Rolling 30 recommendation trading days: observed maximum price from Multify entry to its Book Profit/close. Fixed averages below are entirely separate."
            )
        }
        item {
            MetricCard(
                label = "Multify MFE Avg Upside %",
                value = String.format(Locale.US, "%.2f%%", learning.longAveragePct),
                supporting = "${learning.rollingCalls} Multify calls across ${learning.rollingTradingDays} days",
                modifier = Modifier.fillMaxWidth(),
                accent = MaterialTheme.colorScheme.primary
            )
        }
        item {
            SectionTitle(
                "Fixed recommendation averages · NEW BASELINE",
                "Fresh Fixed-only measurement since this release. One observation per stock recommendation, never per scan. Rolling last 30 Fixed trading days. Averages use the highest sampled post-publication upside, not the frozen target benchmark."
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                MetricCard(
                    label = "Today's Avg Fixed Target / Stock",
                    value = percent(fixed.todayTargetAveragePct),
                    supporting = "${fixed.todayPublished} distinct Fixed stock calls today",
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    label = "Today's Observed Fixed Avg / Stock",
                    value = percent(fixed.todayObservedAveragePct),
                    supporting = "${fixed.todayObserved}/${fixed.todayPublished} sampled after publication",
                    modifier = Modifier.weight(1f),
                    accent = MaterialTheme.colorScheme.primary
                )
            }
        }
        item {
            MetricCard(
                label = "Fixed Rolling Observed Avg / Stock",
                value = percent(fixed.rollingObservedAveragePct),
                supporting = "${fixed.observed}/${fixed.published} observed Fixed recommendations · fresh baseline only",
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                MetricCard(
                    label = "Reached +0.55% · Fixed",
                    value = hitPercent(fixed.armHitRatePct),
                    supporting = "${fixed.armHits}/${fixed.observed} · avg achieved ${percent(fixed.armHitMeanMfePct)}",
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    label = "Reached +1.00% · Fixed",
                    value = hitPercent(fixed.onePctHitRatePct),
                    supporting = "${fixed.onePctHits}/${fixed.observed} · avg achieved ${percent(fixed.onePctHitMeanMfePct)}",
                    modifier = Modifier.weight(1f)
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                MetricCard(
                    label = "Full Fixed Target Hit",
                    value = hitPercent(fixed.fullTargetHitRatePct),
                    supporting = "${fixed.fullTargetHits}/${fixed.observed} · avg achieved ${percent(fixed.fullHitMeanMfePct)} · individual published target",
                    modifier = Modifier.weight(1f),
                    accent = MaterialTheme.colorScheme.primary
                )
                MetricCard(
                    label = "Fixed Observed MFE Distribution",
                    value = fixed.medianPct?.let { "${percent(it)} median" } ?: "Learning",
                    supporting = "P25 ${percent(fixed.p25Pct)} · P75 ${percent(fixed.p75Pct)}",
                    modifier = Modifier.weight(1f)
                )
            }
        }
        item {
            SectionTitle("Today's Fixed calls · per-stock evidence",
                "Fixed recommendation entry and highest observed price after publication. '—' means a price has not yet been observed; missing evidence is not counted as a missed target.")
        }
        item {
            Card(shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    if (rows.isEmpty()) {
                        Text("No Fixed calls recorded today", style = MaterialTheme.typography.bodySmall)
                    } else rows.forEach { f ->
                        val sampled = f.observedAfterPublication
                        KeyValueRow(
                            f.symbol,
                            "Target ${percent(f.targetPct)} · MFE " +
                                (if (sampled) percent(f.maxFavourablePct) else "—") +
                                " · ${f.status.replace('_', ' ')}"
                        )
                    }
                }
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
    var forecastClockMs by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(2_000L)
            forecastClockMs = System.currentTimeMillis()
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp, 14.dp, 16.dp, 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            SectionTitle(
                "LONG holding forecast",
                "EARLY ENTRY · entire NSE market screened by bulk prices, then candidates evaluated with 1-minute candles and 5/15/30-minute context. Qualified early setups publish immediately; five-session learning continues without blocking notifications."
            )
        }
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha=.18f)),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Forecast engine · AUTOMATIC DISCOVERY", fontWeight = FontWeight.SemiBold)
                    Text(
                        if (state.settings.forecastAutoBuyEnabled)
                            "Forecast screens the entire NSE universe but uses only spare API capacity after SELL, Multify and Fixed. Automatic CNC BUY remains gated behind five completed calibration sessions."
                        else
                            "EARLY ENTRY alerts publish during calibration. Forecast automatic buying is OFF; optional manual BUY still needs broker readiness. Multiple separate setups in the same stock may appear after a completed target and new pullback.",
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
                supporting = "hard ≥1% target · Multify MFE benchmark ${String.format(Locale.US, "%.2f%%", learning.longAveragePct)} · live calibration ${forecastLearning.researchSessionsCaptured}/${forecastLearning.researchSessionGoal}",
                modifier = Modifier.fillMaxWidth(),
                accent = MaterialTheme.colorScheme.primary
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = onGenerate,
                    modifier = Modifier.weight(1f)
                ) { Text("Refresh scanner") }
                OutlinedButton(onClick = onResearch, modifier = Modifier.weight(1f)) { Text("Run research") }
            }
        }
        item { ForecastTable(rows, onBuy, state.settings.brokerAuthenticated && state.settings.staticIpMatched && state.settings.brokerDdpiEnabled && !state.settings.safetyHalt, fixedClockMsForTable = forecastClockMs) }
        item {
            val watchlist = dashboard?.forecastWatchlist.orEmpty()
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Early-entry research · PRIMARY pending / SECONDARY", fontWeight = FontWeight.SemiBold)
                    Text("These watchlist rows cannot place an order. " +
                        "A stock can form several peaks: after a completed target and pullback, " +
                        "a new independently qualified PRIMARY setup may trigger another opportunity.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (watchlist.isEmpty()) {
                        Text("No current SECONDARY candidates; research continues.",
                            style = MaterialTheme.typography.bodySmall)
                    } else {
                        watchlist.forEach { candidate ->
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .12f))
                            Text(candidate.symbol + " · " +
                                (if (candidate.qualityTier == "PRIMARY") "PRIMARY awaiting confirmation" else "SECONDARY watch") + " · " +
                                String.format(Locale.US, "%.0f%% confidence", candidate.confidence * 100.0),
                                fontWeight = FontWeight.Medium)
                            Text(candidate.reason.ifBlank { "Awaiting a fresh qualifying setup" },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 3)
                        }
                    }
                }
            }
        }
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("Multify reference · cumulative", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    KeyValueRow("Reference total", state.dashboard?.forecastReferences?.totalCalls?.toString() ?: "107")
                    KeyValueRow("Frozen historical baseline", state.dashboard?.forecastReferences?.frozenCases?.toString() ?: "107")
                    KeyValueRow("New Multify BUY calls", state.dashboard?.forecastReferences?.appendedCalls?.toString() ?: "—")
                    KeyValueRow("New calls with Book Profit", state.dashboard?.forecastReferences?.liveCompleted?.toString() ?: "—")
                    KeyValueRow("New calls with measured MFE", state.dashboard?.forecastReferences?.liveMfeMeasured?.toString() ?: "—")
                    KeyValueRow("New calls awaiting exit", state.dashboard?.forecastReferences?.livePendingOutcome?.toString() ?: "—")
                    Text(
                        "Each new verified Multify BUY notification adds one reference event; Book Profit and after-market high-price evidence enrich the same event. Fixed stays frozen at 107. These cases help Forecast ranking context, not pre-spike proof or a restriction on NSE coverage.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    HorizontalDivider()
                    Text("Multify DNA", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Forecast keeps the 40 observable factors and three families. Prior positive Fixed outcomes are the main one-way transfer signal; positive Multify outcomes may add a smaller assistance bonus. Negative/flat Fixed or Multify outcomes never subtract from ranking. Fixed never reads Forecast feedback.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    KeyValueRow("Factor families", "3")
                    KeyValueRow("Observable factors", "40")
                    KeyValueRow("Fundamental example cases", MultifyReverseEngineering.positiveCases.size.toString())
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
private fun FixedScreen(
    state: TraderUiState,
    onGenerate: () -> Unit,
    onBuy: (String) -> Unit
) {
    val dashboard = state.dashboard
    val rows = dashboard?.fixedForecasts.orEmpty().sortedBy { it.rank }
    val snapshot = dashboard?.fixedSnapshot ?: com.multify.traderpro.data.network.FixedSnapshotDto(
        snapshotId = FixedFinalContract.CONTRACT_ID,
        sourceVersion = "v7.4.1 final reference",
        frozenAtMs = FixedFinalContract.REFERENCE_EXPORT_AT_MS,
        learningCalls = FixedFinalContract.CANONICAL_LEARNING_CALLS,
        historicalForecasts = 0,
        researchSnapshots = 0,
        champions = 0,
        frozenMultifyMfeAveragePct = FixedFinalContract.CANONICAL_MULTIFY_MFE_AVG_PCT,
        targetFloorPct = FixedForecastQualityPolicy.MIN_TARGET_PCT,
        fixedTargetPct = maxOf(
            FixedForecastQualityPolicy.MIN_TARGET_PCT,
            FixedFinalContract.CANONICAL_MULTIFY_MFE_AVG_PCT
        )
    )
    val health = dashboard?.fixedHealth ?: com.multify.traderpro.data.network.EngineHealthDto()

    var fixedClockMs by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(2_000L)
            fixedClockMs = System.currentTimeMillis()
        }
    }
    val fixedMarketOpen = FixedBuyAvailabilityPolicy.nseRegularSession(fixedClockMs)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp, 14.dp, 16.dp, 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            SectionTitle(
                "Fixed LONG recommendations",
                "Frozen v7.4.1 recommendation intelligence. Live market data changes; the model, reference cases, parameters and embedded calibration snapshot do not."
            )
        }
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha=.18f)),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("Fixed engine · IMMUTABLE", fontWeight = FontWeight.SemiBold)
                    KeyValueRow("Snapshot", snapshot.snapshotId.ifBlank { "Capturing…" })
                    KeyValueRow("Source", snapshot.sourceVersion.ifBlank { "v7.4.1" })
                    KeyValueRow(
                        "Snapshot SHA",
                        snapshot.sha256.takeIf { it.isNotBlank() }?.take(12)?.plus("…") ?: "Pending"
                    )
                    KeyValueRow("Frozen Multify calibration calls", snapshot.learningCalls.toString())
                    KeyValueRow("Prior Forecast feedback rows", snapshot.historicalForecasts.toString())
                    KeyValueRow("Prior research-feedback rows", snapshot.researchSnapshots.toString())
                    KeyValueRow("Prior champion bonuses", snapshot.champions.toString())
                    Text(
                        "The 0 feedback rows are intentional, not missing phone history. The accepted v7.4.1 reference session did not use prior Forecast/champion/research feedback; keeping them empty prevents post-hoc learning.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        if (state.settings.fixedAutoBuyEnabled)
                            "Fixed Auto-buy is ON independently. Every new Fixed PRIMARY recommendation is sent as its own direct CNC MARKET BUY before presentation."
                        else
                            "Fixed scanning continues with auto-buy OFF. Recommendations remain manual; Multify ARM is separate.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        item {
            MetricCard(
                label = "Fixed Avg Upside · FROZEN",
                value = String.format(Locale.US, "%.2f%%", snapshot.fixedTargetPct),
                supporting = "hard floor ≥${String.format(Locale.US, "%.2f%%", snapshot.targetFloorPct)} · frozen Multify MFE benchmark ${String.format(Locale.US, "%.2f%%", snapshot.frozenMultifyMfeAveragePct)} · this value does not learn or drift",
                modifier = Modifier.fillMaxWidth(),
                accent = MaterialTheme.colorScheme.primary
            )
        }
        item {
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
                    Text("Fixed discovery · ${health.discovery}", fontWeight = FontWeight.SemiBold)
                    KeyValueRow("NSE coverage", String.format(Locale.US, "%.1f%%", health.discoveryCoveragePct))
                    KeyValueRow("Baseline", if (health.discoveryBaselineReady) "Ready" else "Warming")
                    KeyValueRow("Baseline age", ageText)
                    KeyValueRow("Failed batches", health.discoveryBatchErrors.toString())
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = onGenerate, modifier = Modifier.weight(1f)) { Text("Refresh Fixed") }
                OutlinedButton(
                    onClick = {},
                    enabled = false,
                    modifier = Modifier.weight(1f)
                ) { Text("Learning locked") }
            }
        }
        item { ForecastTable(rows, onBuy,
            state.settings.brokerAuthenticated && state.settings.staticIpMatched && state.settings.brokerDdpiEnabled && !state.settings.safetyHalt,
            fixed = true, fixedMarketOpen = fixedMarketOpen, fixedClockMsForTable = fixedClockMs
        ) }
        item {
            val held = dashboard?.fixedWatchlist.orEmpty()
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Fixed · Entry-safety holds", fontWeight = FontWeight.SemiBold)
                    Text("These are genuine Fixed PRIMARY evaluations blocked by the existing " +
                        "anti-chase guard. They are NOT trading recommendations; BUY is disabled.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (held.isEmpty()) {
                        Text("No fresh safety-held PRIMARY candidates in the latest scan.",
                            style = MaterialTheme.typography.bodySmall)
                    } else held.forEach { candidate ->
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .12f))
                        Text(candidate.symbol + " · SAFETY HOLD · BUY disabled", fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("What is frozen", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "The complete v7.4.1 selection pipeline used by the accepted reference session is frozen: strategy implementation, 40-factor model, factor-family rules, quality gates, publication rules, Multify reference cases and target calibration. Prior Forecast/research/champion feedback is intentionally empty in the final reference state. Fixed outcome tracking is diagnostic only and never feeds future Fixed ranking.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ForecastTable(
    rows: List<ForecastDto>, onBuy: (String) -> Unit, executionReady: Boolean,
    fixed: Boolean = false, fixedMarketOpen: Boolean = true, fixedClockMsForTable: Long = 0L
) {
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
                        enabled = if (fixed) FixedBuyAvailabilityPolicy.canBuy(
                            fixedClockMsForTable, executionReady, f.status, f.executionEligible
                        ) else ForecastBuyAvailabilityPolicy.canBuy(
                            fixedClockMsForTable, executionReady, f.status, f.executionEligible
                        ),
                        modifier = Modifier.fillMaxWidth().padding(top = 7.dp)
                    ) {
                        Text(
                            when {
                                fixed && f.status == "TARGET_HIT" -> "TARGET HIT · BUY disabled"
                                fixed && f.status != "ACTIVE" -> "Session ended · BUY disabled"
                                !fixed && f.status == "TARGET_HIT" -> "TARGET HIT · BUY disabled"
                                !fixed && f.status != "ACTIVE" -> "Finished · BUY disabled"
                                !fixed && !com.multify.traderpro.engine.FixedBuyAvailabilityPolicy.nseRegularSession(fixedClockMsForTable) -> "Market closed · BUY disabled"
                                fixed && !fixedMarketOpen -> "Market closed · BUY disabled"
                                !f.executionEligible -> f.qualityTier + " · observation only"
                                !executionReady -> "Broker readiness required · " + f.symbol
                                else -> "BUY " + f.symbol + " · CNC"
                            }
                        )
                    }
                    if (fixed) {
                        Text(
                            "Fixed status: ${f.status.replace('_', ' ')} · observed high " +
                                (if (f.observedAfterPublication) "+${String.format(Locale.US, "%.2f", f.maxFavourablePct)}%" else "—"),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
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
    onSaveBeta: (String, String) -> Unit,
    onAuthenticateBeta: () -> Unit,
    onAuthenticate: () -> Unit,
    onArmRequested: () -> Unit,
    onBudgetChanged: (Long) -> Unit,
    onForecastAutoBuyChanged: (Boolean) -> Unit,
    onForecastScannerChanged: (Boolean) -> Unit,
    onFixedAutoBuyChanged: (Boolean) -> Unit,
    onSubmitManualSignal: (String, String, String) -> Unit,
    onResetHalt: () -> Unit,
    onExportLogs: () -> Unit,
    onRestoreLearning: () -> Unit
) {
    val context = LocalContext.current
    var apiKey by remember { mutableStateOf("") }
    var betaApiKey by remember { mutableStateOf("") }
    var betaTotpSecret by remember { mutableStateOf("") }
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
        item { SectionTitle("Alpha — trading account", "Fixed, Multify, all BUY/SELL/GTT, portfolio and static IP remain on Alpha.") }
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
                    KeyValueRow("Groww", if (state.settings.brokerAuthenticated) "Authenticated" else "Reconnect Groww — trading and broker monitoring paused; verify holdings/GTT in Groww")
                    KeyValueRow("DDPI", if (state.settings.brokerDdpiEnabled) "Enabled" else "Required for ARM")
                    KeyValueRow("Static IP", if (state.settings.staticIpMatched) "Verified" else "Not verified")
                    Text(
                        "A toggle being ON does not confirm an order was placed. Groww must accept quotes, margin requests and BUY orders; broker cooldowns can temporarily block entries.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item { SectionTitle("Beta — Forecast research only", "Separate Groww account. No static IP field, no orders, no holdings access.") }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha=.24f)),
                shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(betaApiKey, { betaApiKey = it }, Modifier.fillMaxWidth(),
                        label = { Text("Beta Groww TOTP API key") },
                        visualTransformation = PasswordVisualTransformation(), singleLine = true)
                    OutlinedTextField(betaTotpSecret, { betaTotpSecret = it }, Modifier.fillMaxWidth(),
                        label = { Text("Beta Groww TOTP secret") },
                        visualTransformation = PasswordVisualTransformation(), singleLine = true)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = { onSaveBeta(betaApiKey, betaTotpSecret) },
                            modifier = Modifier.weight(1f)) { Text("Save Beta") }
                        Button(onClick = onAuthenticateBeta, enabled = state.betaConfigured,
                            modifier = Modifier.weight(1f)) { Text("Authenticate Beta") }
                    }
                    KeyValueRow("Beta research", when {
                        state.betaAuthenticated -> "Authenticated — read-only market data"
                        state.betaConfigured -> "Configured — authenticate for research"
                        else -> "Not configured — Forecast uses Alpha's spare capacity"
                    })
                    Text("Beta is strictly market-data only. All trading and protective orders remain on Alpha. " +
                        "When Beta is configured but unauthenticated, Forecast pauses instead of consuming Alpha quota. " +
                        "Separate API keys do not guarantee separate broker rate limits.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                        Switch(checked = armed, onCheckedChange = { onArmRequested() }, enabled = true) // Allow tap to display the ARM rejection reason.
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
                            Text("Fixed-only performance mode", fontWeight = FontWeight.SemiBold)
                            Text(
                                "ON pauses background Forecast discovery/research, preserving Fixed and all existing holdings. Turning this ON also disables Forecast auto-buy; Forecast code and history are retained.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Checkbox(
                            checked = !state.settings.forecastScannerEnabled,
                            onCheckedChange = { fixedOnly -> onForecastScannerChanged(!fixedOnly) }
                        )
                    }
                    KeyValueRow("Forecast background discovery", if (state.settings.forecastScannerEnabled) "ON · opportunistic" else "PAUSED · Fixed-only")
                    HorizontalDivider()
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Auto-buy forecast recommendations", fontWeight = FontWeight.SemiBold)
                            Text(
                                "After five completed calibration sessions, this toggle independently allows new confirmed PRIMARY Forecast calls to use direct Groww CNC MARKET BUY. Multify ARM is separate; common broker readiness, safety halt and sell protection still apply.",
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
                        if (state.settings.forecastAutoBuyEnabled) "ON · execution subject to broker and calibration guards" else "OFF · five completed sessions required before activation"
                    )
                    Text(
                        "Entry control: no Fixed entry GTT. Auto-buy uses a fresh quote for sizing and sends a direct CNC MARKET BUY immediately. The actual broker fill becomes the holding average and trailing anchor; the live execution target keeps the frozen recommendation target percentage but is anchored to that actual fill.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    HorizontalDivider()
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Auto-buy Fixed recommendations", fontWeight = FontWeight.SemiBold)
                            Text(
                                "When enabled, every new PRIMARY recommendation from the immutable Fixed engine gets its own Groww CNC MARKET BUY. This toggle is independent from Multify ARM; available CNC cash, broker readiness and safety checks still apply.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = state.settings.fixedAutoBuyEnabled,
                            onCheckedChange = onFixedAutoBuyChanged
                        )
                    }
                    KeyValueRow(
                        "Fixed auto-buy",
                        if (state.settings.fixedAutoBuyEnabled) "ON · entries require fresh call and broker readiness" else "OFF · manual BUY only"
                    )
                    Text(
                        "Fixed remains immutable. Forecast may read prior positive Fixed outcomes, but can never write into Fixed or change its v7.4.1 rules. Every app-owned Multify, Fixed and Forecast LONG uses the same sell protection: hard -2% loss floor, then the existing +0.55% arm → +0.50% protected gain → +0.10% ratchet when profitable. No averaging down.",
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
