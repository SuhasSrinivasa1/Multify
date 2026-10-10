package com.multify.traderpro.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "learning_calls",
    indices = [
        Index(value = ["callDate"]),
        Index(value = ["symbol", "callDate"]),
        Index(value = ["source"])
    ]
)
data class LearningCallEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val callDate: String,
    val symbol: String,
    val source: String,
    val action: String = "BUY",
    val entryPrice: Double,
    val targetPrice: Double? = null,
    val targetPct: Double? = null,
    val multifyExitPrice: Double? = null,
    val longRealizedPct: Double? = null,
    val buyEventId: Long? = null,
    val sellEventId: Long? = null,
    val buyAtMs: Long = 0L,
    val sellAtMs: Long? = null,
    val updatedAtMs: Long = 0L
)

@Entity(
    tableName = "price_observations",
    indices = [
        Index(value = ["eventId", "phase", "offsetSeconds"], unique = true),
        Index(value = ["symbol", "observedAtMs"])
    ]
)
data class PriceObservationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val eventId: Long,
    val symbol: String,
    val phase: String,
    val offsetSeconds: Int,
    val observedAtMs: Long,
    val price: Double
)

@Entity(
    tableName = "long_forecasts",
    indices = [
        Index(value = ["forecastDate", "rank"], unique = true),
        Index(value = ["forecastDate", "symbol"], unique = true),
        Index(value = ["status", "forecastDate"])
    ]
)
data class LongForecastEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val forecastDate: String,
    val rank: Int,
    val symbol: String,
    val entryPrice: Double,
    val targetPct: Double,
    val targetPrice: Double,
    val confidence: Double,
    val score: Double,
    val strategy: String,
    val regime: String,
    val marketRegime: String = "UNKNOWN",
    val sectorRegime: String = "UNAVAILABLE",
    val championKey: String = "",
    val reason: String,
    val generatedAtMs: Long,
    val status: String = "ACTIVE",
    val targetHitAtMs: Long? = null,
    val lastPrice: Double,
    val maxFavourablePct: Double = 0.0,
    val maxAdversePct: Double = 0.0,
    val lastObservedAtMs: Long,
    val notified: Boolean = false,
    val multifyMatched: Boolean = false
)

@Entity(
    tableName = "long_champions",
    indices = [Index(value = ["marketRegime", "regime", "strategy"], unique = true)]
)
data class LongChampionEntity(
    @PrimaryKey val key: String,
    val marketRegime: String,
    val sectorRegime: String = "UNAVAILABLE",
    val regime: String,
    val strategy: String,
    val wins: Int = 0,
    val losses: Int = 0,
    val sampleCount: Int = 0,
    val distinctDays: Int = 0,
    val distinctSymbols: Int = 0,
    val frozen: Boolean = false,
    val firstSeenAtMs: Long,
    val lastUpdatedAtMs: Long
)

@Entity(
    tableName = "research_reports",
    indices = [Index(value = ["reportDate"], unique = true)]
)
data class ResearchReportEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val reportDate: String,
    val generatedAtMs: Long,
    val title: String,
    val summary: String
)
