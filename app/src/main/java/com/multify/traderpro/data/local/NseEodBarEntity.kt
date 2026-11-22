package com.multify.traderpro.data.local

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "nse_eod_bars",
    primaryKeys = ["symbol", "tradeDate"],
    indices = [Index(value = ["tradeDate"]), Index(value = ["symbol"])]
)
data class NseEodBarEntity(
    val symbol: String,
    val tradeDate: String,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Long
)
