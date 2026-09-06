package com.sethv.fintrack.core.data.repository

import androidx.room.withTransaction
import com.sethv.fintrack.core.data.mapper.toDomain
import com.sethv.fintrack.core.data.mapper.toEntity
import com.sethv.fintrack.core.database.FinTrackDatabase
import com.sethv.fintrack.core.database.dao.TransactionDao
import com.sethv.fintrack.core.model.ExpenseCategory
import com.sethv.fintrack.core.model.PendingStatus
import com.sethv.fintrack.core.model.PendingTransaction
import com.sethv.fintrack.core.model.Transaction
import com.sethv.fintrack.core.model.TransactionType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class TransactionRepositoryImpl @Inject constructor(
    private val database: FinTrackDatabase,
    private val transactionDao: TransactionDao,
    private val pendingTransactionRepository: PendingTransactionRepository,
    private val creditCardRepository: CreditCardRepository,
) : TransactionRepository {

    override suspend fun insertTransaction(transaction: Transaction): Long =
        transactionDao.insert(transaction.toEntity())

    override suspend fun acceptPending(
        pending: PendingTransaction,
        amount: Double,
        merchant: String,
        category: ExpenseCategory,
        type: TransactionType,
        notes: String,
    ): Long {
        val transaction = pending.toTransaction(
            amount = amount,
            merchant = merchant,
            category = category,
            type = type,
            notes = notes,
            cardId = resolveCardId(pending),
        )
        return database.withTransaction {
            // Re-validate INSIDE the DB transaction: the row may have been
            // accepted/rejected by another path since the caller loaded it.
            val current = database.pendingTransactionDao().getById(pending.id)
            if (current == null || current.status != PendingStatus.PENDING.name) {
                return@withTransaction TransactionRepository.ALREADY_HANDLED
            }
            val newId = transactionDao.insert(transaction.toEntity())
            pendingTransactionRepository.acceptPending(pending.id)
            newId
        }
    }

    /**
     * Lazy credit-card link: when the pending row names a bank that matches a
     * registered card AND its timestamp falls inside that card's matching
     * unpaid bill window [statementStart - 1d, dueDate + 1d], returns the card
     * id; otherwise null (old rows and bank-name-only rows stay unlinked).
     */
    private suspend fun resolveCardId(pending: PendingTransaction): Long? {
        val cardId = creditCardRepository.findCardByBank(pending.bank) ?: return null
        val bill = creditCardRepository.getBillsForCard(cardId).first()
            .filter { !it.isPaid && it.statementStart > 0L }
            .firstOrNull { pending.dateTime in (it.statementStart - CARD_LINK_WINDOW)..(it.dueDate + CARD_LINK_WINDOW) }
            ?: return null
        return cardId
    }

    override suspend fun acceptAllPending(pending: List<PendingTransaction>): List<Long> {
        if (pending.isEmpty()) return emptyList()
        return database.withTransaction {
            // Only rows still PENDING at commit time are promoted — anything
            // handled meanwhile is skipped instead of double-inserted.
            val fresh = pending.mapNotNull { p ->
                database.pendingTransactionDao().getById(p.id)
                    ?.takeIf { it.status == PendingStatus.PENDING.name }
                    ?.toDomain()
            }
            if (fresh.isEmpty()) return@withTransaction emptyList()

            val transactions = fresh.map { p ->
                // Review tab "Accept All" uses the parsed values as-is — same field
                // mapping as a single-row accept where the user didn't edit anything.
                p.toTransaction(
                    amount = p.amount,
                    merchant = p.merchant,
                    category = p.category,
                    type = p.type,
                    notes = p.notes,
                    cardId = resolveCardId(p),
                )
            }
            val newIds = transactionDao.insertAll(transactions.map { it.toEntity() })
            pendingTransactionRepository.acceptAllPending(fresh.map { it.id })
            newIds
        }
    }

    override fun getAllTransactions(): Flow<List<Transaction>> =
        transactionDao.getAll().map { entities -> entities.map { it.toDomain() } }

    override suspend fun getTransactionById(id: Long): Transaction? =
        transactionDao.getById(id)?.toDomain()

    override suspend fun deleteTransaction(id: Long) {
        transactionDao.deleteById(id)
    }

    override fun getTransactionsByDateRange(startTime: Long, endTime: Long): Flow<List<Transaction>> =
        transactionDao.getByDateRange(startTime, endTime).map { entities ->
            entities.map { it.toDomain() }
        }

    override suspend fun existsBySmsFingerprint(smsBody: String, minuteBucket: Long): Boolean =
        transactionDao.countBySmsFingerprint(smsBody, minuteBucket) > 0
}

private fun PendingTransaction.toTransaction(
    amount: Double,
    merchant: String,
    category: ExpenseCategory,
    type: TransactionType,
    notes: String,
    cardId: Long? = null,
): Transaction = Transaction(
    amount = amount,
    merchant = merchant,
    category = category,
    type = type,
    dateTime = dateTime,
    bank = bank,
    notes = notes,
    smsBody = smsBody,
    createdAt = createdAt,
    cardId = cardId,
)

// A debit posted for a card can legitimately land slightly before the
// statement opens or right after it is due — one day of slack on each end.
private const val CARD_LINK_WINDOW: Long = 24L * 60 * 60 * 1000
