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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class TransactionRepositoryImpl @Inject constructor(
    private val database: FinTrackDatabase,
    private val transactionDao: TransactionDao,
    private val pendingTransactionRepository: PendingTransactionRepository,
) : TransactionRepository {

    override suspend fun insertTransaction(transaction: Transaction): Long =
        transactionDao.insert(transaction.toEntity())

    override suspend fun acceptPending(
        pending: PendingTransaction,
        amount: Double,
        merchant: String,
        category: ExpenseCategory,
        notes: String,
    ): Long {
        val transaction = pending.toTransaction(
            amount = amount,
            merchant = merchant,
            category = category,
            notes = notes,
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
                    notes = p.notes,
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
    notes: String,
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
)