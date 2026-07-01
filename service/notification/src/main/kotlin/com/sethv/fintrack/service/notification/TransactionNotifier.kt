package com.sethv.fintrack.service.notification

import com.sethv.fintrack.core.model.PendingTransaction

interface TransactionNotifier {
    fun showTransactionNotification(pendingTransaction: PendingTransaction)
}
