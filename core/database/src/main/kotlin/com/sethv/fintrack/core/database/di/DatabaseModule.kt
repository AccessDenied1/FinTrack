package com.sethv.fintrack.core.database.di

import android.content.Context
import androidx.room.Room
import com.sethv.fintrack.core.database.FinTrackDatabase
import com.sethv.fintrack.core.database.dao.BalanceSettingsDao
import com.sethv.fintrack.core.database.dao.BankCardDao
import com.sethv.fintrack.core.database.dao.CardBillDao
import com.sethv.fintrack.core.database.dao.PendingTransactionDao
import com.sethv.fintrack.core.database.dao.TransactionDao
import com.sethv.fintrack.core.database.migration.MIGRATION_1_2
import com.sethv.fintrack.core.database.migration.MIGRATION_2_3
import com.sethv.fintrack.core.database.migration.MIGRATION_3_4
import com.sethv.fintrack.core.database.migration.MIGRATION_4_5
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
    )
        .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
        .build()

    @Provides
    fun provideTransactionDao(database: FinTrackDatabase): TransactionDao =
        database.transactionDao()

    @Provides
    fun providePendingTransactionDao(database: FinTrackDatabase): PendingTransactionDao =
        database.pendingTransactionDao()

    @Provides
    fun provideBalanceSettingsDao(database: FinTrackDatabase): BalanceSettingsDao =
        database.balanceSettingsDao()

    @Provides
    fun provideBankCardDao(database: FinTrackDatabase): BankCardDao =
        database.bankCardDao()

    @Provides
    fun provideCardBillDao(database: FinTrackDatabase): CardBillDao =
        database.cardBillDao()
}
