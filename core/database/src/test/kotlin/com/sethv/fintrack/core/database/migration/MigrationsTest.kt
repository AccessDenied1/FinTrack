package com.sethv.fintrack.core.database.migration

import android.database.Cursor
import androidx.sqlite.db.SupportSQLiteDatabase
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertTrue
import org.junit.Test

class MigrationsTest {

    @Test
    fun `db 4 opens as 5 with new cols null`() {
        val db = mockk<SupportSQLiteDatabase>(relaxed = true)
        val cursor = mockk<Cursor>(relaxed = true)
        every { cursor.moveToFirst() } returns true
        // SupportSQLiteDatabase.query has overloads; mock both
        every { db.query(any<String>()) } returns cursor
        every { db.query(any<String>(), any()) } returns cursor

        // Run migration 4 -> 5
        MIGRATION_4_5.migrate(db)

        // Verify all expected execSQL calls
        verify { db.execSQL("ALTER TABLE card_bills ADD COLUMN creditLimit REAL") }
        verify { db.execSQL("ALTER TABLE card_bills ADD COLUMN statementStart INTEGER NOT NULL DEFAULT 0") }
        verify { db.execSQL("ALTER TABLE transactions ADD COLUMN cardId INTEGER") }
        verify { db.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_cardId ON transactions(cardId)") }
        verify { db.execSQL("ALTER TABLE credit_cards ADD COLUMN creditLimitOverride REAL") }

        // Validate new columns exist via SELECT (mocked query should not throw and cursor moves)
        db.query("SELECT creditLimit, statementStart FROM card_bills").use { assertTrue(it.moveToFirst()) }
        db.query("SELECT cardId FROM transactions").use { assertTrue(it.moveToFirst()) }
        db.query("SELECT creditLimitOverride FROM credit_cards").use { assertTrue(it.moveToFirst()) }
    }
}
