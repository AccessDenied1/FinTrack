package com.sethv.fintrack.core.data.repository

import com.sethv.fintrack.core.model.CardBill
import com.sethv.fintrack.core.model.CreditCard
import kotlinx.coroutines.flow.Flow

/** card-bills table moved to a separate module (core/database-entities) */
sealed class CardsFeatureDisabled : RuntimeException("Cards feature not configured")

data class CardWithBills(
    val card: CreditCard,
    val bills: List<CardBill>,
)

interface CreditCardRepository {

    /** All registered cards (auto-registered from bill/payment SMS). */
    fun getAllCards(): Flow<List<CreditCard>>

    fun getBillsForCard(cardId: Long): Flow<List<CardBill>>

    fun getAllBills(): Flow<List<CardBill>>

    /**
     * Finds the card by bank+last4, creating it on first sight so users never
     * have to register manually. Returns its id.
     */
    suspend fun findOrCreateCard(bankName: String, lastFour: String): Long

    suspend fun renameCard(cardId: Long, label: String)

    /**
     * Upserts a parsed bill: matches an UNPAID bill of this card whose due date
     * falls within a tight window (±6 days) of [dueDate] and updates it;
     * otherwise inserts a fresh row. The window is deliberately narrow so a
     * re-delivered statement/reminder updates the same bill, while next month's
     * statement (~28-31 days later) never overwrites the current one. Returns
     * the bill id.
     */
    suspend fun upsertBill(
        cardId: Long,
        totalDue: Double,
        minDue: Double,
        dueDate: Long,
        statementLabel: String,
    ): Long

    suspend fun markBillPaid(billId: Long, paidAmount: Double, paidAt: Long = System.currentTimeMillis())

    suspend fun unmarkBillPaid(billId: Long)

    /**
     * Auto-payment matching: if an unpaid bill for [cardId] has totalDue <=
     * [paidAmount] (+1% tolerance for interest/fees), it is marked PAID.
     * Returns true when a bill was settled.
     */
    suspend fun settleBillWithPayment(cardId: Long, paidAmount: Double): Boolean

    /** Nearest unpaid due date across all cards, or null when all clear. */
    fun getNextUnpaidBill(): Flow<CardBill?>
}
