package com.multify.traderpro.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
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
    val armEnabled: Boolean = false,
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
        val armEnabled = booleanPreferencesKey("live_execution_enabled")
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
        context.dataStore.edit { it[Keys.serviceHeartbeatAtMs] = nowMs }
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
        armEnabled = this[Keys.armEnabled] ?: false,
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
        listenerReconnectCount = this[Keys.listenerReconnectCount] ?: 0L,
        lastListenerReconnectAtMs = this[Keys.lastListenerReconnectAtMs] ?: 0L,
        lastEventProcessingLatencyMs = this[Keys.lastEventProcessingLatencyMs] ?: 0L,
        lastOrderDispatchPrepMicros = this[Keys.lastOrderDispatchPrepMicros] ?: 0L,
        lastBrokerAckLatencyMs = this[Keys.lastBrokerAckLatencyMs] ?: 0L,
        serviceHeartbeatAtMs = this[Keys.serviceHeartbeatAtMs] ?: 0L,
        lastMarketDataAtMs = this[Keys.lastMarketDataAtMs] ?: 0L
    )
}
