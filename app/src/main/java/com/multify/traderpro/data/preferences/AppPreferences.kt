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
    val dailyBudgetRupees: Long = 200_000L,
    val fastTrackBudgetRupees: Long = 200_000L,
    val liveExecutionEnabled: Boolean = false,
    val fastTrackEnabled: Boolean = false,
    val postSellShortEnabled: Boolean = true,
    val executionMode: String = "AUTO",
    val waveCount: Int = 1,
    val onboardingComplete: Boolean = false,
    val brokerAuthenticated: Boolean = false,
    val authenticatedAtMs: Long = 0L,
    val accessTokenExpiry: String = "",
    val brokerUcc: String = "",
    val brokerDdpiEnabled: Boolean = false,
    val verifiedPublicIp: String = "",
    val staticIpMatched: Boolean = false,
    val safetyHalt: Boolean = false,
    val liveEnabledDate: String = "",
    val fastTrackEnabledDate: String = "",
    val minLiveConfidenceBps: Long = 7200L,
    val shadowPeakPnlPaise: Long = 0L,
    val shadowPeakPnlDate: String = "",
    val livePeakPnlPaise: Long = 0L,
    val livePeakPnlDate: String = "",
    val waveSpacingBps: Long = 200L,
    val waveCapitalRupees: Long = 5_000L,
    val maxCampaignCapitalRupees: Long = 200_000L,
    val maxDailyLossRupees: Long = 2_500L,
    val maxSingleStockLossRupees: Long = 1_500L
) {
    val minLiveConfidence: Double get() = minLiveConfidenceBps / 10_000.0
    val shadowPeakPnl: Double get() = if (shadowPeakPnlDate == LocalDate.now().toString()) shadowPeakPnlPaise / 100.0 else 0.0
    val livePeakPnl: Double get() = if (livePeakPnlDate == LocalDate.now().toString()) livePeakPnlPaise / 100.0 else 0.0
    val liveExecutionEffective: Boolean get() =
        liveExecutionEnabled && liveEnabledDate == LocalDate.now().toString() && !safetyHalt
    val fastTrackEffective: Boolean get() =
        fastTrackEnabled && fastTrackEnabledDate == LocalDate.now().toString() && !safetyHalt
    val waveSpacingPercent: Double get() = waveSpacingBps / 100.0
    val firstWaveMode: String get() = when (executionMode) {
        "LONG_ONLY" -> "LONG"
        "SHORT_ONLY" -> "SHORT"
        else -> "AUTO"
    }
    val activeWaveCount: Long get() = waveCount.toLong()
}

