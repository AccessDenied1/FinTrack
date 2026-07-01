package com.sethv.fintrack.core.data.mapper

import com.sethv.fintrack.core.database.entity.PendingTransactionEntity
import com.sethv.fintrack.core.database.entity.TransactionEntity
import com.sethv.fintrack.core.model.ExpenseCategory
import com.sethv.fintrack.core.model.PendingStatus
import com.sethv.fintrack.core.model.PendingTransaction
import com.sethv.fintrack.core.model.Transaction
import com.sethv.fintrack.core.model.TransactionType

fun TransactionEntity.toDomain(): Transaction = Transaction(
    id = id,
    amount = amount,
    merchant = merchant,
    category = ExpenseCategory.valueOf(category),
    type = TransactionType.valueOf(type),
    dateTime = dateTime,
    bank = bank,
    notes = notes,
    smsBody = smsBody,
    createdAt = createdAt,
)

fun Transaction.toEntity(): TransactionEntity = TransactionEntity(
    id = id,
    amount = amount,
    merchant = merchant,
    category = category.name,
    type = type.name,
    dateTime = dateTime,
    bank = bank,
    notes = notes,
    smsBody = smsBody,
    createdAt = createdAt,
)

fun PendingTransactionEntity.toDomain(): PendingTransaction = PendingTransaction(
    id = id,
    amount = amount,
    merchant = merchant,
    category = ExpenseCategory.valueOf(category),
    type = TransactionType.valueOf(type),
    dateTime = dateTime,
    bank = bank,
    notes = notes,
    smsBody = smsBody,
    createdAt = createdAt,
    status = PendingStatus.valueOf(status),
)

fun PendingTransaction.toEntity(): PendingTransactionEntity = PendingTransactionEntity(
    id = id,
    amount = amount,
    merchant = merchant,
    category = category.name,
    type = type.name,
    dateTime = dateTime,
    bank = bank,
    notes = notes,
    smsBody = smsBody,
    createdAt = createdAt,
    status = status.name,
)
