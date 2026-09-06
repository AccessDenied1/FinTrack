package com.sethv.fintrack.core.data.mapper

import com.sethv.fintrack.core.database.entity.BankCardEntity
import com.sethv.fintrack.core.database.entity.CardBillEntity
import com.sethv.fintrack.core.database.entity.PendingTransactionEntity
import com.sethv.fintrack.core.database.entity.TransactionEntity
import com.sethv.fintrack.core.model.CardBill
import com.sethv.fintrack.core.model.CreditCard
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
    cardId = cardId,
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
    cardId = cardId,
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

fun BankCardEntity.toDomain(): CreditCard = CreditCard(
    id = id,
    bankName = bankName,
    lastFour = lastFour,
    label = label,
    createdAt = createdAt,
    creditLimitOverride = creditLimitOverride,
)

fun CreditCard.toEntity(): BankCardEntity = BankCardEntity(
    id = id,
    bankName = bankName,
    lastFour = lastFour,
    label = label,
    createdAt = createdAt,
    creditLimitOverride = creditLimitOverride,
)

fun CardBillEntity.toDomain(): CardBill = CardBill(
    id = id,
    cardId = cardId,
    totalDue = totalDue,
    minDue = minDue,
    dueDate = dueDate,
    statementLabel = statementLabel,
    generatedAt = generatedAt,
    isPaid = isPaid,
    paidAt = if (paidAt == 0L) null else paidAt,
    paidAmount = if (paidAmount == 0.0) null else paidAmount,
    creditLimit = creditLimit,
    statementStart = statementStart,
)

fun CardBill.toEntity(): CardBillEntity = CardBillEntity(
    id = id,
    cardId = cardId,
    totalDue = totalDue,
    minDue = minDue,
    dueDate = dueDate,
    statementLabel = statementLabel,
    generatedAt = generatedAt,
    isPaid = isPaid,
    paidAt = paidAt ?: 0,
    paidAmount = paidAmount ?: 0.0,
    creditLimit = creditLimit,
    statementStart = statementStart,
)
