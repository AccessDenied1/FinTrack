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

    @Query("SELECT * FROM card_bills ORDER BY dueDate DESC, generatedAt DESC")
    fun getAll(): Flow<List<CardBillEntity>>

    @Query("SELECT * FROM card_bills WHERE id = :id")
    suspend fun getById(id: Long): CardBillEntity?

    @Query("SELECT * FROM card_bills WHERE isPaid = 0 ORDER BY dueDate ASC, generatedAt DESC")
    fun getUnpaid(): Flow<List<CardBillEntity>>

    /**
     * Unpaid bill whose due date is closest to [targetDueDate] (never a bill
     * due strictly after [toInclusive]). Ties break toward the most recent
     * statement, so a same-day re-delivery always resolves to the bill it
     * belongs to. Deterministic — never an arbitrary row.
     */
    @Query(
        "SELECT * FROM card_bills " +
            "WHERE cardId = :cardId AND isPaid = 0 AND dueDate <= :toInclusive " +
            "ORDER BY ABS(dueDate - :targetDueDate) ASC, generatedAt DESC LIMIT 1",
    )
    suspend fun findUnpaidForCardNearestDue(
        cardId: Long,
        targetDueDate: Long,
        toInclusive: Long,
    ): CardBillEntity?

    @Query("SELECT * FROM card_bills WHERE cardId = :cardId AND isPaid = 0")
    suspend fun findUnpaidForCard(cardId: Long): List<CardBillEntity>

    @Query(
        "SELECT * FROM card_bills WHERE cardId = :cardId AND isPaid = 0 " +
            "ORDER BY dueDate ASC, generatedAt DESC LIMIT 1",
    )
    suspend fun findEarliestUnpaidForCard(cardId: Long): CardBillEntity?

    @Query(
        "SELECT * FROM card_bills WHERE cardId = :cardId " +
            "ORDER BY isPaid ASC, dueDate DESC, generatedAt DESC LIMIT 1",
    )
    suspend fun findMostRecentBillForCard(cardId: Long): CardBillEntity?

    @Query("SELECT * FROM card_bills WHERE isPaid = 0 AND dueDate >= :now ORDER BY dueDate ASC, generatedAt DESC LIMIT 1")
    suspend fun findNextUnpaid(now: Long): CardBillEntity?

    @Query("UPDATE card_bills SET isPaid = 1, paidAt = :paidAt, paidAmount = :paidAmount WHERE id = :id")
    suspend fun markPaid(id: Long, paidAt: Long, paidAmount: Double)

    @Query("UPDATE card_bills SET isPaid = 0, paidAt = 0, paidAmount = 0.0 WHERE id = :id")
    suspend fun unmarkPaid(id: Long)

    @Query("DELETE FROM card_bills WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM card_bills WHERE cardId = :cardId")
    suspend fun deleteByCard(cardId: Long)

    @Query("DELETE FROM card_bills")
    suspend fun deleteAll()
}