@Singleton
class AppPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val expectedStaticIp = stringPreferencesKey("expected_static_ip")
        val packageFilter = stringPreferencesKey("package_filter")
        val dailyBudgetRupees = longPreferencesKey("daily_budget_rupees")
        val fastTrackBudgetRupees = longPreferencesKey("fast_track_budget_rupees")
        val liveExecutionEnabled = booleanPreferencesKey("live_execution_enabled")
        val fastTrackEnabled = booleanPreferencesKey("fast_track_enabled")
        val postSellShortEnabled = booleanPreferencesKey("post_sell_short_enabled")
        val executionMode = stringPreferencesKey("execution_mode")
        val waveCount = intPreferencesKey("wave_count")
        val firstWaveMode = stringPreferencesKey("first_wave_mode")
        val activeWaveCount = longPreferencesKey("active_wave_count")
        val onboardingComplete = booleanPreferencesKey("onboarding_complete")
        val brokerAuthenticated = booleanPreferencesKey("broker_authenticated")
        val authenticatedAtMs = longPreferencesKey("authenticated_at_ms")
        val accessTokenExpiry = stringPreferencesKey("access_token_expiry")
        val brokerUcc = stringPreferencesKey("broker_ucc")
        val brokerDdpiEnabled = booleanPreferencesKey("broker_ddpi_enabled")
        val verifiedPublicIp = stringPreferencesKey("verified_public_ip")
        val staticIpMatched = booleanPreferencesKey("static_ip_matched")
        val safetyHalt = booleanPreferencesKey("safety_halt")
        val liveEnabledDate = stringPreferencesKey("live_enabled_date")
        val fastTrackEnabledDate = stringPreferencesKey("fast_track_enabled_date")
        val minLiveConfidenceBps = longPreferencesKey("min_live_confidence_bps")
        val shadowPeakPnlPaise = longPreferencesKey("shadow_peak_pnl_paise")
        val shadowPeakPnlDate = stringPreferencesKey("shadow_peak_pnl_date")
        val livePeakPnlPaise = longPreferencesKey("live_peak_pnl_paise")
        val livePeakPnlDate = stringPreferencesKey("live_peak_pnl_date")
        val waveSpacingBps = longPreferencesKey("wave_spacing_bps")
        val waveCapitalRupees = longPreferencesKey("wave_capital_rupees")
        val maxCampaignCapitalRupees = longPreferencesKey("max_campaign_capital_rupees")
        val maxDailyLossRupees = longPreferencesKey("max_daily_loss_rupees")
        val maxSingleStockLossRupees = longPreferencesKey("max_single_stock_loss_rupees")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p -> p.toSettings() }

    suspend fun updateBrokerSettings(expectedStaticIp: String, packageFilter: String, dailyBudgetRupees: Long) {
        context.dataStore.edit {
            it[Keys.expectedStaticIp] = expectedStaticIp.trim()
            it[Keys.packageFilter] = packageFilter.trim()
            it[Keys.dailyBudgetRupees] = dailyBudgetRupees.coerceIn(10_000L, 200_000L)
            it[Keys.onboardingComplete] = true
        }
    }

    suspend fun setIntradayBudget(value: Long) {
        context.dataStore.edit { it[Keys.dailyBudgetRupees] = value.coerceIn(10_000L, 200_000L) }
    }

    suspend fun setFastTrackBudget(value: Long) {
        context.dataStore.edit { it[Keys.fastTrackBudgetRupees] = value.coerceIn(10_000L, 200_000L) }
    }

    suspend fun setPostSellShortEnabled(value: Boolean) {
        context.dataStore.edit { it[Keys.postSellShortEnabled] = value }
    }

    suspend fun setExecutionMode(value: String) {
        val normalized = value.uppercase().replace(' ', '_').let {
            when (it) {
                "LONG", "LONG_ONLY" -> "LONG_ONLY"
                "SHORT", "SHORT_ONLY" -> "SHORT_ONLY"
                else -> "AUTO"
            }
        }
        context.dataStore.edit { it[Keys.executionMode] = normalized }
    }

    suspend fun setWaveCount(value: Int) {
        context.dataStore.edit { it[Keys.waveCount] = value.coerceIn(1, 20) }
    }

    suspend fun setFirstWaveMode(value: String) = setExecutionMode(value)
    suspend fun setActiveWaveCount(value: Long) = setWaveCount(value.toInt())

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
                it[Keys.liveExecutionEnabled] = false
                it[Keys.fastTrackEnabled] = false
                it[Keys.liveEnabledDate] = ""
                it[Keys.fastTrackEnabledDate] = ""
            }
        }
    }

    suspend fun setLiveExecution(value: Boolean) {
        context.dataStore.edit {
            it[Keys.liveExecutionEnabled] = value
            it[Keys.liveEnabledDate] = if (value) LocalDate.now().toString() else ""
        }
    }

    suspend fun setFastTrack(value: Boolean) {
        context.dataStore.edit {
            it[Keys.fastTrackEnabled] = value
            it[Keys.fastTrackEnabledDate] = if (value) LocalDate.now().toString() else ""
        }
    }

    suspend fun setWaveRiskSettings(
        waveSpacingPercent: Double,
        waveCapitalRupees: Long,
        maximumWaves: Int,
        maxCampaignCapitalRupees: Long,
        maxDailyLossRupees: Long,
        maxSingleStockLossRupees: Long
    ) {
        context.dataStore.edit {
            it[Keys.waveSpacingBps] = (waveSpacingPercent.coerceIn(0.5, 10.0) * 100.0).toLong()
            it[Keys.waveCapitalRupees] = waveCapitalRupees.coerceIn(1_000L, 50_000L)
            it[Keys.waveCount] = maximumWaves.coerceIn(1, 20)
            it[Keys.maxCampaignCapitalRupees] = maxCampaignCapitalRupees.coerceIn(10_000L, 500_000L)
            it[Keys.maxDailyLossRupees] = maxDailyLossRupees.coerceIn(500L, 100_000L)
            it[Keys.maxSingleStockLossRupees] = maxSingleStockLossRupees.coerceIn(250L, 50_000L)
        }
    }

    suspend fun updateShadowPeakPnl(value: Double) {
        val today = LocalDate.now().toString()
        context.dataStore.edit {
            val current = if (it[Keys.shadowPeakPnlDate] == today) (it[Keys.shadowPeakPnlPaise] ?: 0L) / 100.0 else 0.0
            if (value > current) {
                it[Keys.shadowPeakPnlDate] = today
                it[Keys.shadowPeakPnlPaise] = (value * 100.0).toLong()
            }
        }
    }

    suspend fun updateLivePeakPnl(value: Double) {
        val today = LocalDate.now().toString()
        context.dataStore.edit {
            val current = if (it[Keys.livePeakPnlDate] == today) (it[Keys.livePeakPnlPaise] ?: 0L) / 100.0 else 0.0
            if (value > current) {
                it[Keys.livePeakPnlDate] = today
                it[Keys.livePeakPnlPaise] = (value * 100.0).toLong()
            }
        }
    }

    suspend fun setSafetyHalt(value: Boolean) {
        context.dataStore.edit {
            it[Keys.safetyHalt] = value
            if (value) {
                it[Keys.liveExecutionEnabled] = false
                it[Keys.fastTrackEnabled] = false
                it[Keys.liveEnabledDate] = ""
                it[Keys.fastTrackEnabledDate] = ""
            }
        }
    }

    suspend fun setMinLiveConfidence(value: Double) {
        context.dataStore.edit { it[Keys.minLiveConfidenceBps] = (value.coerceIn(0.50, 0.95) * 10_000).toLong() }
    }

    private fun Preferences.toSettings() = AppSettings(
        expectedStaticIp = this[Keys.expectedStaticIp].orEmpty(),
        packageFilter = this[Keys.packageFilter].orEmpty(),
        dailyBudgetRupees = (this[Keys.dailyBudgetRupees] ?: 200_000L).coerceIn(10_000L, 200_000L),
        fastTrackBudgetRupees = (this[Keys.fastTrackBudgetRupees] ?: 200_000L).coerceIn(10_000L, 200_000L),
        liveExecutionEnabled = this[Keys.liveExecutionEnabled] ?: false,
        fastTrackEnabled = this[Keys.fastTrackEnabled] ?: false,
        postSellShortEnabled = this[Keys.postSellShortEnabled] ?: true,
        executionMode = this[Keys.executionMode].orEmpty().ifBlank {
            when (this[Keys.firstWaveMode].orEmpty().uppercase()) {
                "LONG" -> "LONG_ONLY"
                "SHORT" -> "SHORT_ONLY"
                else -> "AUTO"
            }
        }.uppercase().replace(' ', '_').let { if (it in setOf("AUTO", "LONG_ONLY", "SHORT_ONLY")) it else "AUTO" },
        waveCount = (this[Keys.waveCount] ?: (this[Keys.activeWaveCount] ?: 1L).toInt()).coerceIn(1, 20),
        onboardingComplete = this[Keys.onboardingComplete] ?: false,
        brokerAuthenticated = this[Keys.brokerAuthenticated] ?: false,
        authenticatedAtMs = this[Keys.authenticatedAtMs] ?: 0L,
        accessTokenExpiry = this[Keys.accessTokenExpiry].orEmpty(),
        brokerUcc = this[Keys.brokerUcc].orEmpty(),
        brokerDdpiEnabled = this[Keys.brokerDdpiEnabled] ?: false,
        verifiedPublicIp = this[Keys.verifiedPublicIp].orEmpty(),
        staticIpMatched = this[Keys.staticIpMatched] ?: false,
        safetyHalt = this[Keys.safetyHalt] ?: false,
        liveEnabledDate = this[Keys.liveEnabledDate].orEmpty(),
        fastTrackEnabledDate = this[Keys.fastTrackEnabledDate].orEmpty(),
        minLiveConfidenceBps = this[Keys.minLiveConfidenceBps] ?: 7200L,
        shadowPeakPnlPaise = this[Keys.shadowPeakPnlPaise] ?: 0L,
        shadowPeakPnlDate = this[Keys.shadowPeakPnlDate].orEmpty(),
        livePeakPnlPaise = this[Keys.livePeakPnlPaise] ?: 0L,
        livePeakPnlDate = this[Keys.livePeakPnlDate].orEmpty(),
        waveSpacingBps = (this[Keys.waveSpacingBps] ?: 200L).coerceIn(50L, 1000L),
        waveCapitalRupees = (this[Keys.waveCapitalRupees] ?: 5_000L).coerceIn(1_000L, 50_000L),
        maxCampaignCapitalRupees = (this[Keys.maxCampaignCapitalRupees] ?: 200_000L).coerceIn(10_000L, 500_000L),
        maxDailyLossRupees = (this[Keys.maxDailyLossRupees] ?: 2_500L).coerceIn(500L, 100_000L),
        maxSingleStockLossRupees = (this[Keys.maxSingleStockLossRupees] ?: 1_500L).coerceIn(250L, 50_000L)
    )
}