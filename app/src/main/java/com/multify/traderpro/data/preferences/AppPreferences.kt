package com.multify.traderpro.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "multify_settings")

data class AppSettings(
    val expectedStaticIp: String = "",
    val packageFilter: String = "",
    val holdingBudgetRupees: Long = 200_000L,
    val maxTotalCapitalRupees: Long = 200_000L,
    val maxConcurrentHoldings: Int = 3,
    val armEnabled: Boolean = false,
    val forecastScannerEnabled: Boolean = false,
    val forecastAutoBuyEnabled: Boolean = false,
    val fixedScannerEnabled: Boolean = true,
    val fixedAutoBuyEnabled: Boolean = false,
    val fixedAutoBuyActivatedAtMs: Long = 0L,
    /** Independent Working Range Auto-Buy permission; the research scanner is always on. */
    val workingRangeEnabled: Boolean = false,
    val workingRangeActivatedAtMs: Long = 0L,
    val onboardingComplete: Boolean = false,
    val brokerAuthenticated: Boolean = false,
    val authenticatedAtMs: Long = 0L,
    val accessTokenExpiry: String = "",
    val brokerUcc: String = "",
    val brokerDdpiEnabled: Boolean = false,
    val verifiedPublicIp: String = "",
    val staticIpMatched: Boolean = false,
    val safetyHalt: Boolean = false,
    val armedDate: String = "",
    val lastNotificationAtMs: Long = 0L,
    val listenerHeartbeatAtMs: Long = 0L,
    val listenerReconnectCount: Long = 0L,
    val lastListenerReconnectAtMs: Long = 0L,
    val lastEventProcessingLatencyMs: Long = 0L,
    val lastOrderDispatchPrepMicros: Long = 0L,
    val lastBrokerAckLatencyMs: Long = 0L,
    val serviceHeartbeatAtMs: Long = 0L,
    val lastMarketDataAtMs: Long = 0L
) {
    val armEffective: Boolean get() =
        armEnabled && armedDate == LocalDate.now().toString() && !safetyHalt
}

