package com.multify.traderpro.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "managed_trades",
    indices = [
        Index(value = ["engine", "closedAtMs"]),
        Index(value = ["symbol", "closedAtMs"]),
        Index(value = ["closedAtMs"])
    ]
)
data class ManagedTradeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val engine: String,
    val symbol: String,
    val product: String,
    val side: String,
    val quantity: Int,
    val entryPrice: Double,
    val exitPrice: Double,
    val grossPnl: Double,
    val estimatedCosts: Double,
    val netPnl: Double,
    val exitReason: String,
    val strategy: String,
    val regime: String,
    val confidence: Double,
    val mfeRupees: Double,
    val maeRupees: Double,
    val sourceEventId: Long?,
    val openOrderId: String,
    val closeOrderId: String,
    val openedAtMs: Long,
    val closedAtMs: Long
)
