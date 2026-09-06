package com.sethv.fintrack.core.database.migration

import android.content.Context
import android.database.Cursor
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
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

        // Verify all expected execSQL calls exactly once
        verify(exactly = 1) { db.execSQL("ALTER TABLE card_bills ADD COLUMN creditLimit REAL") }
        verify(exactly = 1) { db.execSQL("ALTER TABLE card_bills ADD COLUMN statementStart INTEGER NOT NULL DEFAULT 0") }
        verify(exactly = 1) { db.execSQL("ALTER TABLE transactions ADD COLUMN cardId INTEGER") }
        verify(exactly = 1) { db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_cardId` ON `transactions` (`cardId`)") }
        verify(exactly = 1) { db.execSQL("ALTER TABLE credit_cards ADD COLUMN creditLimitOverride REAL") }

        // Validate new columns exist via SELECT (mocked query should not throw and cursor moves)
        db.query("SELECT creditLimit, statementStart FROM card_bills").use { assertTrue(it.moveToFirst()) }
        db.query("SELECT cardId FROM transactions").use { assertTrue(it.moveToFirst()) }
        db.query("SELECT creditLimitOverride FROM credit_cards").use { assertTrue(it.moveToFirst()) }
    }

    @Test
    fun `real sqlite migration 4 to 5 adds columns and index`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val factory = FrameworkSQLiteOpenHelperFactory()
        val callback = object : SupportSQLiteOpenHelper.Callback(4) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                // v4 schema – raw CREATEs copied from entities / MIGRATION_3_4
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `transactions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `amount` REAL NOT NULL, `merchant` TEXT NOT NULL, `category` TEXT NOT NULL, `type` TEXT NOT NULL, `dateTime` INTEGER NOT NULL, `bank` TEXT NOT NULL, `notes` TEXT NOT NULL, `smsBody` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_dateTime` ON `transactions` (`dateTime`)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS credit_cards (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        bankName TEXT NOT NULL,
                        lastFour TEXT NOT NULL,
                        label TEXT NOT NULL DEFAULT '',
                        createdAt INTEGER NOT NULL
                    )
                    """.trimIndent()
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
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_card_bills_cardId` ON `card_bills` (`cardId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_card_bills_dueDate` ON `card_bills` (`dueDate`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_card_bills_isPaid` ON `card_bills` (`isPaid`)")
            }

            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
        }
        val helper = factory.create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(null)
                .callback(callback)
                .build()
        )
        val db = helper.writableDatabase

        // Insert dummy rows at v4 before migration
        db.execSQL("INSERT INTO credit_cards (bankName, lastFour, label, createdAt) VALUES ('HDFC', '1234', 'My Card', 1000)")
        db.execSQL("INSERT INTO transactions (amount, merchant, category, type, dateTime, bank, notes, smsBody, createdAt) VALUES (100.0, 'TestMerchant', 'Food', 'debit', 2000, 'HDFC', '', 'sms', 3000)")
        db.execSQL("INSERT INTO card_bills (cardId, totalDue, minDue, dueDate, statementLabel, generatedAt, isPaid, paidAt, paidAmount) VALUES (1, 5000.0, 100.0, 4000, 'Jan', 5000, 0, 0, 0.0)")

        // Run real migration – this executes actual SQLite ALTERs
        MIGRATION_4_5.migrate(db)

        // Verify new columns via PRAGMA table_info
        fun columnsFor(table: String): Set<String> {
            val cols = mutableSetOf<String>()
            db.query("PRAGMA table_info($table)").use { c ->
                val nameIdx = c.getColumnIndex("name")
                while (c.moveToNext()) {
                    cols.add(c.getString(nameIdx))
                }
            }
            return cols
        }

        val billCols = columnsFor("card_bills")
        assertTrue("card_bills should have creditLimit", billCols.contains("creditLimit"))
        assertTrue("card_bills should have statementStart", billCols.contains("statementStart"))

        val txCols = columnsFor("transactions")
        assertTrue("transactions should have cardId", txCols.contains("cardId"))

        val cardCols = columnsFor("credit_cards")
        assertTrue("credit_cards should have creditLimitOverride", cardCols.contains("creditLimitOverride"))

        // Verify index via sqlite_master
        db.query("SELECT name FROM sqlite_master WHERE type='index' AND tbl_name='transactions' AND name='index_transactions_cardId'").use { c ->
            assertTrue("index_transactions_cardId should exist", c.moveToFirst())
        }

        // SELECT checks – new columns readable and old rows preserved with defaults/nulls
        db.query("SELECT creditLimit, statementStart FROM card_bills").use { c ->
            assertTrue(c.moveToFirst())
            val idxLimit = c.getColumnIndex("creditLimit")
            assertTrue(c.isNull(idxLimit))
            val idxStart = c.getColumnIndex("statementStart")
            assertEquals(0L, c.getLong(idxStart))
        }

        db.query("SELECT cardId FROM transactions").use { c ->
            assertTrue(c.moveToFirst())
            assertTrue(c.isNull(c.getColumnIndex("cardId")))
        }

        db.query("SELECT creditLimitOverride FROM credit_cards").use { c ->
            assertTrue(c.moveToFirst())
            assertTrue(c.isNull(c.getColumnIndex("creditLimitOverride")))
        }

        // Insert after migration with new columns to ensure they work
        db.execSQL("INSERT INTO transactions (amount, merchant, category, type, dateTime, bank, notes, smsBody, createdAt, cardId) VALUES (50.0, 'M2', 'Travel', 'credit', 6000, 'ICICI', '', 'sms2', 7000, 1)")
        db.query("SELECT cardId FROM transactions WHERE merchant='M2'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(1L, c.getLong(c.getColumnIndex("cardId")))
        }

        db.close()
        helper.close()
    }
}
