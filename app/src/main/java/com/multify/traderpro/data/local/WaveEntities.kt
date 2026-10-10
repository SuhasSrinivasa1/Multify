package com.multify.traderpro.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "wave_campaigns",
    indices = [
        Index(value = ["eventId"], unique = true),
        Index(value = ["symbol", "active"]),
        Index(value = ["callDate"])
    ]
)
data class WaveCampaignEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val eventId: Long,
    val symbol: String,
    val callDate: String,
    val startPrice: Double,
    val startAtMs: Long,
    val startSource: String,
    val wave: Int = 1,
    val leg: String = "UP",
    val legStartPrice: Double,
    val legStartAtMs: Long,
    val extremePrice: Double,
    val extremeAtMs: Long,
    val legArmed: Boolean = false,
    val initialAdversePrice: Double,
    val lastPrice: Double,
    val active: Boolean = true,
    val completedAtMs: Long? = null,
    val updatedAtMs: Long
)

@Entity(
    tableName = "wave_observations",
    indices = [
        Index(value = ["campaignId", "wave", "direction"], unique = true),
        Index(value = ["callDate", "wave", "direction"]),
        Index(value = ["symbol", "confirmedAtMs"])
    ]
)
data class WaveObservationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val campaignId: Long,
    val eventId: Long,
    val symbol: String,
    val callDate: String,
    val wave: Int,
    val direction: String,
    val startPrice: Double,
    val extremePrice: Double,
    val confirmationPrice: Double,
    val startAtMs: Long,
    val extremeAtMs: Long,
    val confirmedAtMs: Long,
    val movePct: Double,
    val reversalPct: Double,
    val source: String = "LIVE_PIVOT"
)
