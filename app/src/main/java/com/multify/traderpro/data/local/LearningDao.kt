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

    @Query("SELECT * FROM learning_calls WHERE callDate=:callDate AND symbol=:symbol AND action=:action ORDER BY CASE WHEN source='LIVE' THEN 0 ELSE 1 END, id ASC LIMIT 1")
    suspend fun callForDateSymbolAction(callDate: String, symbol: String, action: String): LearningCallEntity?

    @Query("SELECT * FROM learning_calls WHERE source='LIVE' AND symbol=:symbol ORDER BY id DESC LIMIT 1")
    suspend fun latestLiveCall(symbol: String): LearningCallEntity?

    @Query("SELECT * FROM learning_calls WHERE source='LIVE' AND buyEventId=:eventId LIMIT 1")
    suspend fun liveCallForBuyEvent(eventId: Long): LearningCallEntity?

    @Query("SELECT * FROM learning_calls WHERE source='LIVE' AND symbol=:symbol AND buyAtMs <= :atMs ORDER BY buyAtMs DESC, id DESC LIMIT 1")
    suspend fun latestLiveCallBefore(symbol: String, atMs: Long): LearningCallEntity?

    @Query("SELECT * FROM learning_calls WHERE action='BUY' AND multifyMfePct IS NULL AND buyAtMs > 0 AND sellAtMs IS NOT NULL AND sellAtMs > buyAtMs ORDER BY callDate DESC, buyAtMs DESC LIMIT :limit")
    suspend fun closedCallsMissingMfe(limit: Int): List<LearningCallEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertObservation(item: PriceObservationEntity): Long

    @Query("SELECT * FROM price_observations ORDER BY observedAtMs ASC")
    suspend fun allObservations(): List<PriceObservationEntity>

    @Query("SELECT * FROM price_observations WHERE eventId=:eventId AND phase=:phase ORDER BY offsetSeconds ASC")
    suspend fun observationsForEventPhase(eventId: Long, phase: String): List<PriceObservationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLongForecast(item: LongForecastEntity): Long

    @Update
    suspend fun updateLongForecast(item: LongForecastEntity)

    @Query("SELECT * FROM long_forecasts WHERE forecastDate=:date ORDER BY rank ASC")
    suspend fun longForecastsForDate(date: String): List<LongForecastEntity>

    @Query("SELECT * FROM long_forecasts WHERE status='ACTIVE' ORDER BY generatedAtMs ASC")
    suspend fun activeLongForecasts(): List<LongForecastEntity>

    @Query("SELECT * FROM long_forecasts ORDER BY generatedAtMs ASC")
    suspend fun allLongForecasts(): List<LongForecastEntity>

    @Query("SELECT * FROM long_forecasts WHERE notified=0 AND forecastDate=:date ORDER BY generatedAtMs ASC")
    suspend fun pendingLongForecastNotifications(date: String): List<LongForecastEntity>

    @Query("UPDATE long_forecasts SET notified=1 WHERE id=:id")
    suspend fun markLongForecastNotified(id: Long)

    @Query("UPDATE long_forecasts SET multifyMatched=1 WHERE forecastDate=:date AND symbol=:symbol")
    suspend fun markLongForecastMatch(date: String, symbol: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertForecastResearch(item: ForecastResearchEntity): Long

    @Update
    suspend fun updateForecastResearch(item: ForecastResearchEntity)

    @Query("SELECT * FROM forecast_research_snapshots WHERE researchDate=:date ORDER BY scanAtMs ASC, rank ASC")
    suspend fun forecastResearchForDate(date: String): List<ForecastResearchEntity>

    @Query("SELECT * FROM forecast_research_snapshots ORDER BY scanAtMs ASC, rank ASC")
    suspend fun allForecastResearch(): List<ForecastResearchEntity>

    @Query("SELECT DISTINCT researchDate FROM forecast_research_snapshots ORDER BY researchDate DESC")
    suspend fun forecastResearchDates(): List<String>

    @Query("SELECT DISTINCT researchDate FROM forecast_research_snapshots WHERE researchCohort=:cohort ORDER BY researchDate DESC")
    suspend fun forecastResearchDatesForCohort(cohort: String): List<String>

    @Query("SELECT * FROM forecast_research_snapshots WHERE researchCohort=:cohort ORDER BY scanAtMs ASC, rank ASC")
    suspend fun allForecastResearchForCohort(cohort: String): List<ForecastResearchEntity>

    @Query("SELECT * FROM forecast_research_snapshots WHERE researchCohort=:cohort AND researchDate=:date ORDER BY scanAtMs ASC, rank ASC")
    suspend fun forecastResearchForDateAndCohort(date: String, cohort: String): List<ForecastResearchEntity>

    @Query("SELECT * FROM forecast_research_snapshots WHERE researchDate=:date AND outcomeUpdatedAtMs IS NULL ORDER BY scanAtMs ASC, rank ASC")
    suspend fun pendingForecastResearchOutcomes(date: String): List<ForecastResearchEntity>

    @Query("SELECT * FROM forecast_research_snapshots WHERE researchCohort=:cohort AND researchDate=:date AND outcomeUpdatedAtMs IS NULL ORDER BY scanAtMs ASC, rank ASC")
    suspend fun pendingForecastResearchOutcomesForCohort(date: String, cohort: String): List<ForecastResearchEntity>

    @Query("UPDATE forecast_research_snapshots SET multifyMatched=1, multifyEventId=:eventId, multifyAtMs=:atMs WHERE researchDate=:date AND symbol=:symbol AND scanAtMs < :atMs")
    suspend fun markForecastResearchMultifyMatch(date: String, symbol: String, eventId: Long, atMs: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertForecastChampion(item: ForecastChampionEntity)

    @Query("SELECT * FROM forecast_champions ORDER BY frozen DESC, wins DESC, sampleCount DESC")
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
