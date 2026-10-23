package com.multify.traderpro.ui.screens

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.multify.traderpro.data.local.SignalEventEntity
import com.multify.traderpro.data.network.DashboardDto
import com.multify.traderpro.data.preferences.AppSettings
import com.multify.traderpro.data.repository.TradingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TraderUiState(
    val settings: AppSettings = AppSettings(),
    val events: List<SignalEventEntity> = emptyList(),
    val dashboard: DashboardDto? = null,
    val loading: Boolean = false,
    val credentialsConfigured: Boolean = false,
    val message: String? = null,
    val error: String? = null
)

@HiltViewModel
class TraderViewModel @Inject constructor(
    private val repository: TradingRepository
) : ViewModel() {
    private val remote = MutableStateFlow(RemoteState())

    val uiState: StateFlow<TraderUiState> = combine(
        repository.settings,
        repository.recentEvents,
        remote
    ) { settings, events, r ->
        TraderUiState(
            settings = settings,
            events = events,
            dashboard = r.dashboard,
            loading = r.loading,
            credentialsConfigured = repository.hasBrokerCredentials(),
            message = r.message,
            error = r.error
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        TraderUiState(credentialsConfigured = repository.hasBrokerCredentials())
    )

    init {
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
                // Presentation-only fast path. Fixed has already persisted the recommendation and
                // completed its auto-buy attempt before this event is emitted.
                runCatching { repository.refreshDashboardLocal() }
                    .onSuccess { dashboard ->
                        remote.value = remote.value.copy(dashboard = dashboard)
                    }
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
                            "Forecast auto-buy enabled · fixed GTT UP entry trigger and fixed published target · ARM required"
                        else
                            "Forecast auto-buy disabled · active untriggered forecast entry GTTs are being cancelled"
                    )
                    refresh(silent = true)
                }
                .onFailure { remote.value = remote.value.copy(loading = false, error = it.userMessage()) }
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
                            "Fixed auto-buy enabled · immutable Fixed recommendations use GTT UP entry triggers · ARM required"
                        else
                            "Fixed auto-buy disabled · active untriggered Fixed entry GTTs are being cancelled"
                    )
                    refresh(silent = true)
                }
                .onFailure { remote.value = remote.value.copy(loading = false, error = it.userMessage()) }
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
        viewModelScope.launch {
            remote.value = remote.value.copy(loading = true, error = null, message = null)
            runCatching {
                check(repository.settings.first().forecastScannerEnabled) {
                    "Forecast scanner is paused for Fixed priority. Enable it in Settings before refreshing Forecast."
                }
                val rows = repository.generateDailyForecasts(force = true)
                repository.autoBuyForecastRecommendations()
                rows
            }
                .onSuccess { rows ->
                    remote.value = remote.value.copy(loading = false, message = "LONG forecast refreshed · ${rows.size} candidates", error = null)
                    refresh(silent = true)
                }
                .onFailure { remote.value = remote.value.copy(loading = false, error = it.userMessage()) }
        }
    }

    fun generateFixed() {
        viewModelScope.launch {
            remote.value = remote.value.copy(loading = true, error = null, message = null)
            runCatching {
                // Keep the repository's 5-minute anti-overlap gate. Forced overlapping scans caused
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
        viewModelScope.launch {
            remote.value = remote.value.copy(loading = true, error = null, message = null)
            runCatching { repository.executeForecast(symbol) }
                .onSuccess { result ->
                    remote.value = remote.value.copy(loading = false, message = result, error = null)
                    refresh(silent = true)
                }
                .onFailure { remote.value = remote.value.copy(loading = false, error = it.userMessage()) }
        }
    }

    fun runAfterHoursResearch() {
        viewModelScope.launch {
            remote.value = remote.value.copy(loading = true, error = null, message = null)
            runCatching { repository.runAfterHoursResearch(force = true) }
                .onSuccess { report ->
                    remote.value = remote.value.copy(loading = false, message = "Research updated · ${report.title}", error = null)
                    refresh(silent = true)
                }
                .onFailure { remote.value = remote.value.copy(loading = false, error = it.userMessage()) }
        }
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
            remote.value = remote.value.copy(loading = true, error = null, message = null)
            runCatching { repository.exportCompleteLogs(uri) }
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
                        message = "Learning restored · ${result.learningCalls} Multify rows · ${result.researchSnapshots} research snapshots · ${result.forecasts} forecasts · ${result.champions} champions · ${result.reports} reports. Trading state was not restored.",
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
