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
