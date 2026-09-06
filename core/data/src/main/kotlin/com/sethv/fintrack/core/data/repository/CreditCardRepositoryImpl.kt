package com.sethv.fintrack.core.data.repository

import com.sethv.fintrack.core.data.mapper.toDomain
import com.sethv.fintrack.core.database.dao.BankCardDao
import com.sethv.fintrack.core.database.dao.CardBillDao
import com.sethv.fintrack.core.database.entity.BankCardEntity
import com.sethv.fintrack.core.database.entity.CardBillEntity
import com.sethv.fintrack.core.model.CardBill
import com.sethv.fintrack.core.model.CreditCard
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class CreditCardRepositoryImpl @Inject constructor(
    private val bankCardDao: BankCardDao,
    private val cardBillDao: CardBillDao,
) : CreditCardRepository {

    override fun getAllCards(): Flow<List<CreditCard>> =
        bankCardDao.getAll().map { list -> list.map { it.toDomain() } }

    override fun getBillsForCard(cardId: Long): Flow<List<CardBill>> =
        cardBillDao.getAll().map { bills ->
            bills.filter { it.cardId == cardId }.map { it.toDomain() }
        }

    override fun getAllBills(): Flow<List<CardBill>> =
        cardBillDao.getAll().map { list -> list.map { it.toDomain() } }

    override suspend fun findOrCreateCard(bankName: String, lastFour: String): Long {
        val normalizedBank = bankName.trim()
        val normalizedLast4 = lastFour.trim()
        bankCardDao.findByBankAndLastFour(normalizedBank, normalizedLast4)?.let { return it.id }
        val newId = bankCardDao.insert(
            BankCardEntity(
                bankName = normalizedBank,
                lastFour = normalizedLast4,
                label = "",
                createdAt = System.currentTimeMillis(),
            ),
        )
        // IGNORE conflict strategy returns -1 on a concurrent race — re-read.
        if (newId != -1L) return newId
        return requireNotNull(
            bankCardDao.findByBankAndLastFour(normalizedBank, normalizedLast4),
        ).id
    }

    override suspend fun renameCard(cardId: Long, label: String) {
        bankCardDao.rename(cardId, label.trim())
    }

    override suspend fun deleteCard(cardId: Long) {
        cardBillDao.deleteByCard(cardId)
        bankCardDao.deleteById(cardId)
    }

    override suspend fun upsertBill(
        cardId: Long,
        totalDue: Double,
        minDue: Double,
        dueDate: Long,
        statementLabel: String,
        creditLimit: Double?,
        statementStart: Long,
    ): Long {
        // A statement is identified by (card, billing cycle). Consecutive
        // cycles are ~28-31 days apart, so a TIGHT window only ever matches a
        // re-delivery / reminder for the SAME statement — never next month's,
        // which would otherwise silently overwrite an unpaid bill.
        val existing = cardBillDao.findUnpaidForCardNearestDue(
            cardId = cardId,
            targetDueDate = dueDate,
            toInclusive = dueDate + SAME_STATEMENT_WINDOW,
        )
        val billId = if (existing != null && kotlin.math.abs(existing.dueDate - dueDate) <= SAME_STATEMENT_WINDOW) {
            cardBillDao.update(
                existing.copy(
                    totalDue = totalDue,
                    minDue = minDue,
                    dueDate = dueDate,
                    statementLabel = statementLabel.ifBlank { existing.statementLabel },
                    creditLimit = creditLimit ?: existing.creditLimit,
                    statementStart = statementStart.takeIf { it != 0L } ?: existing.statementStart,
                ),
            )
            existing.id
        } else {
            cardBillDao.insert(
                CardBillEntity(
                    id = 0,
                    cardId = cardId,
                    totalDue = totalDue,
                    minDue = minDue,
                    dueDate = dueDate,
                    statementLabel = statementLabel,
                    generatedAt = System.currentTimeMillis(),
                    isPaid = false,
                    creditLimit = creditLimit,
                    statementStart = statementStart,
                ),
            )
        }
        // The parsed available-limit is a snapshot of the CARD's limit — keep
        // the manual override in sync whenever a fresh one arrives.
        if (creditLimit != null) {
            bankCardDao.updateLimit(cardId, creditLimit)
        }
        return billId
    }

    override suspend fun findCardByBank(bankHint: String): Long? {
        val target = bankHint.trim().uppercase()
        if (target.isEmpty()) return null
        return bankCardDao.getAll().first()
            .firstOrNull { it.bankName.trim().uppercase() == target }
            ?.id
    }

    override suspend fun findCardIdForTimestamp(bankHint: String, timestamp: Long): Long? {
        val target = bankHint.trim().uppercase()
        if (target.isEmpty()) return null
        val candidates = bankCardDao.getAll().first()
            .filter { it.bankName.trim().uppercase() == target }
        if (candidates.isEmpty()) return null
        val qualifying = candidates.filter { card ->
            cardBillDao.findUnpaidForCard(card.id).any { bill ->
                bill.statementStart > 0L &&
                    timestamp in (bill.statementStart - CARD_LINK_WINDOW)..(bill.dueDate + CARD_LINK_WINDOW)
            }
        }
        return qualifying.singleOrNull()?.id
    }

    override suspend fun updateLimit(cardId: Long, limit: Double?) {
        bankCardDao.updateLimit(cardId, limit)
    }

    override suspend fun markBillPaid(billId: Long, paidAmount: Double, paidAt: Long) {
        cardBillDao.markPaid(billId, paidAt, paidAmount)
    }

    override suspend fun unmarkBillPaid(billId: Long) {
        cardBillDao.unmarkPaid(billId)
    }

    /**
     * Auto-payment matching: settles the EARLIEST unpaid bill [paidAmount]
     * fully covers (totalDue <= paidAmount + 1% tolerance for interest/fees
     * posted between statement and payment). Earliest-first is deterministic
     * and safe — settling a nearer bill before a later one never blocks the
     * later bill from being settled by its own payment.
     * Returns the settled bill, or null when nothing is fully covered (e.g. a
     * minimum-due payment) so the caller can credit it via
     * [creditPaymentToMostRecentBill] instead of dropping it.
     */
    override suspend fun settleBillWithPayment(cardId: Long, paidAmount: Double): CardBill? {
        if (paidAmount <= 0.0) return null
        val earliest = cardBillDao.findEarliestUnpaidForCard(cardId) ?: return null
        val tolerance = paidAmount * 1.01 + 1.0
        if (earliest.totalDue > tolerance) return null
        cardBillDao.markPaid(earliest.id, System.currentTimeMillis(), paidAmount)
        return earliest.toDomain()
    }

    /**
     * Credits [amount] against this card's most recent bill (an unpaid one if
     * any, else the latest paid statement). Reduces totalDue/minDue, and marks
     * the bill PAID when the credit clears it. Never touches another card.
     * Used for partial payments (min due) and prepays.
     */
    override suspend fun creditPaymentToMostRecentBill(cardId: Long, amount: Double, paidAt: Long) {
        if (amount <= 0.0) return
        val bill = cardBillDao.findMostRecentBillForCard(cardId) ?: return
        val newTotal = (bill.totalDue - amount).coerceAtLeast(0.0)
        val nowCleared = newTotal <= 0.0 && !bill.isPaid
        cardBillDao.update(
            bill.copy(
                totalDue = newTotal,
                minDue = (bill.minDue - amount).coerceAtLeast(0.0),
                isPaid = bill.isPaid || nowCleared,
                paidAt = if (nowCleared) paidAt else bill.paidAt,
                paidAmount = bill.paidAmount + amount,
            ),
        )
    }

    override fun getNextUnpaidBill(): Flow<CardBill?> =
        cardBillDao.getUnpaid().map { unpaid -> unpaid.minByOrNull { it.dueDate }?.toDomain() }

    private companion object {
        const val DAY_MILLIS = 24L * 60 * 60 * 1000
        const val SAME_STATEMENT_WINDOW = 6L * DAY_MILLIS
        // A debit posted for a card can legitimately land slightly before the
        // statement opens or right after it is due — one day of slack on each end.
        const val CARD_LINK_WINDOW = DAY_MILLIS
    }
}
