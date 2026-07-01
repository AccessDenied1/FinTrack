package com.sethv.fintrack.core.database.di

import android.content.Context
import androidx.room.Room
import com.sethv.fintrack.core.database.FinTrackDatabase
import com.sethv.fintrack.core.database.dao.PendingTransactionDao
import com.sethv.fintrack.core.database.dao.TransactionDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideFinTrackDatabase(
        @ApplicationContext context: Context,
    ): FinTrackDatabase = Room.databaseBuilder(
        context,
        FinTrackDatabase::class.java,
        "fintrack.db",
    ).build()

    @Provides
    fun provideTransactionDao(database: FinTrackDatabase): TransactionDao =
        database.transactionDao()

    @Provides
    fun providePendingTransactionDao(database: FinTrackDatabase): PendingTransactionDao =
        database.pendingTransactionDao()
}
