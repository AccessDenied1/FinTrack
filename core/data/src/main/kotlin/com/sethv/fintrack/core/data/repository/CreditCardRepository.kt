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

    /** Removes the card and every bill attached to it. */
    suspend fun deleteCard(cardId: Long)

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
        creditLimit: Double? = null,
        statementStart: Long = 0L,
    ): Long

    /**
     * Finds a registered card by its bank name, matching case-insensitively and
     * ignoring surrounding whitespace. Returns the first match's id, or null
     * when no card's bank name matches [bankHint].
     */
    suspend fun findCardByBank(bankHint: String): Long?

    /** Sets (or clears with null) the manual credit-limit override on a card. */
    suspend fun updateLimit(cardId: Long, limit: Double?)

    suspend fun markBillPaid(billId: Long, paidAmount: Double, paidAt: Long = System.currentTimeMillis())

    suspend fun unmarkBillPaid(billId: Long)

    /**
     * Auto-payment matching. A full payment (>= the earliest unpaid bill's
     * totalDue, +1% tolerance) settles that bill — it is marked PAID and
     * returned. A partial payment is credited against the earliest bill's
     * outstanding (totalDue reduced) and returns null. Returns null when the
     * payment could not be applied at all (no unpaid bill in range) so callers
     * can decide whether to credit it as a prepay.
     */
    suspend fun settleBillWithPayment(cardId: Long, paidAmount: Double): CardBill?

    /**
     * Credits [amount] against this card's most recent bill (unpaid first, else
     * the latest paid one) — used as a prepay fallback when [settleBillWithPayment]
     * found nothing to settle. Never touches another card.
     */
    suspend fun creditPaymentToMostRecentBill(cardId: Long, amount: Double, paidAt: Long = System.currentTimeMillis())

    /** Nearest unpaid due date across all cards, or null when all clear. */
    fun getNextUnpaidBill(): Flow<CardBill?>
}
