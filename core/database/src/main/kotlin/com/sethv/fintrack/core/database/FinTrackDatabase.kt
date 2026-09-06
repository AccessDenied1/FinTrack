package com.sethv.fintrack.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.sethv.fintrack.core.database.converter.Converters
import com.sethv.fintrack.core.database.dao.BalanceSettingsDao
import com.sethv.fintrack.core.database.dao.BankCardDao
import com.sethv.fintrack.core.database.dao.CardBillDao
import com.sethv.fintrack.core.database.dao.PendingTransactionDao
import com.sethv.fintrack.core.database.dao.TransactionDao
import com.sethv.fintrack.core.database.entity.BalanceSettingsEntity
import com.sethv.fintrack.core.database.entity.BankCardEntity
import com.sethv.fintrack.core.database.entity.CardBillEntity
import com.sethv.fintrack.core.database.entity.PendingTransactionEntity
import com.sethv.fintrack.core.database.entity.TransactionEntity

@Database(
    entities = [
        TransactionEntity::class,
        PendingTransactionEntity::class,
        BalanceSettingsEntity::class,
        BankCardEntity::class,
        CardBillEntity::class,
    ],
    version = 5,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class FinTrackDatabase : RoomDatabase() {

    abstract fun transactionDao(): TransactionDao

    abstract fun pendingTransactionDao(): PendingTransactionDao

    abstract fun balanceSettingsDao(): BalanceSettingsDao

    abstract fun bankCardDao(): BankCardDao

    abstract fun cardBillDao(): CardBillDao
}
