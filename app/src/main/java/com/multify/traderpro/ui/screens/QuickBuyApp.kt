package com.multify.traderpro.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.multify.traderpro.data.local.SignalEventEntity
import com.multify.traderpro.data.preferences.AppSettings
import com.multify.traderpro.data.repository.TradingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class QuickState(
    val settings: AppSettings = AppSettings(),
    val events: List<SignalEventEntity> = emptyList(),
    val working: Boolean = false,
    val message: String = "",
    val error: String = ""
)

@HiltViewModel
class QuickBuyViewModel @Inject constructor(
    private val repository: TradingRepository
) : ViewModel() {
    private val status = MutableStateFlow(QuickState())
    val state: StateFlow<QuickState> = combine(repository.settings, repository.recentEvents, status) { prefs, events, s ->
        s.copy(settings = prefs, events = events.take(10))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), QuickState())

    fun saveBroker(apiKey: String, totpSecret: String, staticIp: String) = launchTask {
        val budget = repository.settings.first().holdingBudgetRupees
        repository.saveBrokerSettings(apiKey, totpSecret, staticIp, "", budget)
        "Broker settings saved. Authenticate Groww to verify IP, DDPI and balance."
    }

    fun authenticate() = launchTask {
        repository.authenticate().detail
    }

    fun setBudget(amount: String) = launchTask {
        val value = amount.replace(",", "").replace("₹", "").trim().toLongOrNull()
            ?: error("Enter a valid amount in rupees")
        require(value in 10_000L..200_000L) { "Budget must be ₹10,000–₹2,00,000 per call" }
        repository.setHoldingBudget(value)
        "Per-call budget saved: ₹" + value
    }

    fun setArm(enabled: Boolean) = launchTask {
        repository.setArm(enabled)
        if (enabled) "ARMED for today's Multify BUY calls" else "DISARMED. Existing SELL protection stays active."
    }

    private fun launchTask(action: suspend () -> String) {
        viewModelScope.launch {
            status.value = status.value.copy(working = true, message = "", error = "")
            try {
                status.value = status.value.copy(working = false, message = action())
            } catch (t: Throwable) {
                status.value = status.value.copy(working = false, error = (t.message ?: t.javaClass.simpleName).take(240))
            }
        }
    }
}

@Composable
fun QuickBuyApp(viewModel: QuickBuyViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var showSettings by remember { mutableStateOf(false) }
    var showArmConfirm by remember { mutableStateOf(false) }
    var key by remember { mutableStateOf("") }
    var secret by remember { mutableStateOf("") }
    var ip by remember(state.settings.expectedStaticIp) { mutableStateOf(state.settings.expectedStaticIp) }
    var budget by remember(state.settings.holdingBudgetRupees) {
        mutableStateOf(state.settings.holdingBudgetRupees.toString())
    }

    MaterialTheme {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Multify QuickBuy", style = MaterialTheme.typography.headlineSmall)
            Text("Multify BUY only • Independent protected SELL", style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { showSettings = false }) { Text("Trading") }
                OutlinedButton(onClick = { showSettings = true }) { Text("Settings") }
            }
            if (state.error.isNotBlank()) {
                Text(state.error, color = MaterialTheme.colorScheme.error)
            }
            if (state.message.isNotBlank()) {
                Text(state.message, color = MaterialTheme.colorScheme.primary)
            }
            if (state.working) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            if (showSettings) {
                Text("Groww connection", style = MaterialTheme.typography.titleLarge)
                Text("Your access token is generated during authentication and stored encrypted. Never paste secrets in logs.")
                OutlinedTextField(key, { key = it }, label = { Text("Groww API key / TOTP token") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    secret, { secret = it }, label = { Text("API secret (TOTP secret)") },
                    visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(ip, { ip = it }, label = { Text("Registered static IP") }, modifier = Modifier.fillMaxWidth())
                Button(
                    onClick = {
                        viewModel.saveBroker(key, secret, ip)
                        key = ""
                        secret = ""
                    }, enabled = !state.working, modifier = Modifier.fillMaxWidth()
                ) { Text("Save") }
                Button(onClick = { viewModel.authenticate() }, enabled = !state.working, modifier = Modifier.fillMaxWidth()) {
                    Text("Authenticate Groww")
                }
                Text("Access token: " + if (state.settings.brokerAuthenticated) "Authenticated" else "Not authenticated")
                Text("Static IP: " + if (state.settings.staticIpMatched) "Verified" else "Not verified")
                Text("DDPI: " + if (state.settings.brokerDdpiEnabled) "Enabled" else "Required before ARM")
                if (state.settings.accessTokenExpiry.isNotBlank()) {
                    Text("Access token expiry: " + state.settings.accessTokenExpiry)
                }
                OutlinedButton(
                    onClick = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Grant Multify notification access") }
            } else {
                val armed = state.settings.armEffective
                Text(if (armed) "ARMED • LIVE BUY ACTIVE" else "DISARMED • BUY OFF",
                    style = MaterialTheme.typography.titleLarge,
                    color = if (armed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
                Text("Orders are placed only for approved Multify intraday trade-release notifications while ARM is on.")
                Text("Groww: " + if (state.settings.brokerAuthenticated && state.settings.staticIpMatched) "Authenticated and IP verified" else "Authentication / IP verification required")
                OutlinedTextField(
                    budget, { budget = it }, label = { Text("Maximum CNC budget per Multify call (₹)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedButton(onClick = { viewModel.setBudget(budget) }, enabled = !state.working) { Text("Save budget") }
                Button(
                    onClick = { if (armed) viewModel.setArm(false) else showArmConfirm = true },
                    enabled = !state.working,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (armed) "DISARM new BUYs" else "ARM Multify Auto BUY") }
                Text("SELL: independently managed CNC positions; broker GTT protection, hard-loss floor, and the original stepped profitable trailing logic. Multify Book Profit is never an exit command.")
                HorizontalDivider()
                Text("Recent Multify notifications", style = MaterialTheme.typography.titleMedium)
                if (state.events.isEmpty()) Text("Waiting for notifications. Enable notification access in Settings.")
                state.events.forEach { event ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text((event.symbol ?: "Multify") + " · " + event.signalType)
                            Text(event.summary ?: "", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
        if (showArmConfirm) {
            AlertDialog(
                onDismissRequest = { showArmConfirm = false },
                title = { Text("Enable live trading?") },
                text = { Text("Approved Multify notifications will place REAL Groww CNC BUY orders, up to ₹" +
                    state.settings.holdingBudgetRupees + " each. Confirm only if you are ready to trade.") },
                confirmButton = {
                    TextButton(onClick = { showArmConfirm = false; viewModel.setArm(true) }) { Text("ARM LIVE") }
                },
                dismissButton = { TextButton(onClick = { showArmConfirm = false }) { Text("Cancel") } }
            )
        }
    }
}
