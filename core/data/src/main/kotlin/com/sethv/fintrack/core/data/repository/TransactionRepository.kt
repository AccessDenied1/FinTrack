package com.sethv.fintrack.core.data.repository

import com.sethv.fintrack.core.model.PendingTransaction
import com.sethv.fintrack.core.model.Transaction
import kotlinx.coroutines.flow.Flow

interface TransactionRepository {

    suspend fun insertTransaction(transaction: Transaction): Long

    /**
     * Promotes a pending row: inserts it as an accepted Transaction, then marks
     * the pending row ACCEPTED. Caller-provided edits (amount/merchant/category/notes)
     * are applied to the persisted transaction.
     */
    suspend fun acceptPending(
        pending: PendingTransaction,
        amount: Double,
        merchant: String,
        category: com.sethv.fintrack.core.model.ExpenseCategory,
        notes: String,
    ): Long

    /**
     * Bulk-accept: atomically inserts all pending rows as transactions AND
     * marks every source pending row ACCEPTED in a single DB transaction.
     * No partial state on crash mid-operation. Returns the inserted transaction ids.
     */
    suspend fun acceptAllPending(pending: List<PendingTransaction>): List<Long>

    fun getAllTransactions(): Flow<List<Transaction>>

    suspend fun getTransactionById(id: Long): Transaction?

    suspend fun deleteTransaction(id: Long)

    fun getTransactionsByDateRange(startTime: Long, endTime: Long): Flow<List<Transaction>>
}