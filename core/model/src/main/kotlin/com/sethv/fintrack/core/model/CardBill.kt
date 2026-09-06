package com.sethv.fintrack.core.model

data class CardBill(
    val id: Long = 0,
    val cardId: Long,
    val totalDue: Double,
    val minDue: Double = 0.0,
    val dueDate: Long,
    val statementLabel: String = "",
    val generatedAt: Long = System.currentTimeMillis(),
    val isPaid: Boolean = false,
    val paidAt: Long? = null,
    val paidAmount: Double? = null,
    val creditLimit: Double? = null,
    val statementStart: Long = 0L,
)
