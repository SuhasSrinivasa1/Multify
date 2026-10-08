package com.multify.traderpro.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SignalEventDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(event: SignalEventEntity): Long

    @Query("SELECT * FROM signal_events WHERE id = :id LIMIT 1")
    suspend fun byId(id: Long): SignalEventEntity?

    @Query("SELECT * FROM signal_events ORDER BY id DESC LIMIT :limit")
    fun observeRecent(limit: Int = 100): Flow<List<SignalEventEntity>>

    @Query("SELECT * FROM signal_events ORDER BY id DESC LIMIT :limit")
    suspend fun recentNow(limit: Int = 20): List<SignalEventEntity>

    @Query("SELECT * FROM signal_events ORDER BY id ASC")
    suspend fun allNow(): List<SignalEventEntity>

    @Query("UPDATE signal_events SET forwardingState=:state, backendAction=:action, backendReason=:reason, lastError=:error WHERE id=:id")
    suspend fun updateForwarding(id: Long, state: String, action: String?, reason: String?, error: String?)

    @Query("DELETE FROM signal_events WHERE receivedAtMs < :beforeMs")
    suspend fun deleteOlderThan(beforeMs: Long)
}
