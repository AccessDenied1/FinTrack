package com.sethv.fintrack.service.notification

import com.sethv.fintrack.core.model.PendingTransaction

interface TransactionNotifier {
    fun showTransactionNotification(pendingTransaction: PendingTransaction)

    /** High-urgency alert for a freshly detected credit-card bill. */
    fun showCardBillAlert(
        bankName: String,
        lastFour: String,
        totalDue: Double,
        minDue: Double?,
        dueDate: Long,
        billId: Long,
    )

    /** Confirmation when a payment SMS settles a tracked bill. */
    fun showBillPaidConfirmation(bankName: String, lastFour: String, paidAmount: Double)
}
