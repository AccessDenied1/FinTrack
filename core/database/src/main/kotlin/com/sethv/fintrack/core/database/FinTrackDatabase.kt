package com.sethv.fintrack.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.sethv.fintrack.core.database.converter.Converters
import com.sethv.fintrack.core.database.dao.PendingTransactionDao
import com.sethv.fintrack.core.database.dao.TransactionDao
import com.sethv.fintrack.core.database.entity.PendingTransactionEntity
import com.sethv.fintrack.core.database.entity.TransactionEntity

@Database(
    entities = [
        TransactionEntity::class,
        PendingTransactionEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class FinTrackDatabase : RoomDatabase() {

    abstract fun transactionDao(): TransactionDao

    abstract fun pendingTransactionDao(): PendingTransactionDao
}
