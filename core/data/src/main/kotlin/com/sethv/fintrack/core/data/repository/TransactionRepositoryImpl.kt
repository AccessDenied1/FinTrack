package com.sethv.fintrack.core.data.repository

import androidx.room.withTransaction
import com.sethv.fintrack.core.data.mapper.toDomain
import com.sethv.fintrack.core.data.mapper.toEntity
import com.sethv.fintrack.core.database.FinTrackDatabase
import com.sethv.fintrack.core.database.dao.TransactionDao
import com.sethv.fintrack.core.model.ExpenseCategory
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
            val newId = transactionDao.insert(transaction.toEntity())
            pendingTransactionRepository.acceptPending(pending.id)
            newId
        }
    }

    override suspend fun acceptAllPending(pending: List<PendingTransaction>): List<Long> {
        if (pending.isEmpty()) return emptyList()
        val transactions = pending.map { p ->
            // Review tab "Accept All" uses the parsed values as-is — same field
            // mapping as a single-row accept where the user didn't edit anything.
            p.toTransaction(
                amount = p.amount,
                merchant = p.merchant,
                category = p.category,
                notes = p.notes,
            )
        }
        return database.withTransaction {
            val newIds = transactionDao.insertAll(transactions.map { it.toEntity() })
            pendingTransactionRepository.acceptAllPending(pending.map { it.id })
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