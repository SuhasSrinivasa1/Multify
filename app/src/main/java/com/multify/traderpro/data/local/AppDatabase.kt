package com.multify.traderpro.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        SignalEventEntity::class,
        ShadowPositionEntity::class,
        ShadowTradeEntity::class,
        ManagedPositionEntity::class,
        ManagedTradeEntity::class,
        LearningCallEntity::class,
        PriceObservationEntity::class,
        StrategySnapshotEntity::class,
        ForecastEntity::class,
        ResearchReportEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun signalEventDao(): SignalEventDao
    abstract fun shadowDao(): ShadowDao
    abstract fun managedTradeDao(): ManagedTradeDao
    abstract fun learningDao(): LearningDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""CREATE TABLE IF NOT EXISTS shadow_positions (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    symbol TEXT NOT NULL,
                    side TEXT NOT NULL,
                    quantity INTEGER NOT NULL,
                    entryPrice REAL NOT NULL,
                    stopPrice REAL NOT NULL,
                    targetPrice REAL NOT NULL,
                    strategy TEXT NOT NULL,
                    regime TEXT NOT NULL,
                    confidence REAL NOT NULL,
                    sourceEventId INTEGER,
                    openedAtMs INTEGER NOT NULL,
                    lastPrice REAL NOT NULL,
                    maxFavourablePrice REAL NOT NULL,
                    maxAdversePrice REAL NOT NULL,
                    lastEvaluatedAtMs INTEGER NOT NULL,
                    status TEXT NOT NULL
                )""")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_shadow_positions_symbol_status ON shadow_positions(symbol, status)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_shadow_positions_openedAtMs ON shadow_positions(openedAtMs)")
                db.execSQL("""CREATE TABLE IF NOT EXISTS shadow_trades (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    symbol TEXT NOT NULL,
                    side TEXT NOT NULL,
                    quantity INTEGER NOT NULL,
                    entryPrice REAL NOT NULL,
                    exitPrice REAL NOT NULL,
                    grossPnl REAL NOT NULL,
                    estimatedCosts REAL NOT NULL,
                    netPnl REAL NOT NULL,
                    exitReason TEXT NOT NULL,
                    strategy TEXT NOT NULL,
                    regime TEXT NOT NULL,
                    confidence REAL NOT NULL,
                    mfeRupees REAL NOT NULL,
                    maeRupees REAL NOT NULL,
                    sourceEventId INTEGER,
                    openedAtMs INTEGER NOT NULL,
                    closedAtMs INTEGER NOT NULL
                )""")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_shadow_trades_symbol_closedAtMs ON shadow_trades(symbol, closedAtMs)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_shadow_trades_closedAtMs ON shadow_trades(closedAtMs)")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""CREATE TABLE IF NOT EXISTS managed_positions (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    engine TEXT NOT NULL,
                    symbol TEXT NOT NULL,
                    product TEXT NOT NULL,
                    side TEXT NOT NULL,
                    quantity INTEGER NOT NULL,
                    entryPrice REAL NOT NULL,
                    stopPrice REAL,
                    targetPrice REAL,
                    strategy TEXT NOT NULL,
                    regime TEXT NOT NULL,
                    confidence REAL NOT NULL,
                    sourceEventId INTEGER,
                    openOrderId TEXT NOT NULL,
                    openReferenceId TEXT NOT NULL,
                    smartOrderId TEXT,
                    openedAtMs INTEGER NOT NULL,
                    lastPrice REAL NOT NULL,
                    maxFavourablePrice REAL NOT NULL,
                    maxAdversePrice REAL NOT NULL,
                    lastEvaluatedAtMs INTEGER NOT NULL,
                    status TEXT NOT NULL
                )""")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_managed_positions_engine_symbol_status ON managed_positions(engine, symbol, status)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_managed_positions_product_symbol_status ON managed_positions(product, symbol, status)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_managed_positions_openedAtMs ON managed_positions(openedAtMs)")
                db.execSQL("""CREATE TABLE IF NOT EXISTS managed_trades (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    engine TEXT NOT NULL,
                    symbol TEXT NOT NULL,
                    product TEXT NOT NULL,
                    side TEXT NOT NULL,
                    quantity INTEGER NOT NULL,
                    entryPrice REAL NOT NULL,
                    exitPrice REAL NOT NULL,
                    grossPnl REAL NOT NULL,
                    estimatedCosts REAL NOT NULL,
                    netPnl REAL NOT NULL,
                    exitReason TEXT NOT NULL,
                    strategy TEXT NOT NULL,
                    regime TEXT NOT NULL,
                    confidence REAL NOT NULL,
                    mfeRupees REAL NOT NULL,
                    maeRupees REAL NOT NULL,
                    sourceEventId INTEGER,
                    openOrderId TEXT NOT NULL,
                    closeOrderId TEXT NOT NULL,
                    openedAtMs INTEGER NOT NULL,
                    closedAtMs INTEGER NOT NULL
                )""")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_managed_trades_engine_closedAtMs ON managed_trades(engine, closedAtMs)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_managed_trades_symbol_closedAtMs ON managed_trades(symbol, closedAtMs)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_managed_trades_closedAtMs ON managed_trades(closedAtMs)")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE shadow_positions ADD COLUMN anchorPrice REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE shadow_positions ADD COLUMN capitalDeployed REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE shadow_positions ADD COLUMN campaignBudget REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE shadow_positions ADD COLUMN addCount INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE managed_positions ADD COLUMN anchorPrice REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE managed_positions ADD COLUMN capitalDeployed REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE managed_positions ADD COLUMN campaignBudget REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE managed_positions ADD COLUMN addCount INTEGER NOT NULL DEFAULT 0")

                db.execSQL("""CREATE TABLE IF NOT EXISTS learning_calls (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    callDate TEXT NOT NULL,
                    symbol TEXT NOT NULL,
                    source TEXT NOT NULL,
                    action TEXT NOT NULL,
                    entryPrice REAL NOT NULL,
                    targetPrice REAL,
                    targetPct REAL,
                    multifyExitPrice REAL,
                    longRealizedPct REAL,
                    buyEventId INTEGER,
                    sellEventId INTEGER,
                    buyAtMs INTEGER NOT NULL,
                    sellAtMs INTEGER,
                    minPostSellPrice REAL,
                    postSellRetracementFraction REAL,
                    shortObservationFinalized INTEGER NOT NULL,
                    updatedAtMs INTEGER NOT NULL
                )""")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_learning_calls_callDate ON learning_calls(callDate)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_learning_calls_symbol_callDate ON learning_calls(symbol, callDate)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_learning_calls_source ON learning_calls(source)")

                db.execSQL("""CREATE TABLE IF NOT EXISTS price_observations (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    eventId INTEGER NOT NULL,
                    symbol TEXT NOT NULL,
                    phase TEXT NOT NULL,
                    offsetSeconds INTEGER NOT NULL,
                    observedAtMs INTEGER NOT NULL,
                    price REAL NOT NULL
                )""")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_price_observations_eventId_phase_offsetSeconds ON price_observations(eventId, phase, offsetSeconds)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_price_observations_symbol_observedAtMs ON price_observations(symbol, observedAtMs)")

                db.execSQL("""CREATE TABLE IF NOT EXISTS strategy_snapshots (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    atMs INTEGER NOT NULL,
                    eventId INTEGER,
                    symbol TEXT NOT NULL,
                    side TEXT NOT NULL,
                    regime TEXT NOT NULL,
                    strategy TEXT NOT NULL,
                    directionalScore REAL NOT NULL,
                    confidence REAL NOT NULL,
                    ltp REAL NOT NULL,
                    vwap REAL,
                    ema9 REAL,
                    ema20 REAL,
                    atr14 REAL,
                    rsi14 REAL,
                    rvol REAL,
                    macdHistogram REAL,
                    spreadBps REAL,
                    orderBookImbalance REAL,
                    dayChangePct REAL,
                    marketCap REAL,
                    votes TEXT NOT NULL
                )""")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_strategy_snapshots_symbol_atMs ON strategy_snapshots(symbol, atMs)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_strategy_snapshots_side_atMs ON strategy_snapshots(side, atMs)")

                db.execSQL("""CREATE TABLE IF NOT EXISTS forecasts (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    forecastDate TEXT NOT NULL,
                    rank INTEGER NOT NULL,
                    symbol TEXT NOT NULL,
                    bias TEXT NOT NULL,
                    confidence REAL NOT NULL,
                    score REAL NOT NULL,
                    reason TEXT NOT NULL,
                    generatedAtMs INTEGER NOT NULL,
                    multifyMatched INTEGER NOT NULL,
                    multifyDirectionMatched INTEGER NOT NULL
                )""")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_forecasts_forecastDate_rank ON forecasts(forecastDate, rank)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_forecasts_symbol_forecastDate ON forecasts(symbol, forecastDate)")

                db.execSQL("""CREATE TABLE IF NOT EXISTS research_reports (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    reportDate TEXT NOT NULL,
                    generatedAtMs INTEGER NOT NULL,
                    title TEXT NOT NULL,
                    summary TEXT NOT NULL
                )""")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_research_reports_reportDate ON research_reports(reportDate)")
            }
        }
    }
}
