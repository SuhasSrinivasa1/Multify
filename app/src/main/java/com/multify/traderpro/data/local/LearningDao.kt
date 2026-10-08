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

    @Query("SELECT * FROM learning_calls WHERE source='LIVE' AND sellAtMs IS NOT NULL AND shortObservationFinalized=0 ORDER BY sellAtMs ASC")
    suspend fun activePostSellObservations(): List<LearningCallEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertObservation(item: PriceObservationEntity): Long

    @Query("SELECT * FROM price_observations ORDER BY observedAtMs ASC")
    suspend fun allObservations(): List<PriceObservationEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertStrategySnapshot(item: StrategySnapshotEntity): Long

    @Query("SELECT * FROM strategy_snapshots ORDER BY atMs DESC LIMIT :limit")
    suspend fun recentStrategySnapshots(limit: Int = 500): List<StrategySnapshotEntity>

    @Query("SELECT * FROM strategy_snapshots ORDER BY atMs ASC")
    suspend fun allStrategySnapshots(): List<StrategySnapshotEntity>

    @Query("DELETE FROM forecasts WHERE forecastDate=:date")
    suspend fun deleteForecasts(date: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertForecasts(items: List<ForecastEntity>)

    @Query("SELECT * FROM forecasts WHERE forecastDate=:date ORDER BY rank ASC")
    suspend fun forecastsForDate(date: String): List<ForecastEntity>

    @Query("SELECT * FROM forecasts ORDER BY forecastDate ASC, rank ASC")
    suspend fun allForecasts(): List<ForecastEntity>

    @Query("UPDATE forecasts SET multifyMatched=1, multifyDirectionMatched=CASE WHEN bias=:bias THEN 1 ELSE multifyDirectionMatched END WHERE forecastDate=:date AND symbol=:symbol")
    suspend fun markForecastMatch(date: String, symbol: String, bias: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertResearchReport(item: ResearchReportEntity): Long

    @Query("SELECT * FROM research_reports ORDER BY reportDate DESC LIMIT 1")
    suspend fun latestResearchReport(): ResearchReportEntity?

    @Query("SELECT * FROM research_reports ORDER BY reportDate DESC")
    suspend fun allResearchReports(): List<ResearchReportEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertWaveCampaign(item: WaveCampaignEntity): Long

    @Update
    suspend fun updateWaveCampaign(item: WaveCampaignEntity)

    @Query("SELECT * FROM wave_campaigns WHERE eventId=:eventId LIMIT 1")
    suspend fun waveCampaignForEvent(eventId: Long): WaveCampaignEntity?

    @Query("SELECT * FROM wave_campaigns WHERE active=1 ORDER BY startAtMs ASC")
    suspend fun activeWaveCampaigns(): List<WaveCampaignEntity>

    @Query("SELECT * FROM wave_campaigns ORDER BY startAtMs ASC")
    suspend fun allWaveCampaigns(): List<WaveCampaignEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertWaveObservation(item: WaveObservationEntity): Long

    @Query("SELECT * FROM wave_observations WHERE campaignId=:campaignId ORDER BY wave ASC, confirmedAtMs ASC")
    suspend fun waveObservationsForCampaign(campaignId: Long): List<WaveObservationEntity>

    @Query("SELECT * FROM wave_observations ORDER BY confirmedAtMs ASC")
    suspend fun allWaveObservations(): List<WaveObservationEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertWaveDecision(item: WaveDecisionEntity): Long

    @Query("SELECT * FROM wave_decisions WHERE campaignId=:campaignId AND waveNumber=:waveNumber LIMIT 1")
    suspend fun waveDecisionForCampaign(campaignId: Long, waveNumber: Int): WaveDecisionEntity?

    @Query("SELECT * FROM wave_decisions ORDER BY triggerAtMs DESC LIMIT :limit")
    suspend fun recentWaveDecisions(limit: Int = 50): List<WaveDecisionEntity>

    @Query("SELECT * FROM wave_decisions WHERE campaignId=:campaignId ORDER BY waveNumber ASC")
    suspend fun waveDecisionsForCampaign(campaignId: Long): List<WaveDecisionEntity>

    @Query("SELECT * FROM wave_decisions WHERE id=:id LIMIT 1")
    suspend fun waveDecisionById(id: Long): WaveDecisionEntity?

    @Query("SELECT * FROM wave_decisions ORDER BY triggerAtMs ASC")
    suspend fun allWaveDecisions(): List<WaveDecisionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWaveOutcome(item: WaveOutcomeEntity): Long

    @Update
    suspend fun updateWaveOutcome(item: WaveOutcomeEntity)

    @Query("SELECT * FROM wave_outcomes WHERE decisionId=:decisionId LIMIT 1")
    suspend fun waveOutcomeForDecision(decisionId: Long): WaveOutcomeEntity?

    @Query("SELECT * FROM wave_outcomes WHERE finalizedAtMs IS NULL ORDER BY lastObservedAtMs ASC")
    suspend fun activeWaveOutcomes(): List<WaveOutcomeEntity>

    @Query("SELECT * FROM wave_outcomes ORDER BY lastObservedAtMs ASC")
    suspend fun allWaveOutcomes(): List<WaveOutcomeEntity>
}
