package com.multify.traderpro.di

import android.content.Context
import androidx.room.Room
import com.multify.traderpro.data.local.AppDatabase
import com.multify.traderpro.data.local.LearningDao
import com.multify.traderpro.data.local.ManagedTradeDao
import com.multify.traderpro.data.local.SignalEventDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "multify_trader_pro.db")
            .addMigrations(AppDatabase.MIGRATION_7_8)
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideSignalEventDao(db: AppDatabase): SignalEventDao = db.signalEventDao()

    @Provides
    fun provideManagedTradeDao(db: AppDatabase): ManagedTradeDao = db.managedTradeDao()

    @Provides
    fun provideLearningDao(db: AppDatabase): LearningDao = db.learningDao()
}
