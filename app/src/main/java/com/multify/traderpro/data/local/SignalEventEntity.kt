package com.multify.traderpro.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "signal_events",
    indices = [Index(value = ["fingerprint"], unique = true), Index(value = ["receivedAtMs"])]
)
data class SignalEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fingerprint: String,
    val receivedAtMs: Long,
    val postedAtMs: Long,
    val sourcePackage: String,
    val appLabel: String,
    val title: String,
    val text: String,
    val bigText: String,
    val signalType: String,
    val symbol: String?,
    val summary: String,
    val confidence: Double,
    val forwardingState: String = "CAPTURED",
    val backendAction: String? = null,
    val backendReason: String? = null,
    val lastError: String? = null
)
