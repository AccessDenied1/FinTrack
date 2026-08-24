package com.sethv.fintrack.core.database.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS balance_settings (
                id INTEGER NOT NULL PRIMARY KEY,
                initialBalance REAL NOT NULL DEFAULT 0.0,
                setAt INTEGER NOT NULL DEFAULT 0
            )
        """.trimIndent())
    }
}

// v3: query-support indices. Names must match Room's generated convention
// (index_<table>_<column>) so schema validation passes after migration.
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_dateTime` ON `transactions` (`dateTime`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_pending_transactions_status` ON `pending_transactions` (`status`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_pending_transactions_dateTime` ON `pending_transactions` (`dateTime`)")
    }
}
