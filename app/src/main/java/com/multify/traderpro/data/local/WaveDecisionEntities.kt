package com.multify.traderpro.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "wave_decisions",
    indices = [
        Index(value = ["campaignId", "waveNumber"], unique = true),
        Index(value = ["symbol", "triggerAtMs"]),
        Index(value = ["callDate"])
    ]
)
data class WaveDecisionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val campaignId: Long,
    val eventId: Long? = null,
    val symbol: String,
    val callDate: String,
    val waveNumber: Int,
    val triggerAtMs: Long,
    val snapshotAtMs: Long,
    val triggerPrice: Double,
    val triggerPct: Double,
    val currentSide: String,
    val selectedDirection: String,
    val longScore: Double,
    val shortScore: Double,
    val longProbability: Double,
    val shortProbability: Double,
    val evLongRupees: Double,
    val evShortRupees: Double,
    val evLongPct: Double,
    val evShortPct: Double,
    val confidence: Double,
    val regime: String,
    val topPositiveFeatures: String,
    val topNegativeFeatures: String,
    val rejectionReason: String,
    val snapshotJson: String,
    val modelVersion: String,
    val dataAgeMs: Long,
    val brokerHealthy: Boolean,
    val listenerHealthy: Boolean,
    val waveCapitalRupees: Double,
    val campaignCapitalBefore: Double,
    val campaignCapitalAfter: Double,
    val actualOrderSubmitted: Boolean,
    val orderReference: String? = null,
    val createdAtMs: Long
)

@Entity(
    tableName = "wave_outcomes",
    indices = [Index(value = ["decisionId"], unique = true), Index(value = ["symbol", "lastObservedAtMs"])]
)
data class WaveOutcomeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val decisionId: Long,
    val symbol: String,
    val entryPrice: Double,
    val longMfeRupees: Double = 0.0,
    val longMaeRupees: Double = 0.0,
    val shortMfeRupees: Double = 0.0,
    val shortMaeRupees: Double = 0.0,
    val longNetRupees: Double = 0.0,
    val shortNetRupees: Double = 0.0,
    val selectedNetRupees: Double = 0.0,
    val oldAveragingNetRupees: Double = 0.0,
    val noTradeAvoidanceRupees: Double = 0.0,
    val bestRealisticNetRupees: Double = 0.0,
    val lastPrice: Double,
    val lastObservedAtMs: Long,
    val finalizedAtMs: Long? = null
)
