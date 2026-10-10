package com.multify.traderpro.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "execution_samples",
    indices = [
        Index(value = ["transactionType", "filledAtMs"]),
        Index(value = ["filledAtMs"])
    ]
)
data class ExecutionSampleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val transactionType: String,
    val symbol: String,
    val quantity: Int,
    val product: String,
    val orderId: String,
    val submittedAtMs: Long,
    val filledAtMs: Long,
    val totalExecutionMs: Long,
    val appDispatchMicros: Long,
    val brokerAckMs: Long
)
