package com.sethv.fintrack.core.data.repository

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.sethv.fintrack.core.data.mapper.toDomain
import com.sethv.fintrack.core.data.mapper.toEntity
import com.sethv.fintrack.core.database.FinTrackDatabase
import com.sethv.fintrack.core.database.entity.TransactionEntity
import com.sethv.fintrack.core.model.ExpenseCategory
import com.sethv.fintrack.core.model.PendingStatus
import com.sethv.fintrack.core.model.PendingTransaction
import com.sethv.fintrack.core.model.TransactionType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class CreditCardRepositoryImplTest {

    private val closers = mutableListOf<() -> Unit>()

    @After
    fun tearDown() {
        closers.forEach { runCatching { it() } }
        closers.clear()
    }

    @Test
    fun `upsertBill persists creditLimit and statementStart`() = runTest {
        val db = buildDb()
        val repo = CreditCardRepositoryImpl(db.bankCardDao(), db.cardBillDao())
        val cardId = repo.findOrCreateCard("HDFC", "4521")

        val billId = repo.upsertBill(
            cardId = cardId,
            totalDue = 5000.0,
            minDue = 250.0,
            dueDate = DUE,
            statementLabel = "Nov",
            creditLimit = LIMIT,
            statementStart = START,
        )

        val entity = db.cardBillDao().getById(billId)!!
        assertEquals(LIMIT, entity.creditLimit!!, 0.01)
        assertEquals(START, entity.statementStart)
        val bill = repo.getBillsForCard(cardId).first().single()
        assertEquals(LIMIT, bill.creditLimit!!, 0.01)
        assertEquals(START, bill.statementStart)
    }

    @Test
    fun `upsertBill without limit leaves creditLimit null and statementStart zero`() = runTest {
        val db = buildDb()
        val repo = CreditCardRepositoryImpl(db.bankCardDao(), db.cardBillDao())
        val cardId = repo.findOrCreateCard("HDFC", "4521")

        val billId = repo.upsertBill(cardId, 5000.0, 250.0, DUE, "Nov")

        val bill = repo.getBillsForCard(cardId).first().single()
        assertEquals(billId, bill.id)
        assertNull(bill.creditLimit)
        assertEquals(0L, bill.statementStart)
        assertNull(db.cardBillDao().getById(billId)!!.creditLimit)
    }

    @Test
    fun `upsertBill with limit also sets the card creditLimitOverride`() = runTest {
        val db = buildDb()
        val repo = CreditCardRepositoryImpl(db.bankCardDao(), db.cardBillDao())
        val cardId = repo.findOrCreateCard("HDFC", "4521")

        repo.upsertBill(cardId, 5000.0, 250.0, DUE, "Nov", creditLimit = LIMIT, statementStart = START)

        assertEquals(LIMIT, db.bankCardDao().getById(cardId)!!.creditLimitOverride!!, 0.01)
    }

    @Test
    fun `findCardByBank matches case-insensitively and ignores surrounding whitespace`() = runTest {
        val db = buildDb()
        val repo = CreditCardRepositoryImpl(db.bankCardDao(), db.cardBillDao())
        val cardId = repo.findOrCreateCard("HDFC", "4521")

        assertEquals(cardId, repo.findCardByBank("hdfc"))
        assertEquals(cardId, repo.findCardByBank("  HDFC  "))
        assertEquals(cardId, repo.findCardByBank("Hdfc"))
    }

    @Test
    fun `findCardByBank returns null when no card matches`() = runTest {
        val db = buildDb()
        val repo = CreditCardRepositoryImpl(db.bankCardDao(), db.cardBillDao())
        repo.findOrCreateCard("HDFC", "4521")

        assertNull(repo.findCardByBank("ICICI"))
        assertNull(repo.findCardByBank(""))
    }

    @Test
    fun `findCardIdForTimestamp links only when exactly one same-bank window contains timestamp`() = runTest {
        val db = buildDb()
        val repo = CreditCardRepositoryImpl(db.bankCardDao(), db.cardBillDao())
        // Non-qualifying card inserted FIRST: old first-match logic would check
        // only this card's window and give up (null) instead of finding cardA.
        val cardOther = repo.findOrCreateCard("HDFC", "9876")
        val cardTarget = repo.findOrCreateCard("HDFC", "4521")
        repo.upsertBill(cardTarget, 5000.0, 250.0, DUE, "Nov", statementStart = START)
        val start2 = DUE + 10 * DAY
        repo.upsertBill(cardOther, 7000.0, 350.0, start2 + 30 * DAY, "Dec", statementStart = start2)

        val insideTarget = START + 2 * DAY
        assertEquals(cardTarget, repo.findCardIdForTimestamp("HDFC", insideTarget))
        assertEquals(cardOther, repo.findCardIdForTimestamp("  hdfc  ", start2 + 2 * DAY))
        assertNull(repo.findCardIdForTimestamp("HDFC", start2 + 60 * DAY))
        assertNull(repo.findCardIdForTimestamp("ICICI", insideTarget))

        val txnRepo = TransactionRepositoryImpl(db, db.transactionDao(), PendingTransactionRepositoryImpl(db.pendingTransactionDao()), repo)
        val pending = insertPending(db, bank = "HDFC", dateTime = insideTarget)
        val id = txnRepo.acceptPending(
            pending = pending,
            amount = pending.amount,
            merchant = pending.merchant,
            category = pending.category,
            type = pending.type,
            notes = pending.notes,
        )
        assertTrue(id > 0)
        assertEquals(cardTarget, db.transactionDao().getById(id)!!.cardId)
    }

    @Test
    fun `findCardIdForTimestamp stays null when same-bank windows overlap`() = runTest {
        val db = buildDb()
        val repo = CreditCardRepositoryImpl(db.bankCardDao(), db.cardBillDao())
        val cardA = repo.findOrCreateCard("HDFC", "4521")
        val cardB = repo.findOrCreateCard("HDFC", "9876")
        repo.upsertBill(cardA, 5000.0, 250.0, DUE, "Nov", statementStart = START)
        repo.upsertBill(cardB, 7000.0, 350.0, DUE, "Nov", statementStart = START)

        // Inside BOTH windows — ambiguous, must not confidently pick either.
        val ambiguous = START + 2 * DAY
        assertNull(repo.findCardIdForTimestamp("HDFC", ambiguous))

        val txnRepo = TransactionRepositoryImpl(db, db.transactionDao(), PendingTransactionRepositoryImpl(db.pendingTransactionDao()), repo)
        val pending = insertPending(db, bank = "HDFC", dateTime = ambiguous)
        val id = txnRepo.acceptPending(
            pending = pending,
            amount = pending.amount,
            merchant = pending.merchant,
            category = pending.category,
            type = pending.type,
            notes = pending.notes,
        )
        assertTrue(id > 0)
        assertNull(db.transactionDao().getById(id)!!.cardId)
    }

    @Test
    fun `updateLimit persists creditLimitOverride and null clears it`() = runTest {
        val db = buildDb()
        val repo = CreditCardRepositoryImpl(db.bankCardDao(), db.cardBillDao())
        val cardId = repo.findOrCreateCard("HDFC", "4521")

        repo.updateLimit(cardId, 100_000.0)
        assertEquals(100_000.0, db.bankCardDao().getById(cardId)!!.creditLimitOverride!!, 0.01)

        repo.updateLimit(cardId, null)
        assertNull(db.bankCardDao().getById(cardId)!!.creditLimitOverride)
    }

    @Test
    fun `acceptPending sets cardId when bank and window match`() = runTest {
        val db = buildDb()
        val creditRepo = CreditCardRepositoryImpl(db.bankCardDao(), db.cardBillDao())
        val cardId = creditRepo.findOrCreateCard("HDFC", "4521")
        creditRepo.upsertBill(
            cardId = cardId,
            totalDue = 5000.0,
            minDue = 250.0,
            dueDate = DUE,
            statementLabel = "Nov",
            creditLimit = LIMIT,
            statementStart = START,
        )
        val txnRepo = TransactionRepositoryImpl(db, db.transactionDao(), PendingTransactionRepositoryImpl(db.pendingTransactionDao()), creditRepo)

        // 2 days after statementStart — inside [START - 1d, DUE + 1d].
        val pending = insertPending(db, bank = "HDFC", dateTime = START + 2 * DAY)

        val id = txnRepo.acceptPending(
            pending = pending,
            amount = pending.amount,
            merchant = pending.merchant,
            category = pending.category,
            type = pending.type,
            notes = pending.notes,
        )

        assertTrue(id > 0)
        assertEquals(cardId, db.transactionDao().getById(id)!!.cardId)
    }

    @Test
    fun `acceptPending keeps cardId null when no card matches the bank`() = runTest {
        val db = buildDb()
        val creditRepo = CreditCardRepositoryImpl(db.bankCardDao(), db.cardBillDao())
        val cardId = creditRepo.findOrCreateCard("HDFC", "4521")
        creditRepo.upsertBill(
            cardId = cardId,
            totalDue = 5000.0,
            minDue = 250.0,
            dueDate = DUE,
            statementLabel = "Nov",
            creditLimit = LIMIT,
            statementStart = START,
        )
        val txnRepo = TransactionRepositoryImpl(db, db.transactionDao(), PendingTransactionRepositoryImpl(db.pendingTransactionDao()), creditRepo)

        val pending = insertPending(db, bank = "ICICI", dateTime = START + 2 * DAY)

        val id = txnRepo.acceptPending(
            pending = pending,
            amount = pending.amount,
            merchant = pending.merchant,
            category = pending.category,
            type = pending.type,
            notes = pending.notes,
        )

        assertTrue(id > 0)
        assertNull(db.transactionDao().getById(id)!!.cardId)
    }

    @Test
    fun `acceptPending keeps cardId null when dateTime is outside the bill window`() = runTest {
        val db = buildDb()
        val creditRepo = CreditCardRepositoryImpl(db.bankCardDao(), db.cardBillDao())
        val cardId = creditRepo.findOrCreateCard("HDFC", "4521")
        creditRepo.upsertBill(
            cardId = cardId,
            totalDue = 5000.0,
            minDue = 250.0,
            dueDate = DUE,
            statementLabel = "Nov",
            creditLimit = LIMIT,
            statementStart = START,
        )
        val txnRepo = TransactionRepositoryImpl(db, db.transactionDao(), PendingTransactionRepositoryImpl(db.pendingTransactionDao()), creditRepo)

        // 2 full days BEFORE the statement window opens (START - 1d).
        val pending = insertPending(db, bank = "HDFC", dateTime = START - 2 * DAY)

        val id = txnRepo.acceptPending(
            pending = pending,
            amount = pending.amount,
            merchant = pending.merchant,
            category = pending.category,
            type = pending.type,
            notes = pending.notes,
        )

        assertTrue(id > 0)
        assertNull(db.transactionDao().getById(id)!!.cardId)
    }

    @Test
    fun `acceptAllPending links matching rows and leaves others null`() = runTest {
        val db = buildDb()
        val creditRepo = CreditCardRepositoryImpl(db.bankCardDao(), db.cardBillDao())
        val cardId = creditRepo.findOrCreateCard("HDFC", "4521")
        creditRepo.upsertBill(
            cardId = cardId,
            totalDue = 5000.0,
            minDue = 250.0,
            dueDate = DUE,
            statementLabel = "Nov",
            creditLimit = LIMIT,
            statementStart = START,
        )
        val txnRepo = TransactionRepositoryImpl(db, db.transactionDao(), PendingTransactionRepositoryImpl(db.pendingTransactionDao()), creditRepo)
        val linked = insertPending(db, bank = "HDFC", dateTime = DUE - DAY)
        val unlinked = insertPending(db, bank = "AXIS", dateTime = DUE - DAY)

        val ids = txnRepo.acceptAllPending(listOf(linked, unlinked))

        assertEquals(2, ids.size)
        val linkedEntity = db.transactionDao().getById(ids.first { it != 0L })!!
        val unlinkedEntity = db.transactionDao().getById(ids.last { it != 0L })!!
        assertEquals(cardId, linkedEntity.cardId)
        assertNull(unlinkedEntity.cardId)
    }

    @Test
    fun `transaction mapper round trip preserves cardId`() {
        val withCard = TransactionEntity(
            id = 3,
            amount = 10.0,
            merchant = "M",
            category = "FOOD",
            type = "DEBIT",
            dateTime = 1L,
            bank = "HDFC",
            notes = "",
            smsBody = "",
            createdAt = 2L,
            cardId = 9L,
        )
        val domain = withCard.toDomain()
        assertEquals(9L, domain.cardId)
        assertEquals(9L, domain.toEntity().cardId)

        val withoutCard = withCard.copy(cardId = null)
        assertNull(withoutCard.toDomain().cardId)
    }

    private suspend fun insertPending(db: FinTrackDatabase, bank: String, dateTime: Long): PendingTransaction {
        val pending = PendingTransaction(
            amount = 100.0,
            merchant = "TEST MERCHANT",
            category = ExpenseCategory.FOOD,
            type = TransactionType.DEBIT,
            dateTime = dateTime,
            bank = bank,
            status = PendingStatus.PENDING,
        )
        val id = db.pendingTransactionDao().insert(pending.toEntity())
        return pending.copy(id = id)
    }

    private fun buildDb(): FinTrackDatabase {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val factory = FrameworkSQLiteOpenHelperFactory()
        val callback = object : SupportSQLiteOpenHelper.Callback(5) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL(V5_SCHEMA)
            }

            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
            }
        }
        val helper = factory.create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(null)
                .callback(callback)
                .build(),
        )
        closers += { helper.close() }
        val db = Room.inMemoryDatabaseBuilder(context, FinTrackDatabase::class.java)
            .openHelperFactory(factory)
            .build()
        closers += { db.close() }
        return db
    }

    private companion object {
        const val DAY: Long = 24L * 60 * 60 * 1000
        const val DUE: Long = 1_750_000_000_000L
        const val START: Long = DUE - 30 * DAY
        const val LIMIT: Double = 100_000.0

        val V5_SCHEMA = """
            CREATE TABLE IF NOT EXISTS `transactions` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `amount` REAL NOT NULL,
                `merchant` TEXT NOT NULL,
                `category` TEXT NOT NULL,
                `type` TEXT NOT NULL,
                `dateTime` INTEGER NOT NULL,
                `bank` TEXT NOT NULL,
                `notes` TEXT NOT NULL,
                `smsBody` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `cardId` INTEGER
            );
            CREATE INDEX IF NOT EXISTS `index_transactions_dateTime` ON `transactions` (`dateTime`);
            CREATE INDEX IF NOT EXISTS `index_transactions_cardId` ON `transactions` (`cardId`);
            CREATE TABLE IF NOT EXISTS `pending_transactions` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `amount` REAL NOT NULL,
                `merchant` TEXT NOT NULL,
                `category` TEXT NOT NULL,
                `type` TEXT NOT NULL,
                `dateTime` INTEGER NOT NULL,
                `bank` TEXT NOT NULL,
                `notes` TEXT NOT NULL,
                `smsBody` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `status` TEXT NOT NULL
            );
            CREATE INDEX IF NOT EXISTS `index_pending_transactions_status` ON `pending_transactions` (`status`);
            CREATE INDEX IF NOT EXISTS `index_pending_transactions_dateTime` ON `pending_transactions` (`dateTime`);
            CREATE TABLE IF NOT EXISTS `balance_settings` (
                `id` INTEGER NOT NULL PRIMARY KEY,
                `initialBalance` REAL NOT NULL DEFAULT 0.0,
                `setAt` INTEGER NOT NULL DEFAULT 0
            );
            CREATE TABLE IF NOT EXISTS `credit_cards` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `bankName` TEXT NOT NULL,
                `lastFour` TEXT NOT NULL,
                `label` TEXT NOT NULL DEFAULT '',
                `createdAt` INTEGER NOT NULL,
                `creditLimitOverride` REAL
            );
            CREATE UNIQUE INDEX IF NOT EXISTS `index_credit_cards_bankName_lastFour` ON `credit_cards` (`bankName`, `lastFour`);
            CREATE TABLE IF NOT EXISTS `card_bills` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `cardId` INTEGER NOT NULL,
                `totalDue` REAL NOT NULL,
                `minDue` REAL NOT NULL DEFAULT 0.0,
                `dueDate` INTEGER NOT NULL,
                `statementLabel` TEXT NOT NULL DEFAULT '',
                `generatedAt` INTEGER NOT NULL,
                `isPaid` INTEGER NOT NULL DEFAULT 0,
                `paidAt` INTEGER NOT NULL DEFAULT 0,
                `paidAmount` REAL NOT NULL DEFAULT 0.0,
                `creditLimit` REAL,
                `statementStart` INTEGER NOT NULL DEFAULT 0
            );
            CREATE INDEX IF NOT EXISTS `index_card_bills_cardId` ON `card_bills` (`cardId`);
            CREATE INDEX IF NOT EXISTS `index_card_bills_dueDate` ON `card_bills` (`dueDate`);
            CREATE INDEX IF NOT EXISTS `index_card_bills_isPaid` ON `card_bills` (`isPaid`);
        """.trimIndent()
    }
}
