package com.sethv.fintrack.core.data.repository

import com.sethv.fintrack.core.model.Transaction
import kotlinx.coroutines.flow.Flow

interface TransactionRepository {

    suspend fun insertTransaction(transaction: Transaction): Long

    fun getAllTransactions(): Flow<List<Transaction>>

    suspend fun getTransactionById(id: Long): Transaction?

    suspend fun deleteTransaction(id: Long)

    fun getTransactionsByDateRange(startTime: Long, endTime: Long): Flow<List<Transaction>>
}
