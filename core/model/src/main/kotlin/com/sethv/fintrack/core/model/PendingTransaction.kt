package com.sethv.fintrack.core.model

enum class PendingStatus {
    PENDING,
    ACCEPTED,
    REJECTED,
}

data class PendingTransaction(
    val id: Long = 0,
    val amount: Double,
    val merchant: String,
    val category: ExpenseCategory,
    val type: TransactionType,
    val dateTime: Long,
    val bank: String = "",
    val notes: String = "",
    val smsBody: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val status: PendingStatus,
)
