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
    val minPostSellPrice: Double? = null,
    val postSellRetracementFraction: Double? = null,
    val shortObservationFinalized: Boolean = false,
    val updatedAtMs: Long = 0L
)

@Entity(
    tableName = "price_observations",
    indices = [Index(value = ["eventId", "phase", "offsetSeconds"], unique = true), Index(value = ["symbol", "observedAtMs"])]
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
    tableName = "strategy_snapshots",
    indices = [Index(value = ["symbol", "atMs"]), Index(value = ["side", "atMs"])]
)
data class StrategySnapshotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val atMs: Long,
    val eventId: Long?,
    val symbol: String,
    val side: String,
    val regime: String,
    val strategy: String,
    val directionalScore: Double,
    val confidence: Double,
    val ltp: Double,
    val vwap: Double?,
    val ema9: Double?,
    val ema20: Double?,
    val atr14: Double?,
    val rsi14: Double?,
    val rvol: Double?,
    val macdHistogram: Double?,
    val spreadBps: Double?,
    val orderBookImbalance: Double?,
    val dayChangePct: Double?,
    val marketCap: Double?,
    val votes: String
)

@Entity(
    tableName = "forecasts",
    indices = [Index(value = ["forecastDate", "rank"], unique = true), Index(value = ["symbol", "forecastDate"])]
)
data class ForecastEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val forecastDate: String,
    val rank: Int,
    val symbol: String,
    val bias: String,
    val confidence: Double,
    val score: Double,
    val reason: String,
    val generatedAtMs: Long,
    val multifyMatched: Boolean = false,
    val multifyDirectionMatched: Boolean = false
)


@Entity(
    tableName = "intraday_forecasts",
    indices = [
        Index(value = ["forecastDate", "side", "rank"], unique = true),
        Index(value = ["forecastDate", "side", "symbol"], unique = true),
        Index(value = ["status", "forecastDate"])
    ]
)
data class IntradayForecastEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val forecastDate: String,
    val side: String,
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
    tableName = "forecast_champions",
    indices = [Index(value = ["side", "marketRegime", "regime", "strategy"], unique = true)]
)
data class ForecastChampionEntity(
    @PrimaryKey val key: String,
    val side: String,
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
