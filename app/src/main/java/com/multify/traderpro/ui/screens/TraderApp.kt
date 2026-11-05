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
                    Text("Fixed engine · FROZEN", fontWeight = FontWeight.SemiBold)
                    Text("v7.4.1 · 40 factors · 15-minute candles · original selection and SELL rules preserved.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        if (state.settings.fixedAutoBuyEnabled) "Fixed Auto Buy · ON" else "Fixed Auto Buy · OFF",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
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
                    Text("Market discovery · ${health.discovery}", fontWeight = FontWeight.SemiBold)
                    Text(
                        "${String.format(Locale.US, "%.1f%%", health.discoveryCoveragePct)} NSE coverage · baseline " +
                            if (health.discoveryBaselineReady) "ready" else "warming",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        item {
            val positions = dashboard?.positions.orEmpty()
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("Open holdings & broker protection", fontWeight = FontWeight.SemiBold)
                    if (positions.isEmpty()) {
                        Text("No app-managed holdings currently open.", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        positions.forEach { p ->
                            val activeProtection = p.smartOrderMode in setOf("HARD_STOP_GTT", "TRAILING_GTT", "RECOVERY_GTT") &&
                                !p.smartOrderId.isNullOrBlank() &&
                                (p.smartOrderStatus?.uppercase(Locale.US)?.let { it in setOf("ACTIVE", "OPEN") } == true)
                            val triggered = p.smartOrderStatus?.uppercase(Locale.US)?.let { it in setOf("TRIGGERED", "COMPLETED") } == true
                            HorizontalDivider()
                            Text("${p.symbol} · ${p.quantity} shares", fontWeight = FontWeight.Medium)
                            Text(
                                when {
                                    activeProtection -> "BROKER GTT ACTIVE · ${p.smartOrderMode.replace("_", " ")}"
                                    triggered -> "BROKER GTT TRIGGERED · RECONCILING"
                                    else -> "PROTECTION NEEDS REVIEW · CHECK GROWW"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = if (activeProtection) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            )
                            Text(
                                "Entry ₹${String.format(Locale.US, "%.2f", p.averagePrice)} · LTP ₹${String.format(Locale.US, "%.2f", p.ltp ?: p.averagePrice)} · P&L ₹${String.format(Locale.US, "%.2f", p.pnl)}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                "Stop ₹${String.format(Locale.US, "%.2f", p.smartOrderTriggerPrice ?: p.stopPrice ?: 0.0)} · Target " +
                                    (p.targetPrice?.let { "₹${String.format(Locale.US, "%.2f", it)}" } ?: "—"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
        item {
            val evidence = dashboard?.fixedResearchSafety
            val prices = dashboard?.fixedFastCandidatePrices.orEmpty()
            val pulse = dashboard?.fixedPulseResearch.orEmpty()
            val early = dashboard?.fixedEarlyWatch.orEmpty()
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("Research runs in background", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Early Watch ${early.size} · Fast Watch ${prices.size} · Market Pulse ${pulse.size} · Archived observations ${evidence?.historicalSnapshots ?: 0}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        "Factor breakdowns, candidate rejections, API timings, cache details and worker traces are saved in Export Logs, not displayed here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
                val newestFixedEvent = rows.groupBy { it.symbol.uppercase(Locale.US) }
                    .mapValues { (_, events) -> events.maxOf { it.generatedAtMs } }
                rows.forEachIndexed { index, f ->
                    val oldFixedEvent = fixed &&
                        f.generatedAtMs < (newestFixedEvent[f.symbol.uppercase(Locale.US)] ?: f.generatedAtMs)
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
                        enabled = if (fixed) !oldFixedEvent && FixedBuyAvailabilityPolicy.canBuy(
                            fixedClockMsForTable, executionReady, f.status, f.executionEligible
                        ) else ForecastBuyAvailabilityPolicy.canBuy(
                            fixedClockMsForTable, executionReady, f.status, f.executionEligible
                        ),
                        modifier = Modifier.fillMaxWidth().padding(top = 7.dp)
                    ) {
                        Text(
                            when {
                                oldFixedEvent -> "Earlier Fixed setup · BUY latest instead"
                                fixed && f.status == "TARGET_HIT" -> "TARGET HIT · BUY disabled"
                                fixed && f.status != "ACTIVE" -> "Session ended · BUY disabled"
                                !fixed && f.status == "TARGET_HIT" -> "TARGET HIT · BUY disabled"
                                !fixed && f.status == "WATCH_ONLY" -> "PROVISIONAL WATCH · BUY disabled"
                                !fixed && f.status == "AWAITING_CONFIRMATION" -> "PRIMARY pending · BUY disabled"
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
                            "Event scan " + SimpleDateFormat("HH:mm:ss", Locale.US)
                                .format(Date(f.generatedAtMs)) + " IST · Fixed status: ${f.status.replace('_', ' ')} · observed high " +
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
    // Capture health is independent of the foreground trading-engine process.
    var listenerHealthClockMs by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(15_000L)
            listenerHealthClockMs = System.currentTimeMillis()
        }
    }
    val listenerPrefs = remember(listenerHealthClockMs) {
        context.getSharedPreferences("notification_listener_state_v801", Context.MODE_PRIVATE)
    }
    val listenerConnected = listenerPrefs.getBoolean("connected", false)
    val lastListenerAt = listenerPrefs.getLong("last_connected_ms", 0L)
    val lastOfficialUnivestAt = listenerPrefs.getLong("last_official_univest_seen_ms", 0L)
    val lastParsedAt = listenerPrefs.getLong("last_univest_parsed_at_ms", 0L)
    val lastParsedSymbol = listenerPrefs.getString("last_univest_parsed_symbol", "").orEmpty()
    val lastParsedStatus = listenerPrefs.getString("last_univest_parse_status", "NOT_CAPTURED").orEmpty()
    fun captureTime(ms: Long): String = if (ms <= 0L) "Never"
        else SimpleDateFormat("dd MMM HH:mm:ss", Locale.US).format(Date(ms))
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
                                "When enabled, confirmed PRIMARY Forecast calls may use direct Groww CNC MARKET BUY; all broker readiness, entry freshness, safety halt and common SELL protections still apply. Research-only rows cannot BUY.",
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
                        if (state.settings.forecastAutoBuyEnabled) "ON · execution subject to broker and quality guards" else "OFF · only verified PRIMARY calls are buy-eligible"
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
                    KeyValueRow("Notification listener", when {
                        !notificationAccess -> "PERMISSION DISABLED"
                        listenerConnected -> "Connected"
                        else -> "Disconnected / awaiting Android"
                    })
                    KeyValueRow("Listener last connected", captureTime(lastListenerAt))
                    KeyValueRow("Latest official Univest alert", captureTime(lastOfficialUnivestAt))
                    KeyValueRow("Latest Univest parse", captureTime(lastParsedAt))
                    KeyValueRow("Univest parse result",
                        if (lastParsedSymbol.isBlank()) lastParsedStatus
                        else "$lastParsedSymbol · $lastParsedStatus")
                    Text(
                        "Univest is research-only. A running trading engine does not prove the Android notification listener is connected. Only an Android-delivered verified notification can be captured automatically.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
