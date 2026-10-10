package com.multify.traderpro.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface LearningDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCall(call: LearningCallEntity): Long

    @Update
    suspend fun updateCall(call: LearningCallEntity)

    @Query("SELECT * FROM learning_calls ORDER BY callDate ASC, id ASC")
    suspend fun allCalls(): List<LearningCallEntity>

    @Query("SELECT COUNT(*) FROM learning_calls")
    suspend fun callCount(): Int

    @Query("SELECT * FROM learning_calls WHERE source!='LIVE' AND callDate=:callDate AND symbol=:symbol AND action=:action ORDER BY id ASC LIMIT 1")
    suspend fun historicalCallForKey(callDate: String, symbol: String, action: String): LearningCallEntity?

    @Query("SELECT * FROM learning_calls WHERE source='LIVE' AND symbol=:symbol ORDER BY id DESC LIMIT 1")
    suspend fun latestLiveCall(symbol: String): LearningCallEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertObservation(item: PriceObservationEntity): Long

    @Query("SELECT * FROM price_observations ORDER BY observedAtMs ASC")
    suspend fun allObservations(): List<PriceObservationEntity>

    @Query("SELECT * FROM price_observations WHERE eventId=:eventId AND phase=:phase ORDER BY offsetSeconds ASC")
    suspend fun observationsForEventPhase(eventId: Long, phase: String): List<PriceObservationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertIntradayForecast(item: IntradayForecastEntity): Long

    @Update
    suspend fun updateIntradayForecast(item: IntradayForecastEntity)

    @Query("SELECT * FROM intraday_forecasts WHERE forecastDate=:date ORDER BY side ASC, rank ASC")
    suspend fun intradayForecastsForDate(date: String): List<IntradayForecastEntity>

    @Query("SELECT * FROM intraday_forecasts WHERE forecastDate=:date AND side=:side ORDER BY rank ASC")
    suspend fun intradayForecastsForSide(date: String, side: String): List<IntradayForecastEntity>

    @Query("SELECT * FROM intraday_forecasts WHERE status='ACTIVE' ORDER BY generatedAtMs ASC")
    suspend fun activeIntradayForecasts(): List<IntradayForecastEntity>

    @Query("SELECT * FROM intraday_forecasts ORDER BY generatedAtMs ASC")
    suspend fun allIntradayForecasts(): List<IntradayForecastEntity>

    @Query("SELECT * FROM intraday_forecasts WHERE notified=0 AND forecastDate=:date ORDER BY generatedAtMs ASC")
    suspend fun pendingForecastNotifications(date: String): List<IntradayForecastEntity>

    @Query("UPDATE intraday_forecasts SET notified=1 WHERE id=:id")
    suspend fun markForecastNotified(id: Long)

    @Query("UPDATE intraday_forecasts SET multifyMatched=1 WHERE forecastDate=:date AND symbol=:symbol")
    suspend fun markIntradayForecastMatch(date: String, symbol: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertForecastChampion(item: ForecastChampionEntity)

    @Query("SELECT * FROM forecast_champions ORDER BY frozen DESC, side ASC, wins DESC, sampleCount DESC")
    suspend fun allForecastChampions(): List<ForecastChampionEntity>

    @Query("SELECT * FROM forecast_champions WHERE key=:key LIMIT 1")
    suspend fun forecastChampion(key: String): ForecastChampionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertResearchReport(item: ResearchReportEntity): Long

    @Query("SELECT * FROM research_reports ORDER BY reportDate DESC LIMIT 1")
    suspend fun latestResearchReport(): ResearchReportEntity?

    @Query("SELECT * FROM research_reports ORDER BY reportDate DESC")
    suspend fun allResearchReports(): List<ResearchReportEntity>
}
