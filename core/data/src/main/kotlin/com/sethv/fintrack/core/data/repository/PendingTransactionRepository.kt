package com.sethv.fintrack.core.data.repository

import com.sethv.fintrack.core.model.PendingTransaction
import kotlinx.coroutines.flow.Flow

interface PendingTransactionRepository {

    suspend fun insertPending(pending: PendingTransaction): Long

    fun getAllPending(): Flow<List<PendingTransaction>>

    fun getPending(): Flow<List<PendingTransaction>>

    fun getPendingCount(): Flow<Int>

    suspend fun getPendingById(id: Long): PendingTransaction?

    /** Marks the pending row ACCEPTED. Row stays in pending table for history. */
    suspend fun acceptPending(id: Long)

    /** Marks the pending row REJECTED. Row stays in pending table for history. */
    suspend fun rejectPending(id: Long)

    /** Bulk accept — used by Review tab "Accept All". */
    suspend fun acceptAllPending(ids: List<Long>)

    /** Bulk reject — used by Review tab "Skip All". */
    suspend fun rejectAllPending(ids: List<Long>)

    suspend fun deletePending(id: Long)

    /** Clears the entire queue (privacy/data-deletion). */
    suspend fun deleteAll()

    /**
     * Duplicate guard for the ingest paths. Timestamps are compared as minute
     * buckets because SMSC and handset receive clocks skew.
     */
    suspend fun existsBySmsFingerprint(smsBody: String, minuteBucket: Long): Boolean
}