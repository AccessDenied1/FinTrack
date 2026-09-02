package com.sethv.fintrack.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.sethv.fintrack.core.database.entity.CardBillEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CardBillDao {

    @Insert
    suspend fun insert(entity: CardBillEntity): Long

    @Update
    suspend fun update(entity: CardBillEntity)

    @Query("SELECT * FROM card_bills ORDER BY dueDate DESC")
    fun getAll(): Flow<List<CardBillEntity>>

    @Query("SELECT * FROM card_bills WHERE id = :id")
    suspend fun getById(id: Long): CardBillEntity?

    @Query("SELECT * FROM card_bills WHERE isPaid = 0 ORDER BY dueDate ASC")
    fun getUnpaid(): Flow<List<CardBillEntity>>

    @Query(
        "SELECT * FROM card_bills WHERE cardId = :cardId AND isPaid = 0 " +
            "AND dueDate BETWEEN :fromInclusive AND :toInclusive LIMIT 1",
    )
    suspend fun findUnpaidForCardNearDue(
        cardId: Long,
        fromInclusive: Long,
        toInclusive: Long,
    ): CardBillEntity?

    @Query("SELECT * FROM card_bills WHERE isPaid = 0 AND dueDate >= :now ORDER BY dueDate ASC LIMIT 1")
    suspend fun findNextUnpaid(now: Long): CardBillEntity?

    @Query("UPDATE card_bills SET isPaid = 1, paidAt = :paidAt, paidAmount = :paidAmount WHERE id = :id")
    suspend fun markPaid(id: Long, paidAt: Long, paidAmount: Double)

    @Query("UPDATE card_bills SET isPaid = 0, paidAt = 0, paidAmount = 0.0 WHERE id = :id")
    suspend fun unmarkPaid(id: Long)
}
