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

    override suspend fun upsertBill(
        cardId: Long,
        totalDue: Double,
        minDue: Double,
        dueDate: Long,
        statementLabel: String,
    ): Long {
        // A statement is identified by (card, billing cycle). Consecutive
        // cycles are ~28-31 days apart, so a TIGHT window only ever matches a
        // re-delivery / reminder for the SAME statement — never next month's,
        // which would otherwise silently overwrite an unpaid bill.
        val existing = cardBillDao.findUnpaidForCardNearDue(
            cardId = cardId,
            fromInclusive = dueDate - SAME_STATEMENT_WINDOW,
            toInclusive = dueDate + SAME_STATEMENT_WINDOW,
        )
        if (existing != null) {
            cardBillDao.update(
                existing.copy(
                    totalDue = totalDue,
                    minDue = minDue,
                    dueDate = dueDate,
                    statementLabel = statementLabel.ifBlank { existing.statementLabel },
                ),
            )
            return existing.id
        }
        return cardBillDao.insert(
            CardBillEntity(
                id = 0,
                cardId = cardId,
                totalDue = totalDue,
                minDue = minDue,
                dueDate = dueDate,
                statementLabel = statementLabel,
                generatedAt = System.currentTimeMillis(),
                isPaid = false,
            ),
        )
    }

    override suspend fun markBillPaid(billId: Long, paidAmount: Double, paidAt: Long) {
        cardBillDao.markPaid(billId, paidAt, paidAmount)
    }

    override suspend fun unmarkBillPaid(billId: Long) {
        cardBillDao.unmarkPaid(billId)
    }

    override suspend fun settleBillWithPayment(cardId: Long, paidAmount: Double): Boolean {
        // A full payment covers the bill total; allow 1% (+₹1) headroom for
        // interest/fees posted between statement and payment.
        val tolerance = paidAmount * 1.01 + 1.0
        val now = System.currentTimeMillis()
        val bill = cardBillDao.findUnpaidForCardNearDue(
            cardId = cardId,
            fromInclusive = now - WINDOW_120_DAYS,
            toInclusive = now + WINDOW_120_DAYS,
        ) ?: return false
        if (bill.totalDue > tolerance) return false
        markBillPaid(bill.id, paidAmount)
        return true
    }

    override fun getNextUnpaidBill(): Flow<CardBill?> =
        cardBillDao.getUnpaid().map { unpaid -> unpaid.minByOrNull { it.dueDate }?.toDomain() }

    private companion object {
        const val DAY_MILLIS = 24L * 60 * 60 * 1000
        const val SAME_STATEMENT_WINDOW = 6L * DAY_MILLIS
        const val WINDOW_120_DAYS = 120L * DAY_MILLIS
    }
}
