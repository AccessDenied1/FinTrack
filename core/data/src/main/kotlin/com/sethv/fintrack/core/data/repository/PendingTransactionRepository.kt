package com.sethv.fintrack.core.data.repository

import com.sethv.fintrack.core.model.PendingTransaction
import kotlinx.coroutines.flow.Flow

interface PendingTransactionRepository {

    suspend fun insertPending(pending: PendingTransaction): Long

    fun getAllPending(): Flow<List<PendingTransaction>>

    suspend fun getPendingById(id: Long): PendingTransaction?

    suspend fun acceptPending(id: Long)

    suspend fun rejectPending(id: Long)

    suspend fun deletePending(id: Long)
}
