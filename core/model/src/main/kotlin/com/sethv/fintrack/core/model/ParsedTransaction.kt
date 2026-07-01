package com.sethv.fintrack.core.model

data class ParsedTransaction(
    val amount: Double,
    val merchant: String,
    val type: TransactionType,
    val dateTime: Long,
    val bank: String,
    val smsBody: String,
)
