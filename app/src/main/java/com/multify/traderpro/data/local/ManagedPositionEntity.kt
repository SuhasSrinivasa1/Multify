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
    val trailingArmPct: Double = 0.0,
    val trailingLastRatchetPrice: Double = 0.0,
    val status: String = "OPEN",
    val smartOrderMode: String = "NONE",
    val smartOrderTriggerPrice: Double? = null,
    val smartOrderDirection: String? = null,
    val smartOrderStatus: String? = null,
    val smartOrderUpdatedAtMs: Long? = null,
    val smartOrderReferenceId: String? = null,
    val smartOrderTriggeredAtMs: Long? = null,
    val smartOrderChildOrderId: String? = null,
    val smartOrderChildStatus: String? = null,
    val netProfitFloorPrice: Double? = null,
    val netProfitTargetPct: Double? = null
)
