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
                runCatching { withContext(Dispatchers.IO) { equityEodResearch.snapshot() } }
                    .onSuccess { equityResearch.value=it }
                delay(60_000L)
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

    fun refreshResearchView() {
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { equityEodResearch.snapshot() } }
                .onSuccess { equityResearch.value=it }
                .onFailure { remote.value=remote.value.copy(error="Local EOD screen: ${it.userMessage()}") }
        }
    }

    /**
     * User-requested six-month-low scan. It always recalculates the local archive.
     * Official NSE downloads are attempted only in the repository's permitted
     * 18:30–08:00 IST window, in a bounded batch of at most 25 missing sessions.
     * This path never calls Groww and never creates a trading signal or order.
     */
    fun scanSixMonthLowsNow() {
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
                val resultMessage = when {
                    progress == null && snapshot.sixMonthHistoryComplete ->
                        "Local scan complete · ${snapshot.lowRows.size} validated six-month-low candidates · ${snapshot.completedDays} sessions."
                    progress == null ->
                        "Local scan complete · ${snapshot.completedDays} EOD sessions available. NSE downloads resume after 18:30 IST; history is still incomplete. No Groww calls."
                    progress.sourceFailure ->
                        "NSE archive source unavailable · ${progress.importedDays} sessions imported · ${snapshot.completedDays} stored. Existing validated results, if any, are retained."
                    snapshot.sixMonthHistoryComplete ->
                        "Scan complete · ${snapshot.lowRows.size} stocks within 0%–10% of validated six-month lows · ${snapshot.completedDays} sessions."
                    progress.importedDays > 0 ->
                        "On-demand scan complete · imported ${progress.importedDays} NSE sessions using ${progress.requestedDays} archive requests · ${snapshot.completedDays} sessions stored. History remains incomplete; tap Scan Now again for the next batch."
                    else ->
                        "Scan complete · ${snapshot.completedDays} sessions stored. No new archive files were imported; six-month claims remain withheld until history is complete."
                }
                remote.value = remote.value.copy(message = resultMessage, error = null)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                remote.value = remote.value.copy(
                    error = "Six-month-low scan failed: ${e.userMessage()}",
                    message = null
                )
            } finally {
                researchScanning.value = false
            }
        }
    }

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

    fun exportLogs(uri: Uri) {
        viewModelScope.launch {
            remote.value = remote.value.copy(
                loading = true, error = null, message = "Exporting diagnostics in background…")
            runCatching {
                repository.exportCompleteLogs(uri) { file, count ->
                    remote.value = remote.value.copy(
                        loading = true,
                        message = "Exporting diagnostics · $count files · $file",
                        error = null)
                }
            }
                .onSuccess { result ->
                    remote.value = remote.value.copy(
                        loading = false,
                        message = "Long-only logs exported · ${result.signalCount} signals · ${result.tradeCount} closed holdings · ${result.entries} files",
                        error = null
                    )
                }
                .onFailure { remote.value = remote.value.copy(loading = false, error = it.userMessage()) }
        }
    }

    fun restoreLearning(uri: Uri) {
        viewModelScope.launch {
            remote.value = remote.value.copy(loading = true, error = null, message = null)
            runCatching { repository.restoreLearningFromLogs(uri) }
                .onSuccess { result ->
                    remote.value = remote.value.copy(
                        loading = false,
                        message = "Learning restored · ${result.learningCalls} Multify rows · ${result.researchSnapshots} research snapshots · ${result.forecasts} forecasts · ${result.champions} champions · ${result.reports} reports · ${result.positiveEvidence} positive examples · ${result.archivedFixedOutcomes} archived Fixed outcomes · ${result.fixedFactorSnapshots} Fixed factor snapshots. Trading state was not restored.",
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
