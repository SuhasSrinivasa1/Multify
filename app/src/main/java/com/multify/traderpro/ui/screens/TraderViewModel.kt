package com.multify.traderpro.ui.screens

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.multify.traderpro.data.local.SignalEventEntity
import com.multify.traderpro.data.network.DashboardDto
import com.multify.traderpro.data.preferences.AppSettings
import com.multify.traderpro.data.repository.TradingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
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
                if (repository.settings.first().brokerAuthenticated) {
                    refresh(silent = true)
                }
            }
        }
    }

    fun refresh(silent: Boolean = false) {
        viewModelScope.launch {
            if (!silent) remote.value = remote.value.copy(loading = true, error = null, message = null)
            runCatching { repository.refreshDashboard() }
                .onSuccess { remote.value = RemoteState(dashboard = it, message = if (silent) null else "Broker state refreshed") }
                .onFailure { remote.value = remote.value.copy(loading = false, error = it.userMessage()) }
        }
    }

    fun saveBrokerSettings(apiKey: String, totpSecret: String, staticIp: String, packageFilter: String, budgetText: String) {
        viewModelScope.launch {
            remote.value = remote.value.copy(loading = true, error = null, message = null)
            runCatching {
                val budget = budgetText.replace(",", "").replace("₹", "").trim().toLongOrNull()
                    ?: error("Enter a valid daily budget")
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
                    val ipNote = if (result.staticIpMatched) "Static IP verified" else "Static IP mismatch — live remains blocked"
                    remote.value = remote.value.copy(loading = false, message = "${result.detail} · $ipNote", error = null)
                    refresh(silent = true)
                }
                .onFailure { remote.value = remote.value.copy(loading = false, error = it.userMessage()) }
        }
    }

    fun setLiveExecution(enabled: Boolean) {
        viewModelScope.launch {
            remote.value = remote.value.copy(loading = true, error = null, message = null)
            runCatching { repository.setLiveExecution(enabled) }
                .onSuccess {
                    remote.value = remote.value.copy(
                        loading = false,
                        message = if (enabled) "LIVE BUY & SELL enabled for today" else "Live execution disabled"
                    )
                    refresh(silent = true)
                }
                .onFailure { remote.value = remote.value.copy(loading = false, error = it.userMessage()) }
        }
    }


    fun setIntradayBudget(value: Long) {
        viewModelScope.launch {
            runCatching { repository.setIntradayBudget(value) }
                .onFailure { remote.value = remote.value.copy(error = it.userMessage()) }
        }
    }

    fun setFastTrackBudget(value: Long) {
        viewModelScope.launch {
            runCatching { repository.setFastTrackBudget(value) }
                .onFailure { remote.value = remote.value.copy(error = it.userMessage()) }
        }
    }

    fun setPostSellShortEnabled(value: Boolean) {
        viewModelScope.launch {
            runCatching { repository.setPostSellShortEnabled(value) }
                .onFailure { remote.value = remote.value.copy(error = it.userMessage()) }
        }
    }

    fun setExecutionMode(value: String) {
        viewModelScope.launch {
            runCatching { repository.setExecutionMode(value) }
                .onFailure { remote.value = remote.value.copy(error = it.userMessage()) }
        }
    }

    fun setWaveCount(value: Int) {
        viewModelScope.launch {
            runCatching { repository.setWaveCount(value) }
                .onFailure { remote.value = remote.value.copy(error = it.userMessage()) }
        }
    }

    fun setWaveRiskSettings(
        spacingPercent: Double, waveCapital: Long, maximumWaves: Int,
        maxCampaignCapital: Long, maxDailyLoss: Long, maxSingleStockLoss: Long
    ) {
        viewModelScope.launch {
            runCatching {
                repository.setWaveRiskSettings(spacingPercent, waveCapital, maximumWaves, maxCampaignCapital, maxDailyLoss, maxSingleStockLoss)
            }.onSuccess {
                remote.value = remote.value.copy(message = "Adaptive Wave risk settings saved", error = null)
            }.onFailure {
                remote.value = remote.value.copy(error = it.userMessage())
            }
        }
    }

    fun setFirstWaveMode(value: String) = setExecutionMode(value)
    fun setActiveWaveCount(value: Long) = setWaveCount(value.toInt())

    fun submitManualSignal(symbol: String, action: String, observedPriceText: String) {
        viewModelScope.launch {
            remote.value = remote.value.copy(loading = true, error = null, message = null)
            val price = observedPriceText.replace(",", "").replace("₹", "").trim().takeIf { it.isNotBlank() }?.toDoubleOrNull()
            runCatching { repository.submitManualSignal(symbol, action, price) }
                .onSuccess { id ->
                    remote.value = remote.value.copy(loading = false, message = "Manual signal processed · event #$id", error = null)
                    refresh(silent = true)
                }
                .onFailure { remote.value = remote.value.copy(loading = false, error = it.userMessage()) }
        }
    }

    fun setFastTrackExecution(enabled: Boolean) {
        viewModelScope.launch {
            remote.value = remote.value.copy(loading = true, error = null, message = null)
            runCatching { repository.setFastTrackExecution(enabled) }
                .onSuccess {
                    remote.value = remote.value.copy(
                        loading = false,
                        message = if (enabled) "Manual notification auto buy/sell enabled for today" else "Manual auto buy/sell disabled"
                    )
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
                    remote.value = remote.value.copy(loading = false, message = "Safety halt reset; live execution remains OFF")
                    refresh(silent = true)
                }
                .onFailure { remote.value = remote.value.copy(loading = false, error = it.userMessage()) }
        }
    }


    fun generateForecasts() {
        viewModelScope.launch {
            remote.value = remote.value.copy(loading = true, error = null, message = null)
            runCatching { repository.generateDailyForecasts(force = true) }
                .onSuccess { rows ->
                    remote.value = remote.value.copy(loading = false, message = "Forecast refreshed · ${rows.size} candidates", error = null)
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

    fun exportLogs(uri: Uri) {
        viewModelScope.launch {
            remote.value = remote.value.copy(loading = true, error = null, message = null)
            runCatching { repository.exportCompleteLogs(uri) }
                .onSuccess { result ->
                    remote.value = remote.value.copy(
                        loading = false,
                        message = "Complete logs exported · ${result.signalCount} signals · ${result.tradeCount} trades · ${result.entries} files",
                        error = null
                    )
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
