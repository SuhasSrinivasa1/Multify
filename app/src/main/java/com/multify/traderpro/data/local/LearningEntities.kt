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
    val multifyMaxPrice: Double? = null,
    val multifyMfePct: Double? = null,
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
        Index(value = ["forecastDate", "symbol"]),
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
    val multifyMatched: Boolean = false,
    val qualityTier: String = "LEGACY",
    val executionEligible: Boolean = false
)

@Entity(
    tableName = "forecast_research_snapshots",
    indices = [
        Index(value = ["researchDate", "scanAtMs"]),
        Index(value = ["researchDate", "symbol"]),
        Index(value = ["qualityTier"])
    ]
)
data class ForecastResearchEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val researchDate: String,
    val scanAtMs: Long,
    val symbol: String,
    val rank: Int,
    val qualityTier: String,
    val publishEligible: Boolean,
    val executionEligible: Boolean,
    val targetPct: Double,
    val entryPrice: Double,
    val regime: String,
    val marketRegime: String,
    val strategy: String,
    val directionalScore: Double,
    val confidence: Double,
    val rejectionReason: String,
    val featureJson: String,
    val voteJson: String,
    val multifyMatched: Boolean = false,
    val multifyEventId: Long? = null,
    val multifyAtMs: Long? = null,
    val outcomeMfePct: Double? = null,
    val outcomeMaePct: Double? = null,
    val outcomeClosePct: Double? = null,
    val outcomeTargetHit: Boolean? = null,
    val outcomeUpdatedAtMs: Long? = null
)


@Entity(
    tableName = "forecast_positive_evidence",
    indices = [
        Index(value = ["source", "evidenceDate"]),
        Index(value = ["marketRegime", "regime", "strategy"]),
        Index(value = ["capBucket", "dominantFamily"]),
        Index(value = ["symbol", "evidenceDate"])
    ]
)
data class ForecastPositiveEvidenceEntity(
    @PrimaryKey val evidenceKey: String,
    val source: String,
    val evidenceDate: String,
    val eventAtMs: Long,
    val symbol: String,
    val strategy: String,
    val regime: String,
    val marketRegime: String,
    val sectorRegime: String = "UNAVAILABLE",
    val capBucket: String = "CAP_UNKNOWN",
    val timeBucket: String = "UNKNOWN",
    val dominantFamily: String = "UNKNOWN",
    val familyConsensus: Double = 0.0,
    val momentumFamilyScore: Double = 0.0,
    val participationFamilyScore: Double = 0.0,
    val structureFamilyScore: Double = 0.0,
    val factorJson: String = "{}",
    val voteJson: String = "[]",
    val entryPrice: Double,
    val targetPct: Double? = null,
    val maxFavourablePct: Double = 0.0,
    val maxAdversePct: Double = 0.0,
    val closePct: Double? = null,
    val realizedPct: Double? = null,
    val protectedTrailPct: Double? = null,
    val trailArmed: Boolean = false,
    val targetHit: Boolean = false,
    val positiveReason: String,
    val qualityTier: String = "EVIDENCE",
    val reconstructionQuality: String = "LIVE_SNAPSHOT",
    val updatedAtMs: Long
)

@Entity(
    tableName = "forecast_champions",
    indices = [Index(value = ["marketRegime", "regime", "strategy"], unique = true)]
)
data class ForecastChampionEntity(
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