@Singleton
class AppPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val expectedStaticIp = stringPreferencesKey("expected_static_ip")
        val packageFilter = stringPreferencesKey("package_filter")
        val holdingBudgetRupees = longPreferencesKey("daily_budget_rupees")
        val maxTotalCapitalRupees = longPreferencesKey("v9011_max_total_capital_rupees")
        val maxConcurrentHoldings = intPreferencesKey("v9011_max_concurrent_holdings")
        val armEnabled = booleanPreferencesKey("live_execution_enabled")
        val forecastScannerEnabled = booleanPreferencesKey("forecast_scanner_enabled")
        val forecastEarlyEntryMigration = booleanPreferencesKey("forecast_early_entry_v788_enabled")
        val forecastAutoBuyEnabled = booleanPreferencesKey("forecast_auto_buy_enabled")
        val fixedScannerEnabled = booleanPreferencesKey("fixed_performance_scanner_enabled_v9027")
        val fixedAutoBuyEnabled = booleanPreferencesKey("fixed_auto_buy_enabled")
        val v9027NewEntryReset = booleanPreferencesKey("fixed_performance_v9027_new_buy_consent_reset")
        val fixedAutoBuyActivatedAtMs = longPreferencesKey("fixed_auto_buy_activated_at_ms")
        val workingRangeEnabled = booleanPreferencesKey("working_range_enabled_v9020")
        val workingRangeActivatedAtMs = longPreferencesKey("working_range_activated_at_ms_v9021")
        val workingRangeAutoBuyResetV9021 = booleanPreferencesKey("working_range_auto_buy_reset_v9021")
        val v7931EntryToggleReset = booleanPreferencesKey("v7931_entry_toggles_off_once")
        val onboardingComplete = booleanPreferencesKey("onboarding_complete")
        val brokerAuthenticated = booleanPreferencesKey("broker_authenticated")
        val authenticatedAtMs = longPreferencesKey("authenticated_at_ms")
        val accessTokenExpiry = stringPreferencesKey("access_token_expiry")
        val brokerUcc = stringPreferencesKey("broker_ucc")
        val brokerDdpiEnabled = booleanPreferencesKey("broker_ddpi_enabled")
        val verifiedPublicIp = stringPreferencesKey("verified_public_ip")
        val staticIpMatched = booleanPreferencesKey("static_ip_matched")
        val safetyHalt = booleanPreferencesKey("safety_halt")
        val armedDate = stringPreferencesKey("live_enabled_date")
        val lastNotificationAtMs = longPreferencesKey("last_notification_at_ms")
        val listenerHeartbeatAtMs = longPreferencesKey("listener_heartbeat_at_ms")
        val listenerReconnectCount = longPreferencesKey("listener_reconnect_count")
        val lastListenerReconnectAtMs = longPreferencesKey("last_listener_reconnect_at_ms")
        val lastEventProcessingLatencyMs = longPreferencesKey("last_event_processing_latency_ms")
        val lastOrderDispatchPrepMicros = longPreferencesKey("last_order_dispatch_prep_micros")
        val lastBrokerAckLatencyMs = longPreferencesKey("last_broker_ack_latency_ms")
        val serviceHeartbeatAtMs = longPreferencesKey("service_heartbeat_at_ms")
        val lastMarketDataAtMs = longPreferencesKey("last_market_data_at_ms")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { it.toSettings() }

    suspend fun updateBrokerSettings(expectedStaticIp: String, packageFilter: String, holdingBudgetRupees: Long) {
        context.dataStore.edit {
            it[Keys.expectedStaticIp] = expectedStaticIp.trim()
            it[Keys.packageFilter] = packageFilter.trim()
            it[Keys.holdingBudgetRupees] = holdingBudgetRupees.coerceIn(10_000L, 200_000L)
            it[Keys.onboardingComplete] = true
        }
    }

    suspend fun setHoldingBudget(value: Long) {
        context.dataStore.edit { it[Keys.holdingBudgetRupees] = value.coerceIn(10_000L, 200_000L) }
    }

    suspend fun setMaxTotalCapital(value: Long) {
        context.dataStore.edit { it[Keys.maxTotalCapitalRupees] = value.coerceIn(10_000L, 2_000_000L) }
    }

    suspend fun setMaxConcurrentHoldings(value: Int) {
        context.dataStore.edit { it[Keys.maxConcurrentHoldings] = value.coerceIn(1, 10) }
    }

    suspend fun updateAuthState(
        authenticated: Boolean,
        authenticatedAtMs: Long = 0L,
        accessTokenExpiry: String = "",
        brokerUcc: String = "",
        brokerDdpiEnabled: Boolean = false,
        verifiedPublicIp: String = "",
        staticIpMatched: Boolean = false
    ) {
        context.dataStore.edit {
            it[Keys.brokerAuthenticated] = authenticated
            it[Keys.authenticatedAtMs] = authenticatedAtMs
            it[Keys.accessTokenExpiry] = accessTokenExpiry
            it[Keys.brokerUcc] = brokerUcc
            it[Keys.brokerDdpiEnabled] = brokerDdpiEnabled
            it[Keys.verifiedPublicIp] = verifiedPublicIp
            it[Keys.staticIpMatched] = staticIpMatched
            if (!authenticated) {
                it[Keys.armEnabled] = false
                it[Keys.armedDate] = ""
            }
        }
    }

    suspend fun setArm(value: Boolean) {
        context.dataStore.edit {
            it[Keys.armEnabled] = value
            it[Keys.armedDate] = if (value) LocalDate.now().toString() else ""
        }
    }

    /** Explicit one-time migration: enable Forecast from the next session without enabling trading. */
    suspend fun activateForecastEarlyEntryOnce() {
        context.dataStore.edit {
            if (it[Keys.forecastEarlyEntryMigration] != true) {
                it[Keys.forecastScannerEnabled] = true
                it[Keys.forecastEarlyEntryMigration] = true
            }
        }
    }

    suspend fun setForecastScannerEnabled(value: Boolean) {
        context.dataStore.edit { it[Keys.forecastScannerEnabled] = value }
    }

    suspend fun setForecastAutoBuy(value: Boolean) {
        context.dataStore.edit { it[Keys.forecastAutoBuyEnabled] = value }
    }

    /** A one-time upgrade migration: previous saved ON states must not arm new purchases.
     * This never changes protective monitoring for already-held, app-owned positions.
     * It also leaves the research scanner enabled so Forecast can continue learning.
     */
    suspend fun disableAutomaticEntriesOnceForV7931() {
        context.dataStore.edit {
            if (it[Keys.v7931EntryToggleReset] == true) return@edit
            it[Keys.armEnabled] = false
            it[Keys.armedDate] = ""
            it[Keys.fixedAutoBuyEnabled] = false
            it[Keys.fixedAutoBuyActivatedAtMs] = 0L
            it[Keys.forecastAutoBuyEnabled] = false
            it[Keys.v7931EntryToggleReset] = true
        }
    }

    /**
     * v9.0.21 changes the Working Range switch into an explicit live-entry permission.
     * Reset the old v9.0.20 research-only ON state once so an upgrade can NEVER
     * silently turn an already-enabled research switch into real-money Auto-Buy.
     * The user must deliberately turn the switch ON again after this migration.
     */
    suspend fun migrateWorkingRangeToExplicitAutoBuyOptInOnce() {
        context.dataStore.edit {
            if (it[Keys.workingRangeAutoBuyResetV9021] == true) return@edit
            it[Keys.workingRangeEnabled] = false
            it[Keys.workingRangeActivatedAtMs] = 0L
            it[Keys.workingRangeAutoBuyResetV9021] = true
        }
    }

    /** ON permits only newly confirmed Working Range events to attempt Alpha Auto-Buy; research always runs. */
    suspend fun setWorkingRangeEnabled(value: Boolean) {
        context.dataStore.edit {
            if (value) {
                if (it[Keys.workingRangeEnabled] != true) {
                    it[Keys.workingRangeActivatedAtMs] = System.currentTimeMillis().coerceAtLeast(1L)
                }
                it[Keys.workingRangeEnabled] = true
            } else {
                it[Keys.workingRangeEnabled] = false
                it[Keys.workingRangeActivatedAtMs] = 0L
            }
        }
    }

    /** A new ON session is a time boundary for eligible Fixed recommendation events.
     * Persist this atomically with the toggle so a restarted service cannot re-buy
     * previously published ACTIVE recommendations. Legacy ON without a timestamp
     * is intentionally non-executable until the user toggles OFF then ON again.
     */
    /** Discovery permission only; never suspends protection, GTT or SELL recovery.
     * Turning OFF also revokes both new BUY permissions, not existing broker orders.
     */
    suspend fun setFixedScannerEnabled(value: Boolean) {
        context.dataStore.edit {
            it[Keys.fixedScannerEnabled] = value
            if (!value) {
                it[Keys.fixedAutoBuyEnabled] = false
                it[Keys.fixedAutoBuyActivatedAtMs] = 0L
                it[Keys.workingRangeEnabled] = false
                it[Keys.workingRangeActivatedAtMs] = 0L
            }
        }
    }

    /** One-time re-authorization of live BUY after engine/stop policy migration. */
    suspend fun resetLiveEntryPermissionsOnceForV9027() {
        context.dataStore.edit {
            if (it[Keys.v9027NewEntryReset] == true) return@edit
            it[Keys.fixedAutoBuyEnabled] = false
            it[Keys.fixedAutoBuyActivatedAtMs] = 0L
            it[Keys.workingRangeEnabled] = false
            it[Keys.workingRangeActivatedAtMs] = 0L
            it[Keys.armEnabled] = false
            it[Keys.armedDate] = ""
            it[Keys.v9027NewEntryReset] = true
        }
    }

    suspend fun setFixedAutoBuy(value: Boolean) {
        context.dataStore.edit {
            if (value) {
                if (it[Keys.fixedAutoBuyEnabled] != true) {
                    it[Keys.fixedAutoBuyActivatedAtMs] = System.currentTimeMillis().coerceAtLeast(1L)
                }
                it[Keys.fixedAutoBuyEnabled] = true
            } else {
                it[Keys.fixedAutoBuyEnabled] = false
                it[Keys.fixedAutoBuyActivatedAtMs] = 0L
            }
        }
    }

    suspend fun setSafetyHalt(value: Boolean) {
        context.dataStore.edit {
            it[Keys.safetyHalt] = value
            if (value) {
                it[Keys.armEnabled] = false
                it[Keys.armedDate] = ""
            }
        }
    }

    suspend fun recordListenerConnected(nowMs: Long = System.currentTimeMillis()) {
        context.dataStore.edit { it[Keys.listenerHeartbeatAtMs] = nowMs }
    }

    suspend fun recordListenerReconnect(nowMs: Long = System.currentTimeMillis()) {
        context.dataStore.edit {
            it[Keys.listenerReconnectCount] = (it[Keys.listenerReconnectCount] ?: 0L) + 1L
            it[Keys.lastListenerReconnectAtMs] = nowMs
        }
    }

    suspend fun recordNotification(nowMs: Long = System.currentTimeMillis()) {
        context.dataStore.edit { it[Keys.lastNotificationAtMs] = nowMs }
    }

    suspend fun recordEventLatency(latencyMs: Long) {
        context.dataStore.edit { it[Keys.lastEventProcessingLatencyMs] = latencyMs.coerceAtLeast(0L) }
    }

    suspend fun recordOrderLatency(dispatchPrepMicros: Long, brokerAckLatencyMs: Long) {
        context.dataStore.edit {
            it[Keys.lastOrderDispatchPrepMicros] = dispatchPrepMicros.coerceAtLeast(0L)
            it[Keys.lastBrokerAckLatencyMs] = brokerAckLatencyMs.coerceAtLeast(0L)
        }
    }

    suspend fun recordServiceHeartbeat(nowMs: Long = System.currentTimeMillis()) {
        context.dataStore.edit { it[Keys.serviceHeartbeatAtMs] = nowMs }
    }

    suspend fun recordMarketData(nowMs: Long = System.currentTimeMillis()) {
        context.dataStore.edit { it[Keys.lastMarketDataAtMs] = nowMs }
    }

    private fun Preferences.toSettings() = AppSettings(
        expectedStaticIp = this[Keys.expectedStaticIp].orEmpty(),
        packageFilter = this[Keys.packageFilter].orEmpty(),
        holdingBudgetRupees = (this[Keys.holdingBudgetRupees] ?: 200_000L).coerceIn(10_000L, 200_000L),
        maxTotalCapitalRupees = (this[Keys.maxTotalCapitalRupees] ?: 200_000L).coerceIn(10_000L, 2_000_000L),
        maxConcurrentHoldings = (this[Keys.maxConcurrentHoldings] ?: 3).coerceIn(1, 10),
        armEnabled = this[Keys.armEnabled] ?: false,
        forecastScannerEnabled = this[Keys.forecastScannerEnabled] ?: false,
        forecastAutoBuyEnabled = this[Keys.forecastAutoBuyEnabled] ?: false,
        fixedScannerEnabled = this[Keys.fixedScannerEnabled] ?: true,
        fixedAutoBuyEnabled = this[Keys.fixedAutoBuyEnabled] ?: false,
        fixedAutoBuyActivatedAtMs = this[Keys.fixedAutoBuyActivatedAtMs] ?: 0L,
        workingRangeEnabled = this[Keys.workingRangeEnabled] ?: false,
        workingRangeActivatedAtMs = this[Keys.workingRangeActivatedAtMs] ?: 0L,
        onboardingComplete = this[Keys.onboardingComplete] ?: false,
        brokerAuthenticated = this[Keys.brokerAuthenticated] ?: false,
        authenticatedAtMs = this[Keys.authenticatedAtMs] ?: 0L,
        accessTokenExpiry = this[Keys.accessTokenExpiry].orEmpty(),
        brokerUcc = this[Keys.brokerUcc].orEmpty(),
        brokerDdpiEnabled = this[Keys.brokerDdpiEnabled] ?: false,
        verifiedPublicIp = this[Keys.verifiedPublicIp].orEmpty(),
        staticIpMatched = this[Keys.staticIpMatched] ?: false,
        safetyHalt = this[Keys.safetyHalt] ?: false,
        armedDate = this[Keys.armedDate].orEmpty(),
        lastNotificationAtMs = this[Keys.lastNotificationAtMs] ?: 0L,
        listenerHeartbeatAtMs = this[Keys.listenerHeartbeatAtMs] ?: 0L,
        listenerReconnectCount = this[Keys.listenerReconnectCount] ?: 0L,
        lastListenerReconnectAtMs = this[Keys.lastListenerReconnectAtMs] ?: 0L,
        lastEventProcessingLatencyMs = this[Keys.lastEventProcessingLatencyMs] ?: 0L,
        lastOrderDispatchPrepMicros = this[Keys.lastOrderDispatchPrepMicros] ?: 0L,
        lastBrokerAckLatencyMs = this[Keys.lastBrokerAckLatencyMs] ?: 0L,
        serviceHeartbeatAtMs = this[Keys.serviceHeartbeatAtMs] ?: 0L,
        lastMarketDataAtMs = this[Keys.lastMarketDataAtMs] ?: 0L
    )
}
