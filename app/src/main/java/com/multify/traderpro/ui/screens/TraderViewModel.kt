package com.multify.traderpro.ui.screens

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.multify.traderpro.data.local.SignalEventEntity
import com.multify.traderpro.data.network.DashboardDto
import com.multify.traderpro.data.preferences.AppSettings
import com.multify.traderpro.data.preferences.AppPreferences
import com.multify.traderpro.data.repository.TradingRepository
import com.multify.traderpro.data.repository.NseSymbolRepository
import com.multify.traderpro.data.repository.EquityEodResearchRepository
import com.multify.traderpro.data.repository.EquityResearchSnapshot
import com.multify.traderpro.engine.NseInstrumentMasterParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import java.time.ZoneId
import java.time.ZonedDateTime

data class TraderUiState(
    val settings: AppSettings = AppSettings(),
    val events: List<SignalEventEntity> = emptyList(),
    val dashboard: DashboardDto? = null,
    val equityResearch: EquityResearchSnapshot = EquityResearchSnapshot(),
    val researchScanning: Boolean = false,
    val loading: Boolean = false,
    val credentialsConfigured: Boolean = false,
    val betaConfigured: Boolean = false,
    val betaAuthenticated: Boolean = false,
    val brokerReconnectRequired: Boolean = false,
    val message: String? = null,
    val error: String? = null
)

