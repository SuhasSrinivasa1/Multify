package com.multify.traderpro.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "managed_positions",
    indices = [
        Index(value = ["engine", "symbol", "status"]),
        Index(value = ["product", "symbol", "status"]),
        Index(value = ["openedAtMs"])
    ]
)
data class ManagedPositionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val engine: String,
    val symbol: String,
    val product: String,
    val side: String,
    val quantity: Int,
    val entryPrice: Double,
    val stopPrice: Double?,
    val targetPrice: Double?,
    val strategy: String,
    val regime: String,
    val confidence: Double,
    val sourceEventId: Long?,
    val openOrderId: String,
    val openReferenceId: String,
    val smartOrderId: String? = null,
    val openedAtMs: Long,
    val lastPrice: Double,
    val maxFavourablePrice: Double,
    val maxAdversePrice: Double,
    val lastEvaluatedAtMs: Long,
    val anchorPrice: Double = 0.0,
    val capitalDeployed: Double = 0.0,
    val campaignBudget: Double = 0.0,
    val addCount: Int = 0,
    val trailingArmPct: Double = 0.0,
    val trailingLastRatchetPrice: Double = 0.0,
    val status: String = "OPEN"
)
