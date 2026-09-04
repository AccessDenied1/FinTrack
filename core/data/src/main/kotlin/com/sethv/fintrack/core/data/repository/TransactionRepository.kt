package com.sethv.fintrack.core.data.repository

import com.sethv.fintrack.core.model.PendingTransaction
import com.sethv.fintrack.core.model.Transaction
import kotlinx.coroutines.flow.Flow

interface TransactionRepository {

    /** Returned by [acceptPending] when the row was already accepted/rejected elsewhere. */
    companion object {
        const val ALREADY_HANDLED: Long = -1L
    }

    suspend fun insertTransaction(transaction: Transaction): Long

    /**
     * Promotes a pending row: inserts it as an accepted Transaction, then marks
     * the pending row ACCEPTED. Caller-provided edits (amount/merchant/category/
     * type/notes) are applied to the persisted transaction.
     *
     * Guarded: the row's status is re-checked INSIDE the DB transaction, so a
     * double-tap or a race between review paths can never insert twice.
     * Returns [ALREADY_HANDLED] if the row was no longer PENDING.
     */
    suspend fun acceptPending(
        pending: PendingTransaction,
        amount: Double,
        merchant: String,
        category: com.sethv.fintrack.core.model.ExpenseCategory,
        type: com.sethv.fintrack.core.model.TransactionType,
        notes: String,
    ): Long

    /**
     * Bulk-accept: atomically inserts all pending rows as transactions AND
     * marks every source pending row ACCEPTED in a single DB transaction.
     * No partial state on crash mid-operation. Rows that were already handled
     * by another path are skipped. Returns the inserted transaction ids.
     */
    suspend fun acceptAllPending(pending: List<PendingTransaction>): List<Long>

    fun getAllTransactions(): Flow<List<Transaction>>

    suspend fun getTransactionById(id: Long): Transaction?

    suspend fun deleteTransaction(id: Long)

    fun getTransactionsByDateRange(startTime: Long, endTime: Long): Flow<List<Transaction>>

    /**
     * Duplicate guard shared by live + historical SMS paths. Timestamps are
     * compared as minute buckets because SMSC and handset receive clocks skew.
     */
    suspend fun existsBySmsFingerprint(smsBody: String, minuteBucket: Long): Boolean
}