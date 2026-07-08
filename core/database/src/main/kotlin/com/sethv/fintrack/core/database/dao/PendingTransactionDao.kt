package com.sethv.fintrack.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sethv.fintrack.core.database.entity.PendingTransactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingTransactionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: PendingTransactionEntity): Long

    @Query("SELECT * FROM pending_transactions ORDER BY dateTime DESC")
    fun getAll(): Flow<List<PendingTransactionEntity>>

    @Query("SELECT * FROM pending_transactions WHERE status = :status ORDER BY dateTime DESC")
    fun getByStatus(status: String): Flow<List<PendingTransactionEntity>>

    @Query("SELECT COUNT(*) FROM pending_transactions WHERE status = :status")
    fun countByStatus(status: String): Flow<Int>

    @Query("SELECT * FROM pending_transactions WHERE id = :id")
    suspend fun getById(id: Long): PendingTransactionEntity?

    @Query("UPDATE pending_transactions SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String)

    @Query("UPDATE pending_transactions SET status = :status WHERE id IN (:ids)")
    suspend fun updateStatusBulk(ids: List<Long>, status: String)

    @Query("DELETE FROM pending_transactions WHERE id = :id")
    suspend fun deletePending(id: Long)

    @Query("DELETE FROM pending_transactions WHERE id IN (:ids)")
    suspend fun deletePendingBulk(ids: List<Long>)
}