@HiltViewModel
class TraderViewModel @Inject constructor(
    private val repository: TradingRepository,
    private val preferences: AppPreferences,
    private val nseSymbols: NseSymbolRepository,
    private val equityEodResearch: EquityEodResearchRepository
) : ViewModel() {
    private val remote = MutableStateFlow(RemoteState())
    private val equityResearch = MutableStateFlow(EquityResearchSnapshot())
    private val researchScanning = MutableStateFlow(false)
    private var pendingLogExport: TradingRepository.PreparedLogExport? = null

    val uiState: StateFlow<TraderUiState> = combine(
        repository.settings,
        repository.recentEvents,
        remote,
        equityResearch,
        researchScanning
    ) { settings, events, r, research, scanning ->
        TraderUiState(
            settings = settings,
            events = events,
            dashboard = r.dashboard,
            equityResearch = research,
            researchScanning = scanning,
            loading = r.loading,
            credentialsConfigured = repository.hasBrokerCredentials(),
            betaConfigured = repository.betaCredentialsConfigured(),
            betaAuthenticated = repository.betaResearchAuthenticated(),
            brokerReconnectRequired = repository.brokerReconnectRequired(),
            message = r.message,
            error = r.error
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        TraderUiState(credentialsConfigured = repository.hasBrokerCredentials())
    )

    init {
        // Migrate the previous research-only ON state to OFF before the new switch
        // can authorize real Alpha CNC orders.
        viewModelScope.launch { repository.initializeWorkingRangeAutoBuyOptIn() }
        // Screens read local NSE EOD database only. No broker or exchange HTTP request on UI refresh.
        viewModelScope.launch {
            while (isActive) {
                if(!researchScanning.value) runCatching { withContext(Dispatchers.IO) { equityEodResearch.snapshot() } }
                    .onSuccess { equityResearch.value=it }
                val now=ZonedDateTime.now(ZoneId.of("Asia/Kolkata"))
                delay(if(EquityEodResearchRepository.afterHours(now)) 15*60_000L else 60*60_000L)
            }
        }
        viewModelScope.launch {
            repository.settings.collect { settings ->
                if (settings.brokerAuthenticated) refresh(silent = true)
            }
        }
        viewModelScope.launch {
            while (isActive) {
                delay(15_000L)
                if (repository.settings.first().brokerAuthenticated) refresh(silent = true)
            }
        }
        viewModelScope.launch {
            repository.fixedDeliveryEvents.collect {
                // Presentation-only immediate refresh. The event is emitted only after
                // Fixed recommendation persistence and the auto-buy attempt have completed.
                runCatching { repository.refreshDashboardLocal() }
                    .onSuccess { dashboard ->
                        remote.value = remote.value.copy(dashboard = dashboard)
                    }
            }
        }
    }

    /** Does not call broker dashboard, quotes, holdings, or archive backfill. */
    fun refreshInstitutionalResearch() {
        viewModelScope.launch {
            if(researchScanning.value)return@launch
            researchScanning.value=true
            try {
                withContext(Dispatchers.IO){equityEodResearch.refreshInstitutionalOnly()}
                equityResearch.value=withContext(Dispatchers.IO){equityEodResearch.snapshot()}
            } catch(cancelled:CancellationException){throw cancelled}
            catch(e:Exception){
                remote.value=remote.value.copy(error="Institutional research: ${e.userMessage()}")
            } finally{researchScanning.value=false}
        }
    }

    fun refreshResearchView() {
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { equityEodResearch.snapshot() } }
                .onSuccess { equityResearch.value=it }
                .onFailure { remote.value=remote.value.copy(error="Local EOD screen: ${it.userMessage()}") }
        }
    }

    /**
     * User-requested 3M/6M low scan. Both modes recalculate local NSE EOD data.
     * Only a selected 6M scan may request a bounded after-hours archive batch.
     * This research path never calls Groww and never creates a trading signal or order.
     */
    fun scanLowRangeNow(months: Int) {
        val selectedMonths = if (months == 6) 6 else 3
        viewModelScope.launch {
            if (researchScanning.value) return@launch
            researchScanning.value = true
            remote.value = remote.value.copy(message = null, error = null)
            try {
                val downloadsAllowed = EquityEodResearchRepository.afterHours(
                    ZonedDateTime.now(ZoneId.of("Asia/Kolkata"))
                )
                val progress = if (downloadsAllowed) {
                    withContext(Dispatchers.IO) { equityEodResearch.refreshAfterHours(25) }
                } else {
                    null
                }
                val snapshot = withContext(Dispatchers.IO) { equityEodResearch.snapshot() }
                equityResearch.value = snapshot
                val run = if(selectedMonths==3) snapshot.adaptiveLowThreeRun else snapshot.adaptiveLowSixRun
                val ready = if(selectedMonths==3) snapshot.threeMonthHistoryComplete else snapshot.sixMonthHistoryComplete
                val sessions = if(selectedMonths==3) snapshot.threeMonthCompletedDays else snapshot.completedDays
                val prefix = "${selectedMonths}M"
                val resultMessage = when {
                    ready && run!=null && run.day==snapshot.latestDay ->
                        "$prefix support scan complete · ${run.qualifiedPatterns} confirmed patterns · $sessions sessions · no Groww calls." +
                            (if(progress!=null) " Imported ${progress.importedDays} NSE sessions." else " Local cached results.")
                    progress?.sourceFailure==true ->
                        "$prefix archive paused · $sessions sessions stored. ${snapshot.lastError.take(180)}. Existing evidence retained."
                    ready ->
                        "$prefix archive ready · support scan pending after hours; no candidate count claimed."
                    else ->
                        "$prefix history building · $sessions sessions stored. NSE downloads resume after 18:30 IST; no Groww calls."
                }
                remote.value = remote.value.copy(message = resultMessage, error = null)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                remote.value = remote.value.copy(
                    error = "${selectedMonths}M low scan failed: ${e.userMessage()}",
                    message = null
                )
            } finally {
                researchScanning.value = false
            }
        }
    }

    /** Import one or more user-selected official NSE UDiFF ZIP / Full Bhavcopy CSV files locally. */
    fun importNseArchives(uris: List<Uri>) {
        viewModelScope.launch {
            if (researchScanning.value) return@launch
            researchScanning.value = true
            remote.value = remote.value.copy(message = null, error = null)
            try {
                val result = withContext(Dispatchers.IO) { equityEodResearch.importLocalArchives(uris) }
                equityResearch.value = withContext(Dispatchers.IO) { equityEodResearch.snapshot() }
                val detail = result.failedFiles.take(2).joinToString(" · ")
                remote.value = remote.value.copy(
                    message = "Manual NSE import · " + result.importedSessions + " sessions · " +
                        result.importedRows + " EQ rows · " + result.skippedFiles + " skipped · " +
                        result.failedFiles.size + " rejected. Zero Groww/network calls." +
                        if (detail.isBlank()) "" else " " + detail,
                    error = null
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                remote.value = remote.value.copy(
                    error = "NSE file import failed: " + error.userMessage(),
                    message = null
                )
            } finally {
                researchScanning.value = false
            }
        }
    }

    /** Compatibility entry point for any existing in-app caller. */
    fun scanSixMonthLowsNow() = scanLowRangeNow(6)

    fun refresh(silent: Boolean = false) {
        viewModelScope.launch {
            if (!silent) remote.value = remote.value.copy(loading = true, error = null, message = null)
            runCatching { repository.refreshDashboardLocal() }
                .onSuccess { remote.value = RemoteState(dashboard = it, message = if (silent) null else "Broker state refreshed") }
                .onFailure { remote.value = remote.value.copy(loading = false, error = it.userMessage()) }
        }
    }

    fun saveBrokerSettings(apiKey: String, totpSecret: String, staticIp: String, packageFilter: String, budgetText: String) {
        viewModelScope.launch {
            remote.value = remote.value.copy(loading = true, error = null, message = null)
            runCatching {
                val budget = budgetText.replace(",", "").replace("₹", "").trim().toLongOrNull()
                    ?: error("Enter a valid holding budget")
                repository.saveBrokerSettings(apiKey, totpSecret, staticIp, packageFilter, budget)
            }.onSuccess {
                remote.value = remote.value.copy(loading = false, message = "Settings saved securely", error = null)
            }.onFailure {
                remote.value = remote.value.copy(loading = false, error = it.userMessage())
            }
        }
    }

    fun saveBetaSettings(key: String, secret: String) {
        viewModelScope.launch {
            remote.value = remote.value.copy(loading = true, error = null)
            runCatching { repository.saveBetaResearchSettings(key, secret) }
                .onSuccess { remote.value = remote.value.copy(loading = false,
                    message = "Beta credentials encrypted; authenticate Beta separately", error = null) }
                .onFailure { remote.value = remote.value.copy(loading = false, error = it.userMessage()) }
        }
    }

    fun authenticateBeta() {
        viewModelScope.launch {
            remote.value = remote.value.copy(loading = true, error = null)
            runCatching { repository.authenticateBetaResearch() }
                .onSuccess { remote.value = remote.value.copy(loading = false, message = it, error = null) }
                .onFailure { remote.value = remote.value.copy(loading = false, error = it.userMessage()) }
        }
    }

    fun authenticate() {
        viewModelScope.launch {
            remote.value = remote.value.copy(loading = true, error = null, message = null)
            runCatching { repository.authenticate() }
                .onSuccess { result ->
                    val ipNote = if (result.staticIpMatched) "Static IP verified" else "Static IP mismatch — ARM remains blocked"
                    remote.value = remote.value.copy(loading = false, message = "${result.detail} · $ipNote", error = null)
                    refresh(silent = true)
                }
                .onFailure { remote.value = remote.value.copy(loading = false, error = it.userMessage()) }
        }
    }

    fun setArm(enabled: Boolean) {
        viewModelScope.launch {
            remote.value = remote.value.copy(loading = true, error = null, message = null)
            runCatching { repository.setArm(enabled) }
                .onSuccess {
                    remote.value = remote.value.copy(
                        loading = false,
                        message = if (enabled) "ARM enabled for today · Multify calls can buy CNC holdings" else "ARM disabled"
                    )
                    refresh(silent = true)
                }
                .onFailure { remote.value = remote.value.copy(loading = false, error = it.userMessage()) }
        }
    }

    fun setForecastScannerEnabled(enabled: Boolean) {
        viewModelScope.launch {
            remote.value = remote.value.copy(loading = true, error = null, message = null)
            runCatching { repository.setForecastScannerEnabled(enabled) }
                .onSuccess {
                    remote.value = remote.value.copy(
                        loading = false,
                        message = if (enabled)
                            "Forecast background scanning enabled · Fixed and sell monitoring remain active"
                        else
                            "Forecast background scanning paused · Fixed has market-data priority"
                    )
                    refresh(silent = true)
                }
                .onFailure { remote.value = remote.value.copy(loading = false, error = it.userMessage()) }
        }
    }

    fun setForecastAutoBuy(enabled: Boolean) {
        viewModelScope.launch {
            remote.value = remote.value.copy(loading = true, error = null, message = null)
            runCatching { repository.setForecastAutoBuy(enabled) }
                .onSuccess {
                    remote.value = remote.value.copy(
                        loading = false,
                        message = if (enabled)
                            "Forecast auto-buy enabled · independent direct MARKET BUY · common sell protection remains active"
                        else
                            "Forecast auto-buy disabled · no new Forecast entries; existing holdings remain sell-protected"
                    )
                    refresh(silent = true)
                }
                .onFailure { remote.value = remote.value.copy(loading = false, error = it.userMessage()) }
        }
    }

    fun setWorkingRangeEnabled(enabled: Boolean) {
        viewModelScope.launch {
            remote.value = remote.value.copy(loading = true, error = null, message = null)
            runCatching { repository.setWorkingRangeEnabled(enabled) }
                .onSuccess {
                    remote.value = remote.value.copy(
                        loading = false,
                        message = if (enabled)
                            "Working Range Auto-Buy enabled · scanner and recommendations were already running · only new post-activation confirmations can attempt Alpha BUY"
                        else
                            "Working Range Auto-Buy disabled · Working Range scanning and recommendations continue · no new Working Range BUYs; existing holdings remain protected"
                    )
                    refresh(silent = true)
                }
                .onFailure { remote.value = remote.value.copy(loading = false, error = it.userMessage()) }
        }
    }

    fun setFixedScannerEnabled(enabled: Boolean) {
        viewModelScope.launch {
            runCatching { preferences.setFixedScannerEnabled(enabled) }
                .onSuccess {
                    remote.value = remote.value.copy(
                        message = if (enabled) "Fixed/Working Range scanning ON"
                        else "Discovery paused; verified SELL/GTT monitoring continues"
                    )
                    refresh(silent = true)
                }
                .onFailure { remote.value = remote.value.copy(error = it.userMessage()) }
        }
    }

    fun setFixedAutoBuy(enabled: Boolean) {
        viewModelScope.launch {
            remote.value = remote.value.copy(loading = true, error = null, message = null)
            runCatching { repository.setFixedAutoBuy(enabled) }
                .onSuccess {
                    remote.value = remote.value.copy(
                        loading = false,
                        message = if (enabled)
                            "Fixed auto-buy enabled · immutable Fixed recommendations use independent direct MARKET BUY · common sell protection remains active"
                        else
                            "Fixed auto-buy disabled · no new Fixed entries; existing holdings remain sell-protected"
                    )
                    refresh(silent = true)
                }
                .onFailure { remote.value = remote.value.copy(loading = false, error = it.userMessage()) }
        }
    }

    fun setMaxTotalCapital(value: Long) {
        viewModelScope.launch {
            runCatching { repository.setMaxTotalCapital(value) }
                .onSuccess { refresh(silent = true) }
                .onFailure { remote.value = remote.value.copy(error = it.userMessage()) }
        }
    }

    fun setMaxConcurrentHoldings(value: Int) {
        viewModelScope.launch {
            runCatching { repository.setMaxConcurrentHoldings(value) }
                .onSuccess { refresh(silent = true) }
                .onFailure { remote.value = remote.value.copy(error = it.userMessage()) }
        }
    }

    fun setHoldingBudget(value: Long) {
        viewModelScope.launch {
            runCatching { repository.setHoldingBudget(value) }
                .onSuccess { refresh(silent = true) }
                .onFailure { remote.value = remote.value.copy(error = it.userMessage()) }
        }
    }

    fun submitManualSignal(symbol: String, action: String, observedPriceText: String) {
        viewModelScope.launch {
            remote.value = remote.value.copy(loading = true, error = null, message = null)
            val price = observedPriceText.replace(",", "").replace("₹", "").trim().takeIf { it.isNotBlank() }?.toDoubleOrNull()
            runCatching { repository.submitManualSignal(symbol, action, price) }
                .onSuccess { id ->
                    remote.value = remote.value.copy(loading = false, message = "Manual Multify fallback processed · event #$id", error = null)
                    refresh(silent = true)
                }
                .onFailure { remote.value = remote.value.copy(loading = false, error = it.userMessage()) }
        }
    }

    fun generateForecasts() {
        remote.value = remote.value.copy(
            loading = false, message = "Forecast is frozen in v8.3. Use Fixed recommendations.", error = null
        )
    }

    fun generateFixed() {
        viewModelScope.launch {
            remote.value = remote.value.copy(loading = true, error = null, message = null)
            runCatching {
                // Keep the repository's frozen 150-second anti-overlap gate. Forced overlapping scans caused
                // repeated Groww 429 throttling in the v7.6.1 S24 Ultra diagnostic log.
                val rows = repository.generateFixedForecasts(force = false)
                repository.autoBuyFixedRecommendations()
                rows
            }.onSuccess { rows ->
                remote.value = remote.value.copy(
                    loading = false,
                    message = "Fixed engine refreshed · ${rows.size} recommendations · frozen v7.4.1 logic",
                    error = null
                )
                refresh(silent = true)
            }.onFailure { remote.value = remote.value.copy(loading = false, error = it.userMessage()) }
        }
    }

    fun manualStockMatches(query: String): List<NseInstrumentMasterParser.ListedEquity> =
        nseSymbols.search(query)

    fun manualMasterStatus(): NseSymbolRepository.Status = nseSymbols.status()

    fun manualMasterReady(): Boolean = nseSymbols.hasFullInstrumentMaster()

    fun syncManualNseStocks() {
        viewModelScope.launch {
            remote.value = remote.value.copy(loading = true, error = null, message = null)
            runCatching { withContext(Dispatchers.IO) { nseSymbols.refresh() } }
                .onSuccess { result ->
                    remote.value = remote.value.copy(
                        loading = false,
                        message = "NSE stock list synced · ${result.count} listed symbols · ${result.source}",
                        error = null
                    )
                }
                .onFailure {
                    remote.value = remote.value.copy(loading = false, error = it.userMessage())
                }
        }
    }

    /** One explicit operator tap, never an Auto-Buy or synthetic Fixed signal. */
    fun manualShortStatus(): com.multify.traderpro.engine.ManualShortPosition? =
        repository.manualShortStatus()

    fun executeManualShort(symbol: String) {
        viewModelScope.launch {
            remote.value = remote.value.copy(loading = true, error = null, message = null)
            runCatching { repository.executeManualShort(symbol) }
                .onSuccess { result ->
                    remote.value = remote.value.copy(loading = false, message = result, error = null)
                    refresh(silent = true)
                }
                .onFailure {
                    remote.value = remote.value.copy(loading = false, error = it.userMessage())
                    refresh(silent = true)
                }
        }
    }

    fun executeManualNse(symbol: String) {
        viewModelScope.launch {
            remote.value = remote.value.copy(loading = true, error = null, message = null)
            runCatching { repository.executeManualNse(symbol) }
                .onSuccess { result ->
                    remote.value = remote.value.copy(loading = false, message = result, error = null)
                    refresh(silent = true)
                }
                .onFailure { remote.value = remote.value.copy(loading = false, error = it.userMessage()) }
        }
    }

    fun executeFixed(symbol: String) {
        viewModelScope.launch {
            remote.value = remote.value.copy(loading = true, error = null, message = null)
            runCatching { repository.executeFixed(symbol) }
                .onSuccess { result ->
                    remote.value = remote.value.copy(loading = false, message = result, error = null)
                    refresh(silent = true)
                }
                .onFailure { remote.value = remote.value.copy(loading = false, error = it.userMessage()) }
        }
    }

    fun executeForecast(symbol: String) {
        remote.value = remote.value.copy(
            loading = false, error = "Forecast orders are frozen in v8.3; Alpha trading remains available for Fixed and Multify."
        )
    }

    fun runAfterHoursResearch() {
        // Historical Forecast research is frozen; no network request is allowed.
        remote.value = remote.value.copy(
            loading = false, message = "Forecast research is paused in Fixed-only v8.3", error = null
        )
    }

    fun resetHalt() {
        viewModelScope.launch {
            remote.value = remote.value.copy(loading = true, error = null, message = null)
            runCatching { repository.resetHalt() }
                .onSuccess {
                    remote.value = remote.value.copy(loading = false, message = "Safety halt reset; ARM remains OFF")
                    refresh(silent = true)
                }
                .onFailure { remote.value = remote.value.copy(loading = false, error = it.userMessage()) }
        }
    }

    fun prepareLogExport(onReady: (String) -> Unit) {
        viewModelScope.launch {
            remote.value = remote.value.copy(
                loading = true, error = null, message = "Preparing and validating diagnostics ZIP…")
            runCatching {
                repository.prepareCompleteLogs(progress = { file, count ->
                    remote.value = remote.value.copy(
                        loading = true,
                        message = "Preparing diagnostics · $count files · $file",
                        error = null)
                })
            }
                .onSuccess { prepared ->
                    pendingLogExport?.file?.delete()
                    pendingLogExport = prepared
                    val mode = if (prepared.includesResearchBackup) "Full research backup" else "Market-hours diagnostics"
                    remote.value = remote.value.copy(
                        loading = false,
                        message = "$mode ready · ${prepared.sizeBytes / 1024L} KB · choose a destination",
                        error = null
                    )
                    onReady(prepared.fileName)
                }
                .onFailure { remote.value = remote.value.copy(loading = false, error = it.userMessage()) }
        }
    }

    fun writePreparedLogExport(uri: Uri) {
        val prepared = pendingLogExport
        if (prepared == null) {
            remote.value = remote.value.copy(loading = false, error = "No prepared ZIP is waiting. Tap Export again.")
            return
        }
        viewModelScope.launch {
            remote.value = remote.value.copy(
                loading = true, error = null, message = "Writing verified ZIP to selected destination…")
            runCatching { repository.writePreparedLogs(uri, prepared) }
                .onSuccess { result ->
                    prepared.file.delete()
                    pendingLogExport = null
                    remote.value = remote.value.copy(
                        loading = false,
                        message = "ZIP saved and size-verified · ${result.signalCount} signals · ${result.tradeCount} closed holdings · ${result.entries} files",
                        error = null
                    )
                }
                .onFailure { remote.value = remote.value.copy(loading = false, error = it.userMessage()) }
        }
    }

    fun cancelPreparedLogExport() {
        pendingLogExport?.file?.delete()
        pendingLogExport = null
    }

    fun restoreLearning(uri: Uri) {
        viewModelScope.launch {
            remote.value = remote.value.copy(loading = true, error = null, message = null)
            runCatching { repository.restoreLearningFromLogs(uri) }
                .onSuccess { result ->
                    remote.value = remote.value.copy(
                        loading = false,
                        message = "Research restored · ${result.eodResearchSessions} EOD sessions · ${result.eodResearchRows} EOD rows · ${result.otherResearchRows} other research rows · ${result.workingRangeStats} Working Range stats · ${result.learningCalls} learning calls · ${result.researchSnapshots} snapshots · ${result.forecasts} forecasts · ${result.champions} champions · ${result.reports} reports · ${result.positiveEvidence} positive examples · ${result.archivedFixedOutcomes} Fixed outcomes · ${result.fixedFactorSnapshots} factor snapshots. Credentials, holdings, orders, GTTs and Auto-Buy permissions were not restored.",
                        error = null
                    )
                    refresh(silent = true)
                }
                .onFailure { remote.value = remote.value.copy(loading = false, error = it.userMessage()) }
        }
    }

    fun clearBanner() {
        remote.value = remote.value.copy(message = null, error = null)
    }

    private data class RemoteState(
        val dashboard: DashboardDto? = null,
        val loading: Boolean = false,
        val message: String? = null,
        val error: String? = null
    )

    private fun Throwable.userMessage(): String = message?.take(260) ?: javaClass.simpleName
}
