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

// v4: credit card management — cards auto-registered from bill/payment SMS
// and their statement bills with due dates.
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS credit_cards (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                bankName TEXT NOT NULL,
                lastFour TEXT NOT NULL,
                label TEXT NOT NULL DEFAULT '',
                createdAt INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_credit_cards_bankName_lastFour` ON `credit_cards` (`bankName`, `lastFour`)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS card_bills (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                cardId INTEGER NOT NULL,
                totalDue REAL NOT NULL,
                minDue REAL NOT NULL DEFAULT 0.0,
                dueDate INTEGER NOT NULL,
                statementLabel TEXT NOT NULL DEFAULT '',
                generatedAt INTEGER NOT NULL,
                isPaid INTEGER NOT NULL DEFAULT 0,
                paidAt INTEGER NOT NULL DEFAULT 0,
                paidAmount REAL NOT NULL DEFAULT 0.0
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_card_bills_cardId` ON `card_bills` (`cardId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_card_bills_dueDate` ON `card_bills` (`dueDate`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_card_bills_isPaid` ON `card_bills` (`isPaid`)")
    }
}

// v5: nullable credit limit / statement period + lazy card link
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE card_bills ADD COLUMN creditLimit REAL")
        db.execSQL("ALTER TABLE card_bills ADD COLUMN statementStart INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE transactions ADD COLUMN cardId INTEGER")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_cardId ON transactions(cardId)")
        db.execSQL("ALTER TABLE credit_cards ADD COLUMN creditLimitOverride REAL")
    }
}
